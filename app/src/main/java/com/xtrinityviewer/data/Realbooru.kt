package com.xtrinityviewer.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object RealbooruModule {
    private const val BASE_URL = "https://realbooru.com/index.php?page=post&s=list"
    private const val DETAIL_URL = "https://realbooru.com/index.php?page=post&s=view&id="

    // User Agent estable (Chrome Windows) para evitar bloqueos y asegurar consistencia
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // 1. ESCÁNER PRINCIPAL (MODO EXACTO DART)
    suspend fun getPosts(page: Int, tags: String = ""): List<UnifiedPost> = withContext(Dispatchers.IO) {
            val pid = page * 40
            val cleanTags = tags.trim().replace(" ", "+")
            val url = "$BASE_URL&tags=$cleanTags&pid=$pid"

            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(20000).get()
            val thumbElements = doc.select(".thumb > a")

            // LOGICA DART: Iteramos todos los elementos y buscamos sus detalles
            // Usamos async para que sea rápido en Android (Dart lo hacía secuencial, aquí paralelizamos)
            val deferredPosts = thumbElements.map { link ->
                async {
                    try {
                        // 1. Obtener ID y Thumb del Feed
                        val href = link.attr("abs:href")
                        val idMatch = Regex("id=(\\d+)").find(href)
                        val id = idMatch?.groupValues?.get(1)

                        val img = link.selectFirst("img")
                        val thumbUrl = img?.attr("abs:src") ?: ""
                        // Título sucio por si falla el scraping profundo
                        val fallbackTitle = img?.attr("title") ?: ""

                        if (id != null) {
                            // 2. IR AL DETALLE (SIEMPRE, COMO EN DART)
                            // Esto asegura que detectemos videos, gifs y tags reales siempre.
                            fetchPostDetails(id, thumbUrl, fallbackTitle)
                        } else {
                            null
                        }
                    } catch (e: Exception) { null }
                }
            }
            return@withContext deferredPosts.awaitAll().filterNotNull()
    }

    // 2. DETECTOR DE TIPOS Y TAGS (LÓGICA DART PORTADA)
    private fun fetchPostDetails(id: String, thumbUrl: String, fallbackTitle: String): UnifiedPost? {
        try {
            val url = "$DETAIL_URL$id"
            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get()

            // Selector clave de Dart: .imageContainer
            val container = doc.selectFirst(".imageContainer") ?: return null

            var fullUrl = ""
            var type = MediaType.IMAGE

            // Lógica Dart: Prioridad a <video source>
            val videoSource = container.selectFirst("video source")
            if (videoSource != null) {
                fullUrl = videoSource.attr("abs:src")
                type = MediaType.VIDEO
            } else {
                // Lógica Dart: Si no, buscar #image
                val img = container.selectFirst("#image")
                if (img != null) {
                    fullUrl = img.attr("abs:src")
                    if (fullUrl.endsWith(".gif", true)) type = MediaType.GIF
                }
            }

            if (fullUrl.isEmpty()) return null

            // Extracción de Tags (Estilo Dart: buscar clases 'tag*')
            val tagsList = doc.select("a").filter { element ->
                element.classNames().any { it.startsWith("tag") }
            }.map { it.text().trim() }
                .filter { it.isNotEmpty() && !it.contains(Regex("[+?-]")) } // Limpieza básica
                .toSet().toList()

            // Si no encontramos tags limpios, usamos el título sucio del feed
            val finalTags = if (tagsList.isNotEmpty()) tagsList else fallbackTitle.split(" ").filter { it.length > 1 }

            return UnifiedPost(
                id = id,
                url = fullUrl, // URL Real (Video o HD)
                previewUrl = thumbUrl, // Mantenemos la miniatura ligera para el grid inicial
                type = type, // Tipo correcto (Video/Gif/Img)
                source = SourceType.REALBOORU,
                title = finalTags.take(3).joinToString(" "),
                tags = finalTags
            )
        } catch (e: Exception) { return null }
    }

    // Para el Lector HD (Misma lógica, re-confirma la URL)
    suspend fun getOriginalUrl(id: String, thumbUrl: String? = null): String = withContext(Dispatchers.IO) {
        val post = fetchPostDetails(id, thumbUrl ?: "", "")
        return@withContext post?.url ?: thumbUrl ?: ""
    }
}