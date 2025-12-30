package com.xtrinityviewer.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object EHentaiModule {
    private const val BASE_URL = "https://e-hentai.org/"
    private const val TAG = "EH_DEBUG"
    private val COOKIES = mapOf("nw" to "1")
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val HEADERS = mapOf("User-Agent" to USER_AGENT, "Referer" to "https://e-hentai.org/")

    // REGEXES
    private val URL_IN_STYLE_REGEX = Regex("""url\((.*?)\)""")
    private val WIDTH_REGEX = Regex("""width:\s*(\d+)px""")
    private val HEIGHT_REGEX = Regex("""height:\s*(\d+)px""")
    private val BG_POS_REGEX = Regex("""url\(.*?\).*?(-?\d+)(?:px)?\s+(-?\d+)(?:px)?""")

    // --- HELPER PARA ARREGLAR URLS ---
    private fun fixUrl(rawUrl: String): String {
        var url = rawUrl.replace("\"", "").replace("'", "").trim()
        if (url.startsWith("//")) {
            url = "https:$url"
        }
        return url
    }

    // --- FEED PRINCIPAL ---
    suspend fun getGalleries(page: Int, query: String = ""): List<UnifiedPost> = withContext(Dispatchers.IO) {

        val finalQuery = query.trim().replace(" ", "+")
        val url = if (finalQuery.isBlank()) "${BASE_URL}?page=$page" else "${BASE_URL}?page=$page&f_search=$finalQuery&f_apply=Apply+Filter"

        try {
            val doc = Jsoup.connect(url).cookies(COOKIES).headers(HEADERS).userAgent(USER_AGENT).timeout(30000).get()
            val posts = mutableListOf<UnifiedPost>()
            val table = doc.selectFirst("table.itg") ?: return@withContext emptyList()

            val rows = table.select("tr")

            for (row in rows) {
                if (row.selectFirst("th") != null) continue

                val glname = row.selectFirst(".glink") ?: row.selectFirst(".gl3c a")
                val linkElement = row.selectFirst(".gl3c a") ?: row.selectFirst(".gl1e a")

                if (glname == null || linkElement == null) continue

                val title = glname.text()
                val href = linkElement.attr("href")

                // Miniaturas
                var thumbUrl = ""
                val imgElement = row.selectFirst(".gl2c img") ?: row.selectFirst(".gl1e img")
                if (imgElement != null) {
                    thumbUrl = imgElement.attr("data-src").ifEmpty { imgElement.attr("src") }
                }

                // --- DETECCIÓN DE SPRITES (FEED) ---
                var sWidth: Int? = null
                var sHeight: Int? = null
                var sX: Int? = null
                var sY: Int? = null

                if (thumbUrl.isEmpty() || thumbUrl.contains("b.gif") || thumbUrl.contains("init.png")) {
                    val styleDiv = row.selectFirst(".gl2c div[style]") ?: row.selectFirst(".gl1e div[style]")
                    val style = styleDiv?.attr("style") ?: ""

                    val match = URL_IN_STYLE_REGEX.find(style)
                    if (match != null) {
                        // AQUÍ ESTABA EL ERROR: Usamos fixUrl para añadir https:
                        thumbUrl = fixUrl(match.groupValues[1])

                        try {
                            val wMatch = WIDTH_REGEX.find(style)
                            val hMatch = HEIGHT_REGEX.find(style)
                            val bgPosMatch = BG_POS_REGEX.find(style)

                            if (wMatch != null && hMatch != null && bgPosMatch != null) {
                                sWidth = wMatch.groupValues[1].toInt()
                                sHeight = hMatch.groupValues[1].toInt()
                                sX = -bgPosMatch.groupValues[1].toInt()
                                sY = -bgPosMatch.groupValues[2].toInt()
                            }
                        } catch (e: Exception) { }
                    }
                }

                val catText = row.selectFirst(".cn")?.text()?.lowercase() ?: "misc"
                val type = if (catText.contains("video") || catText.contains("motion")) MediaType.VIDEO else MediaType.GALLERY

                if (href.isNotEmpty()) {
                    posts.add(
                        UnifiedPost(
                            id = href,
                            url = href,
                            previewUrl = thumbUrl,
                            type = type,
                            source = SourceType.EHENTAI,
                            title = title,
                            tags = listOf(catText),
                            aspectRatio = 0.7f,
                            spriteWidth = sWidth,
                            spriteHeight = sHeight,
                            spriteX = sX,
                            spriteY = sY
                        )
                    )
                }
            }
            return@withContext posts
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    // --- LECTOR DE PÁGINAS ---
    suspend fun getGalleryPageChunk(galleryUrl: String, pageIndex: Int): Triple<String, List<GalleryPageDto>, Int> = withContext(Dispatchers.IO) {
        try {
            val pagedUrl = if (pageIndex == 0) galleryUrl else "$galleryUrl?p=$pageIndex"
            val doc = Jsoup.connect(pagedUrl).cookies(COOKIES).headers(HEADERS).userAgent(USER_AGENT).timeout(30000).get()

            var coverUrl = ""
            if (pageIndex == 0) {
                val gd1Div = doc.selectFirst("#gd1 div[style]")
                val style = gd1Div?.attr("style") ?: ""
                val match = URL_IN_STYLE_REGEX.find(style)
                if (match != null) {
                    coverUrl = fixUrl(match.groupValues[1])
                }
            }

            var totalWebPages = 1
            val lastPageLink = doc.select(".ptt td a").last()
            if (lastPageLink != null) {
                val href = lastPageLink.attr("href")
                val pMatch = Regex("[?&]p=(\\d+)").find(href)
                if (pMatch != null) {
                    totalWebPages = pMatch.groupValues[1].toInt() + 1
                }
            }

            val pages = mutableListOf<GalleryPageDto>()
            val links = doc.select("#gdt a")
            val baseIndex = pageIndex * links.size

            var i = 0
            for (link in links) {
                val href = link.attr("href")
                var thumbUrl = ""
                var width: Int? = null
                var height: Int? = null
                var offX: Int? = null
                var offY: Int? = null

                val div = link.selectFirst("div[style]")
                if (div != null) {
                    val style = div.attr("style")
                    val uMatch = URL_IN_STYLE_REGEX.find(style)
                    if (uMatch != null) thumbUrl = fixUrl(uMatch.groupValues[1])

                    try {
                        val wMatch = WIDTH_REGEX.find(style)
                        val hMatch = HEIGHT_REGEX.find(style)
                        val bgPosMatch = BG_POS_REGEX.find(style)

                        if (wMatch != null && hMatch != null && bgPosMatch != null) {
                            width = wMatch.groupValues[1].toInt()
                            height = hMatch.groupValues[1].toInt()
                            offX = -bgPosMatch.groupValues[1].toInt()
                            offY = -bgPosMatch.groupValues[2].toInt()
                        }
                    } catch (e: Exception) { }
                }

                if (thumbUrl.isEmpty()) thumbUrl = link.selectFirst("img")?.attr("src") ?: ""

                if (href.isNotEmpty() && thumbUrl.isNotEmpty()) {
                    pages.add(GalleryPageDto(baseIndex + i, thumbUrl, href, width, height, offX, offY))
                    i++
                }
            }
            return@withContext Triple(coverUrl, pages, totalWebPages)
        } catch (e: Exception) {
            return@withContext Triple("", emptyList(), 0)
        }
    }

    suspend fun getRealImageUrl(pageUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(pageUrl).cookies(COOKIES).headers(HEADERS).userAgent(USER_AGENT).timeout(20000).get()
            return@withContext doc.selectFirst("img#img")?.attr("src")
                ?: doc.selectFirst("div#i3 img")?.attr("src")
                ?: ""
        } catch (e: Exception) { "" }
    }
}