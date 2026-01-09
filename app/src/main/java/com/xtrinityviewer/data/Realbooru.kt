package com.xtrinityviewer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object RealbooruModule {
    private const val BASE_URL = "https://realbooru.com/index.php?page=post&s=list"
    private const val DETAIL_URL = "https://realbooru.com/index.php?page=post&s=view&id="

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun getPosts(page: Int, tags: String = ""): List<UnifiedPost> = withContext(Dispatchers.IO) {
            val pid = page * 40
            val cleanTags = tags.trim().replace(" ", "+")
            val url = "$BASE_URL&tags=$cleanTags&pid=$pid"

            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(20000).get()
            val thumbElements = doc.select(".thumb > a")
            val deferredPosts = thumbElements.map { link ->
                async {
                    try {
                        val href = link.attr("abs:href")
                        val idMatch = Regex("id=(\\d+)").find(href)
                        val id = idMatch?.groupValues?.get(1)

                        val img = link.selectFirst("img")
                        val thumbUrl = img?.attr("abs:src") ?: ""
                        val fallbackTitle = img?.attr("title") ?: ""

                        if (id != null) {
                            fetchPostDetails(id, thumbUrl, fallbackTitle)
                        } else {
                            null
                        }
                    } catch (e: Exception) { null }
                }
            }
            return@withContext deferredPosts.awaitAll().filterNotNull()
    }

    private fun fetchPostDetails(id: String, thumbUrl: String, fallbackTitle: String): UnifiedPost? {
        try {
            val url = "$DETAIL_URL$id"
            val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(10000).get()
            val container = doc.selectFirst(".imageContainer") ?: return null
            var fullUrl = ""
            var type = MediaType.IMAGE
            val videoSource = container.selectFirst("video source")
            if (videoSource != null) {
                fullUrl = videoSource.attr("abs:src")
                type = MediaType.VIDEO
            } else {
                val img = container.selectFirst("#image")
                if (img != null) {
                    fullUrl = img.attr("abs:src")
                    if (fullUrl.endsWith(".gif", true)) type = MediaType.GIF
                }
            }

            if (fullUrl.isEmpty()) return null
            val tagsList = doc.select("a").filter { element ->
                element.classNames().any { it.startsWith("tag") }
            }.map { it.text().trim() }
                .filter { it.isNotEmpty() && !it.contains(Regex("[+?-]")) }
                .toSet().toList()
            val finalTags = if (tagsList.isNotEmpty()) tagsList else fallbackTitle.split(" ").filter { it.length > 1 }

            return UnifiedPost(
                id = id,
                url = fullUrl,
                previewUrl = thumbUrl,
                type = type,
                source = SourceType.REALBOORU,
                title = finalTags.take(3).joinToString(" "),
                tags = finalTags
            )
        } catch (e: Exception) { return null }
    }

    suspend fun getOriginalUrl(id: String, thumbUrl: String? = null): String = withContext(Dispatchers.IO) {
        val post = fetchPostDetails(id, thumbUrl ?: "", "")
        return@withContext post?.url ?: thumbUrl ?: ""
    }
}