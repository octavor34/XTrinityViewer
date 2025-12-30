package com.xtrinityviewer.data

enum class SourceType { R34, REDDIT, CHAN, EHENTAI, E621, REALBOORU, VERCOMICS }

enum class MediaType { IMAGE, GIF, VIDEO, GALLERY }

data class UnifiedPost(
    val id: String,
    val url: String,
    val previewUrl: String,
    val type: MediaType,
    val source: SourceType,
    val title: String,
    val tags: List<String> = emptyList(),
    val aspectRatio: Float = 1.0f,
    val spriteWidth: Int? = null,
    val spriteHeight: Int? = null,
    val spriteX: Int? = null,
    val spriteY: Int? = null
)

data class R34Dto(
    val id: Int,
    val file_url: String,
    val preview_url: String?,
    val sample_url: String?,
    val tags: String,
    val height: Int,
    val width: Int
)
data class AutocompleteDto(
    val label: String,
    val value: String
)
// --- Soporte para Sprites (Recortes) ---
data class GalleryPageDto(
    val index: Int,
    val thumbUrl: String,
    val viewerUrl: String,
    val thumbWidth: Int? = null,
    val thumbHeight: Int? = null,
    val thumbX: Int? = null,
    val thumbY: Int? = null
)

data class E621Response(
    val posts: List<E621PostDto>
)
data class E621PostDto(
    val id: Int,
    val file: E621File?,
    val sample: E621Sample?,
    val tags: E621Tags?,
    val description: String?
)
data class E621File(
    val url: String?,
    val ext: String?,
    val width: Int,
    val height: Int
)
data class E621Sample(
    val url: String?
)
data class E621Tags(
    val general: List<String>,
    val character: List<String>,
    val copyright: List<String>,
    val artist: List<String>
)

data class FourChanPageDto(
    val threads: List<FourChanThreadContainer>
)
data class FourChanThreadContainer(
    val posts: List<FourChanPostDto>
)
data class FourChanPostDto(
    val no: Long,
    val tim: Long?,
    val ext: String?,
    val sub: String?,
    val com: String?,
    val w: Int?,
    val h: Int?,
    val replies: Int?
)
data class FourChanBoardsResponse(
    val boards: List<FourChanBoardDto>
)
data class FourChanBoardDto(
    val board: String,
    val title: String,
    val meta_description: String?
)
// --- REDDIT DTOs ---
data class RedditResponse(
    val data: RedditData)
data class RedditData(
    val children: List<RedditChild>,
    val after: String?
)
data class RedditChild(
    val data: RedditPostData
)
data class RedditPostData(
    val id: String,
    val title: String,
    val subreddit_name_prefixed: String?,
    val url: String?,
    val url_overridden_by_dest: String?,
    val thumbnail: String?,
    val is_video: Boolean?,
    val is_self: Boolean?,
    val domain: String?,
    val media: RedditMedia?,
    val secure_media: RedditMedia?,
    val preview: RedditPreview?,
    val is_gallery: Boolean?,
    val gallery_data: RedditGalleryData?,
    val media_metadata: Map<String, RedditMediaMetadata>?
)
data class RedditMedia(
    val reddit_video: RedditVideo?
)
data class RedditVideo(
    val fallback_url: String?,
    val hls_url: String?
)

data class RedditPreview(
    val images: List<RedditPreviewImage>?,
    val reddit_video_preview: RedditVideo?
)

data class RedditPreviewImage(
    val source: RedditImageSource?,
    val resolutions: List<RedditImageSource>?,
    val variants: RedditPreviewVariants?
)

data class RedditPreviewVariants(
    val mp4: RedditVariantItem?,
    val gif: RedditVariantItem?
)

data class RedditVariantItem(
    val source: RedditImageSource?
)

data class RedditImageSource(
    val url: String?, val u: String?,
    val width: Int?, val x: Int?,
    val height: Int?, val y: Int?
) {
    fun getEffectiveUrl(): String? = (url ?: u)?.replace("&amp;", "&")
    fun getWidth(): Int = width ?: x ?: 0
    fun getHeight(): Int = height ?: y ?: 0
}

data class RedditGalleryData(
    val items: List<RedditGalleryItem>
)
data class RedditGalleryItem(
    val media_id: String
)

data class RedditMediaMetadata(
    val s: RedditImageSource?,
    val e: String?,
    val p: List<RedditImageSource>?,
    val o: List<RedditImageSource>?
)

data class RedditSearchResponse(
    val data: RedditSearchData?
)
data class RedditSearchData(
    val children: List<RedditSearchChild>?
)
data class RedditSearchChild(
    val data: RedditSubredditData?
)
data class RedditSubredditData(
    val display_name: String?,
    val display_name_prefixed: String?,
    val subscribers: Long?
)