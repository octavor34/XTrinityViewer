package com.xtrinityviewer.data

import com.xtrinityviewer.viewmodel.FileFilter

// ==========================================
// 1. RULE 34
// ==========================================
class R34Module : SiteModule {
    override val name = "Rule34"
    var apiKey: String = ""
    var userId: String = ""

    override suspend fun getPosts(page: Int, tags: List<String>, filter: FileFilter): List<UnifiedPost> {
        val filterTag = if (filter == FileFilter.GAL) "" else filter.tagToInject
        val cleanTags = (tags + filterTag).filter { it.isNotBlank() }.joinToString(" ") { it.replace(" ", "_") }

        val res = NetworkModule.api.getR34Posts(
            page = page,
            tags = cleanTags.ifBlank { "all" },
            apiKey = apiKey,
            userId = userId
        )
        return res.map { dto ->
            val type = when (dto.file_url.substringAfterLast('.', "").lowercase()) {
                "mp4", "webm" -> MediaType.VIDEO; "gif" -> MediaType.GIF; else -> MediaType.IMAGE
            }
            UnifiedPost(
                id = dto.id.toString(),
                url = dto.file_url,
                previewUrl = dto.preview_url ?: dto.sample_url ?: dto.file_url,
                type = type,
                source = SourceType.R34,
                title = dto.tags.split(" ").take(3).joinToString(" "),
                tags = dto.tags.split(" ").filter { it.isNotBlank() }
            )
        }
    }

    override suspend fun getAutocomplete(query: String): List<AutocompleteDto> {
        val cleanText = query.replace(" ", "_")
        return NetworkModule.api.getAutocomplete(cleanText)
    }
}

// ==========================================
// 2. E621
// ==========================================
class E621Module : SiteModule {
    override val name = "E621"
    var user: String = ""
    var apiKey: String = ""

    override suspend fun getPosts(page: Int, tags: List<String>, filter: FileFilter): List<UnifiedPost> {
        val filterTag = if (filter == FileFilter.GAL) "" else filter.tagToInject
        val cleanTags = (tags + filterTag).filter { it.isNotBlank() }.joinToString(" ") { it.replace(" ", "_") }

        val res = NetworkModule.apiE621.getE621Posts(
            page = page + 1,
            tags = cleanTags,
            login = user,
            apiKey = apiKey
        )
        return res.posts.mapNotNull { dto ->
            val url = dto.file?.url ?: return@mapNotNull null
            val type = when (dto.file.ext?.lowercase()) { "webm", "mp4" -> MediaType.VIDEO; "gif" -> MediaType.GIF; else -> MediaType.IMAGE }
            val allTags = (dto.tags?.general ?: emptyList()) + (dto.tags?.character ?: emptyList())

            UnifiedPost(
                id = dto.id.toString(),
                url = url,
                previewUrl = dto.sample?.url ?: url,
                type = type,
                source = SourceType.E621,
                title = (dto.tags?.general ?: emptyList()).take(3).joinToString(" "),
                tags = allTags
            )
        }
    }
}

// ==========================================
// 3. VERCOMICS PORNO
// ==========================================
class VerComicsLegacyModule : SiteModule {
    override val name = "VerComicsPorno"
    override val requiresWebViewBypass = true

    override suspend fun getPosts(page: Int, tags: List<String>, filter: FileFilter): List<UnifiedPost> {
        val query = tags.joinToString(" ")
        return VerComicsModule.getComics(page, query)
    }

    override suspend fun getAutocomplete(query: String): List<AutocompleteDto> {
        val allTags = VerComicsModule.getTags()
        val clean = query.trim().lowercase()
        return allTags.filter { it.label.lowercase().contains(clean) || it.value.contains(clean) }.take(15)
    }

    override suspend fun getDetails(post: UnifiedPost): List<GalleryPageDto> {
        return VerComicsModule.getChapterImages(post.url)
    }
}

// ==========================================
// 4. REALBOORU
// ==========================================
class RealbooruModuleWrapper : SiteModule {
    override val name = "Realbooru"

