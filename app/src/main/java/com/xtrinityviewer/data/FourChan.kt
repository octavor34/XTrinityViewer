package com.xtrinityviewer.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object FourChanModule {
    private const val IMG_BASE = "https://i.4cdn.org"
    private const val THUMB_BASE = "https://t.4cdn.org"

    suspend fun getBoardsList(): List<AutocompleteDto> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.api4Chan.get4ChanBoards()
            return@withContext response.boards.map { board ->
                AutocompleteDto(
                    label = "/${board.board}/ - ${board.title}",
                    value = board.board
                )
            }
        } catch (e: Exception) {
            Log.e("4CHAN", "Error fetching boards: ${e.message}")
            return@withContext emptyList()
        }
    }
    suspend fun getThreads(page: Int, boardQuery: String): List<UnifiedPost> = withContext(Dispatchers.IO) {

        val board = if (boardQuery.isBlank()) "gif" else boardQuery.trim().lowercase()
        val apiPage = page + 1

        Log.d("4CHAN", "Fetching board: /$board/ page: $apiPage")

        val response = NetworkModule.api4Chan.get4ChanPage(board, apiPage)

        return@withContext response.threads.mapNotNull { thread ->
            val op = thread.posts.firstOrNull()
            mapToUnifiedPost(op, board)
        }
    }
    suspend fun getThreadPosts(board: String, threadId: Long): List<UnifiedPost> = withContext(Dispatchers.IO) {
        try {
            Log.d("4CHAN", "Fetching thread: /$board/$threadId")
            val response = NetworkModule.api4Chan.get4ChanThread(board, threadId)

            return@withContext response.posts.mapNotNull { post ->
                mapToUnifiedPost(post, board)
            }
        } catch (e: Exception) {
            Log.e("4CHAN", "Error Thread: ${e.message}")
            return@withContext emptyList()
        }
    }
    private fun mapToUnifiedPost(op: FourChanPostDto?, board: String): UnifiedPost? {
        if (op != null && op.tim != null && op.ext != null) {
            val fullUrl = "$IMG_BASE/$board/${op.tim}${op.ext}"
            val thumbUrl = "$THUMB_BASE/$board/${op.tim}s.jpg"

            val type = when(op.ext) {
                ".webm", ".mp4" -> MediaType.VIDEO
                ".gif" -> MediaType.GIF
                else -> MediaType.IMAGE
            }

            val rawComment = op.com ?: op.sub ?: ""
            val cleanComment = Jsoup.parse(rawComment).text()

            val filename = "${op.tim}${op.ext}"
            val replyCount = op.replies ?: 0
            val replyTag = "R:$replyCount"

            return UnifiedPost(
                id = op.no.toString(),
                url = fullUrl,
                previewUrl = thumbUrl,
                type = type,
                source = SourceType.CHAN,
                title = cleanComment,
                tags = listOf(filename, "/$board/", replyTag),
                aspectRatio = if (op.w != null && op.h != null) op.w.toFloat() / op.h.toFloat() else 1f
            )
        }
        return null
    }
}