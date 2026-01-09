package com.xtrinityviewer.data

import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

object VerComicsModule {
    private const val BASE_URL = "https://vercomicsporno.com"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    private val masterCookieList = mutableListOf<Cookie>()

    fun getCookiesAsString(): String {
        return synchronized(masterCookieList) {
            masterCookieList.joinToString("; ") { "${it.name}=${it.value}" }
        }
    }

    fun saveCookiesFromWebView(url: String, cookieString: String?) {
        if (cookieString == null) return
        val httpUrl = url.toHttpUrlOrNull() ?: return

        val cookies = cookieString.split(";").mapNotNull { part ->
            try {
                val parts = part.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    Cookie.Builder()
                        .name(parts[0])
                        .value(parts[1])
                        .domain(httpUrl.host)
                        .path("/")
                        .build()
                } else null
            } catch (e: Exception) { null }
        }

        if (cookies.isNotEmpty()) {
            synchronized(masterCookieList) {
                cookies.forEach { newCookie ->
                    masterCookieList.removeIf { it.name == newCookie.name }
                    masterCookieList.add(newCookie)
                }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                if (url.host.contains("vercomics")) {
                    return synchronized(masterCookieList) { masterCookieList.toList() }
                }
                return emptyList()
            }
        })
        .build()

    suspend fun getTags(): List<AutocompleteDto> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchHtmlConRetry(BASE_URL) ?: return@withContext emptyList()
            val tags = mutableListOf<AutocompleteDto>()
            val elements = doc.select("div.tagcloud a")

            for (el in elements) {
                val name = el.text()
                val href = el.attr("href")
                val value = href.substringAfter("/etiquetas/").trim('/')
                val aria = el.attr("aria-label")
                val count = if (aria.contains("(")) aria.substringAfter("(").substringBefore(" ") else ""
                val labelDisplay = if (count.isNotEmpty()) "$name ($count)" else name

                if (value.isNotEmpty()) {
                    tags.add(AutocompleteDto(labelDisplay, value))
                }
            }
            return@withContext tags.sortedBy { it.label }
        } catch (e: Exception) { return@withContext emptyList() }
    }

    @SuppressLint("SuspiciousIndentation")
    suspend fun getComics(page: Int, query: String = ""): List<UnifiedPost> = withContext(Dispatchers.IO) {
        val paged = page + 1
        val url = if (query.isNotBlank()) {
            val cleanQuery = query.trim().replace(" ", "+")
            if (page == 0) {
                "$BASE_URL/comics-porno?s=$cleanQuery"
            } else {
                "$BASE_URL/comics-porno/page/$paged/?s=$cleanQuery"
            }
        } else {
            if (page == 0) BASE_URL else "$BASE_URL/page/$paged/"
        }
        val doc = fetchHtmlConRetry(url)
            ?: throw Exception("No se pudo conectar a VerComics")
            if (query.isNotBlank()) {
                val ogUrl = doc.select("meta[property=og:url]").attr("content")
                if (!ogUrl.contains("comics-porno")) {
                    return@withContext emptyList()
                }
                if (doc.body().hasClass("home")) {
                    return@withContext emptyList()
                }
            }
            val posts = mutableListOf<UnifiedPost>()
            val elements = doc.select("div.blog-list-items div.entry")

            for (el in elements) {
                val linkTag = el.selectFirst("a.popimg") ?: continue
                val href = linkTag.attr("href")
                val titleTag = el.selectFirst("h2.information a")
                val title = titleTag?.attr("title") ?: titleTag?.text() ?: "Sin título"
                val lowerTitle = title.lowercase()
                val lowerHref = href.lowercase()

                if (lowerTitle.contains("fanbox") || lowerTitle.contains("toonxvip")) {
                    continue
                }

                val imgTag = el.selectFirst("figure img")
                var thumb = ""

                if (imgTag != null) {
                    thumb = imgTag.attr("data-src")
                    if (thumb.isEmpty()) thumb = imgTag.attr("src")
                }

                if (thumb.startsWith("//")) thumb = "https:$thumb"
                if (thumb.startsWith("/")) thumb = "$BASE_URL$thumb"

                if (href.isNotEmpty() && thumb.isNotEmpty()) {
                    posts.add(
                        UnifiedPost(
                            id = href,
                            url = href,
                            previewUrl = thumb,
                            type = MediaType.GALLERY,
                            source = SourceType.VERCOMICS,
                            title = title,
                            tags = emptyList(),
                            aspectRatio = 0.7f
                        )
                    )
                }
            }
            return@withContext posts
    }

    suspend fun getChapterImages(url: String): List<GalleryPageDto> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchHtmlConRetry(url) ?: return@withContext emptyList()
            val pages = mutableListOf<GalleryPageDto>()
            val contentDiv = doc.selectFirst(".wp-content") ?: doc.body()
            val images = contentDiv.select("img")
            var index = 0
            for (img in images) {
                var src = img.attr("src")

                if (src.isEmpty()) src = img.attr("data-src")

                if (!src.startsWith("http")) continue

                val imgClass = img.className().lowercase()

                if (imgClass.contains("attachment-comics-thumb") || // Miniatura de post relacionado
                    imgClass.contains("wp-post-image") ||           // Portada pequeña
                    imgClass.contains("avatar")) {                  // Avatar de usuario
                    continue
                }

                if (src.endsWith(".mp4") || src.endsWith(".webm") || src.contains("downloadbutton", true) ||
                    src.contains("logo", true) || src.contains("vip", true)) continue

                var isJunk = false
                for (parent in img.parents()) {
                    val pClass = parent.className().lowercase()
                    val pId = parent.id().lowercase()

                    if (pClass.contains("related") ||  // related-posts-container
                        pClass.contains("slider") ||   // mia-slider-widget, swiper-slide
                        pClass.contains("widget") ||
                        pClass.contains("sidebar") ||
                        pClass.contains("yarpp") ||    // Yet Another Related Posts Plugin
                        pId.contains("footer")) {
                        isJunk = true
                        break
                    }
                }
                if (isJunk) continue

                val w = img.attr("width").toIntOrNull() ?: 999
                val h = img.attr("height").toIntOrNull() ?: 999
                if (w < 450) continue

                pages.add(GalleryPageDto(index++, src, src))
            }
            return@withContext pages
        } catch (e: Exception) { return@withContext emptyList() }
    }

    private suspend fun fetchHtmlConRetry(url: String): Document? {
        var attempts = 0
        while (attempts < 3) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "$BASE_URL/")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    return Jsoup.parse(body, url)
                }
                response.close()
            } catch (e: Exception) { }
            attempts++
            delay(1500)
        }
        return null
    }
}