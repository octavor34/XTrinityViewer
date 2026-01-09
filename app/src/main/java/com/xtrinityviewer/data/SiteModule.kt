package com.xtrinityviewer.data

import com.xtrinityviewer.viewmodel.FileFilter

interface SiteModule {
    val name: String
    val requiresWebViewBypass: Boolean get() = false

    suspend fun getPosts(page: Int, tags: List<String>, filter: FileFilter): List<UnifiedPost>

    suspend fun getAutocomplete(query: String): List<AutocompleteDto> = emptyList()

    suspend fun getDetails(post: UnifiedPost): List<GalleryPageDto> {
        return listOf(GalleryPageDto(0, post.previewUrl, post.url))
    }

    suspend fun resolveDirectLink(post: UnifiedPost): String = post.url
}