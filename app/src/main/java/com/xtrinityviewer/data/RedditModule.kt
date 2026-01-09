package com.xtrinityviewer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


object RedditModule {
    private val paginationMap = mutableMapOf<Int, String>()
    fun resetPagination() {
        paginationMap.clear()
    }
    suspend fun searchSubreddits(query: String): List<AutocompleteDto> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.apiReddit.getRedditAutocomplete(query = query)
            val children = response.data?.children ?: emptyList()

            return@withContext children.mapNotNull { child ->
                val sub = child.data ?: return@mapNotNull null
                val name = sub.display_name ?: return@mapNotNull null
                val prefixed = sub.display_name_prefixed ?: "r/$name"
                val subsCount = sub.subscribers ?: 0
                AutocompleteDto(label = "$prefixed (${subsCount / 1000}k)", value = name)
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }
    suspend fun getPosts(page: Int, subredditQuery: String): List<UnifiedPost> = withContext(Dispatchers.IO) {
        val subreddit = if (subredditQuery.isBlank()) "popular" else subredditQuery.trim().replace("r/", "").trim()
        val after = if (page == 0) null else paginationMap[page]

        if (page > 0 && after == null) return@withContext emptyList()

        val response = NetworkModule.apiReddit.getRedditPosts(subreddit = subreddit, after = after)

        response.data.after?.let { nextToken -> paginationMap[page + 1] = nextToken }

        return@withContext response.data.children.mapNotNull { child -> mapToUnifiedPost(child.data) }
    }
    private fun mapToUnifiedPost(p: RedditPostData): UnifiedPost? {
        if (p.is_self == true) return null

        var src = (p.url_overridden_by_dest ?: p.url ?: "").replace("&amp;", "&")
        var prev = (p.thumbnail ?: "").replace("&amp;", "&")
        var type = MediaType.IMAGE

        if (p.is_gallery == true && p.media_metadata != null && p.gallery_data != null) {
            type = MediaType.GALLERY
            val firstItem = p.gallery_data.items.firstOrNull()

            if (firstItem != null) {
                val metadata = p.media_metadata[firstItem.media_id]
                val sourceUrl = metadata?.s?.getEffectiveUrl()?.replace("&amp;", "&")

                if (sourceUrl != null) {
                    src = sourceUrl
                    val previews = metadata.p
                    prev = if (!previews.isNullOrEmpty()) {
                        previews.last().getEffectiveUrl()?.replace("&amp;", "&") ?: src
                    } else {
                        src
                    }
                }
            }
        }
        else if (p.is_video == true || p.domain?.contains("v.redd.it") == true || p.domain?.contains("redgifs") == true || src.contains("redgifs.com")) {
            type = MediaType.VIDEO

            var videoUrl: String? = null
            if (p.domain?.contains("v.redd.it") == true) {
                videoUrl = p.secure_media?.reddit_video?.hls_url
                    ?: p.media?.reddit_video?.hls_url
            }
            if (videoUrl == null) {
                videoUrl = p.secure_media?.reddit_video?.fallback_url
                    ?: p.media?.reddit_video?.fallback_url
                            ?: p.preview?.reddit_video_preview?.fallback_url
                            ?: p.preview?.images?.firstOrNull()?.variants?.mp4?.source?.getEffectiveUrl()
            }
            if (!videoUrl.isNullOrEmpty()) {
                src = videoUrl.replace("&amp;", "&")

                val resolutions = p.preview?.images?.firstOrNull()?.resolutions
                if (!resolutions.isNullOrEmpty()) {
                    prev = resolutions.last().getEffectiveUrl()?.replace("&amp;", "&") ?: prev
                }
            }
            else if (src.endsWith(".gifv")) {
                src = src.replace(".gifv", ".mp4")
            }
        }
        else if (src.endsWith(".gif")) {
            val hiddenMp4 = p.preview?.images?.firstOrNull()?.variants?.mp4?.source?.getEffectiveUrl()
            if (hiddenMp4 != null) {
                src = hiddenMp4.replace("&amp;", "&")
                type = MediaType.VIDEO
            } else {
                type = MediaType.GIF
            }
        }
        if (type == MediaType.IMAGE || type == MediaType.GIF) {
            val resolutions = p.preview?.images?.firstOrNull()?.resolutions
            if (!resolutions.isNullOrEmpty()) {
                prev = resolutions.last().getEffectiveUrl()?.replace("&amp;", "&") ?: prev
            } else {
                val source = p.preview?.images?.firstOrNull()?.source
                if (source != null) {
                    prev = source.getEffectiveUrl()?.replace("&amp;", "&") ?: prev
                }
            }
        }

        if (prev == "self" || prev == "default" || prev == "nsfw" || prev.isEmpty() || !prev.startsWith("http")) {

            if (type != MediaType.VIDEO) {
                prev = src
            } else {
                prev = src
            }
        }

        if (src.isEmpty() || !src.startsWith("http")) return null

        val w = p.preview?.images?.firstOrNull()?.source?.getWidth() ?: 1
        val h = p.preview?.images?.firstOrNull()?.source?.getHeight() ?: 1
        val ratio = if(h > 0) w.toFloat() / h.toFloat() else 1f

        return UnifiedPost(
            id = p.id,
            url = src,
            previewUrl = prev,
            type = type,
            source = SourceType.REDDIT,
            title = p.title ?: "",
            tags = listOf(p.subreddit_name_prefixed ?: "u/reddit"),
            aspectRatio = ratio
        )
    }
    suspend fun getGalleryImages(postId: String): List<GalleryPageDto> = withContext(Dispatchers.IO) {
        try {
            val cleanId = postId.replace("t3_", "")
            val url = "https://www.reddit.com/comments/$cleanId.json?raw_json=1"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            val response = NetworkModule.client.newCall(request).execute()
            val jsonString = response.body?.string() ?: return@withContext emptyList()
            val parser = com.google.gson.JsonParser()
            val jsonElement = parser.parse(jsonString)
            val jsonArray = jsonElement.asJsonArray
            val dataObj = jsonArray.get(0).asJsonObject
                .get("data").asJsonObject
                .get("children").asJsonArray.get(0).asJsonObject
                .get("data").asJsonObject
            val galleryItems = mutableListOf<GalleryPageDto>()

            if (dataObj.has("media_metadata")) {
                val mediaMetadata = dataObj.get("media_metadata").asJsonObject

                if (dataObj.has("gallery_data")) {
                    val items = dataObj.get("gallery_data").asJsonObject.get("items").asJsonArray

                    items.forEachIndexed { index, itemElem ->
                        val mediaId = itemElem.asJsonObject.get("media_id").asString
                        if (mediaMetadata.has(mediaId)) {
                            val meta = mediaMetadata.get(mediaId).asJsonObject
                            val status = if (meta.has("e")) meta.get("e").asString else "valid"

                            if (status == "Image" || status == "valid" || status == "AnimatedImage") {
                                if (meta.has("s")) {
                                    val s = meta.get("s").asJsonObject
                                    var rawUrl = ""
                                    if (s.has("u") && !s.get("u").isJsonNull) rawUrl = s.get("u").asString
                                    else if (s.has("gif") && !s.get("gif").isJsonNull) rawUrl = s.get("gif").asString
                                    else if (s.has("mp4") && !s.get("mp4").isJsonNull) rawUrl = s.get("mp4").asString

                                    if (rawUrl.isNotEmpty()) {
                                        val finalUrl = rawUrl.replace("&amp;", "&")
                                        galleryItems.add(GalleryPageDto(index, finalUrl, finalUrl))
                                    }
                                }
                            }
                        }
                    }
                }
                else {
                    var index = 0
                    mediaMetadata.entrySet().forEach { entry ->
                        val meta = entry.value.asJsonObject
                        if (meta.has("s")) {
                            val s = meta.get("s").asJsonObject
                            var rawUrl = ""
                            if (s.has("u")) rawUrl = s.get("u").asString
                            else if (s.has("gif")) rawUrl = s.get("gif").asString

                            if (rawUrl.isNotEmpty()) {
                                val finalUrl = rawUrl.replace("&amp;", "&")
                                galleryItems.add(GalleryPageDto(index++, finalUrl, finalUrl))
                            }
                        }
                    }
                }
            }
            return@withContext galleryItems
        } catch (e: Exception) {
            android.util.Log.e("REDDIT_DEBUG", "Error parseando galería: ${e.message}")
            return@withContext emptyList()
        }
    }
    suspend fun searchInSubreddit(page: Int, subreddit: String, query: String): List<UnifiedPost> = withContext(Dispatchers.IO) {
        val cleanSub = subreddit.replace("r/", "").trim()
        val after = if (page == 0) null else paginationMap[page]

        val response = NetworkModule.apiReddit.searchRedditPosts(
            subreddit = cleanSub,
            query = query,
            restrictSr = "on",
            nsfw = "1",
            sort = "relevance",
            limit = 25,
            after = after
        )

        response.data.after?.let { nextToken -> paginationMap[page + 1] = nextToken }

        return@withContext response.data.children.mapNotNull { child -> mapToUnifiedPost(child.data) }
    }
}