    override suspend fun getPosts(page: Int, tags: List<String>, filter: FileFilter): List<UnifiedPost> {
        val filterTag = if (filter == FileFilter.GAL) "" else filter.tagToInject
        val cleanTags = (tags + filterTag).filter { it.isNotBlank() }.joinToString(" ")
        return RealbooruModule.getPosts(page, cleanTags)
    }

    override suspend fun resolveDirectLink(post: UnifiedPost): String {
        return RealbooruModule.getOriginalUrl(post.id, post.previewUrl)
    }
}

// ==========================================
// 5. 4CHAN
// ==========================================
class FourChanModuleWrapper : SiteModule {
    override val name = "4Chan"
    override suspend fun getPosts(page: Int, tags: List<String>, filter: FileFilter): List<UnifiedPost> {
        val board = tags.firstOrNull()?.replace("/", "") ?: "gif"
        val threads = FourChanModule.getThreads(page, board)
        return if (filter == FileFilter.COUNT) {
            threads.sortedByDescending { post ->
                post.tags.find { it.startsWith("R:") }
                    ?.substringAfter("R:")
                    ?.toIntOrNull() ?: 0
            }
        } else {
            threads
        }
    }

    override suspend fun getAutocomplete(query: String): List<AutocompleteDto> {
        val boards = FourChanModule.getBoardsList()
        val clean = query.replace("/", "").lowercase()
        return if (clean.isEmpty()) boards else boards.filter {
            it.value.contains(clean) || it.label.lowercase().contains(clean)
        }
    }

    override suspend fun getDetails(post: UnifiedPost): List<GalleryPageDto> {
        try {
            val boardTag = post.tags.find { it.startsWith("/") && it.endsWith("/") }
            val board = boardTag?.replace("/", "") ?: "gif"
            val threadId = post.id.toLongOrNull() ?: return emptyList()
            val threadPosts = FourChanModule.getThreadPosts(board, threadId)
            return threadPosts.mapIndexed { index, item ->
                GalleryPageDto(
                    index = index,
                    thumbUrl = item.previewUrl,
                    viewerUrl = item.url
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf(GalleryPageDto(0, post.previewUrl, post.url))
        }
    }
}

// ==========================================
// 6. REDDIT
// ==========================================
class RedditModuleWrapper : SiteModule {
    override val name = "Reddit"

    override suspend fun getPosts(page: Int, tags: List<String>, filter: FileFilter): List<UnifiedPost> {
        return if (tags.isEmpty()) {
            RedditModule.getPosts(page, "popular")
        } else {
            RedditModule.getPosts(page, tags[0])
        }
    }

    override suspend fun resolveDirectLink(post: UnifiedPost): String {
        val cleanUrl = post.url.replace("&amp;", "&")

        val isDirectImage = cleanUrl.contains("preview.redd.it") ||
                cleanUrl.contains("i.redd.it") ||
                cleanUrl.contains("external-preview")

        return if (isDirectImage) {
            cleanUrl
        } else {
            if (post.previewUrl.isNotEmpty()) {
                post.previewUrl.replace("&amp;", "&")
            } else {
                cleanUrl
            }
        }
    }

    override suspend fun getAutocomplete(query: String): List<AutocompleteDto> {
        return RedditModule.searchSubreddits(query)
    }

    override suspend fun getDetails(post: UnifiedPost): List<GalleryPageDto> {
        return RedditModule.getGalleryImages(post.id)
    }
}

// ==========================================
// 7. E-HENTAI
// ==========================================
class EHentaiModuleWrapper : SiteModule {
    override val name = "E-Hentai"

    override suspend fun getPosts(page: Int, tags: List<String>, filter: FileFilter): List<UnifiedPost> {
        val query = tags.joinToString(" ")
        return EHentaiModule.getGalleries(page, query)
    }

    override suspend fun getDetails(post: UnifiedPost): List<GalleryPageDto> {
        val result = EHentaiModule.getGalleryPageChunk(post.id, 0)
        return result.second
    }

    override suspend fun resolveDirectLink(post: UnifiedPost): String {
        return EHentaiModule.getRealImageUrl(post.id)
    }
}