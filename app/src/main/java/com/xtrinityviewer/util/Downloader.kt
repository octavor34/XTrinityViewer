package com.xtrinityviewer.util

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xtrinityviewer.data.EHentaiModule
import com.xtrinityviewer.data.MediaType
import com.xtrinityviewer.data.RedditModule
import com.xtrinityviewer.data.SourceType
import com.xtrinityviewer.data.UnifiedPost
import com.xtrinityviewer.data.VerComicsModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Downloader {

    private const val CHANNEL_ID = "trinity_downloads"
    private const val CHANNEL_NAME = "Descargas Trinity"
    private var notificationIdCounter = 1000

    fun calculateFileName(post: UnifiedPost): String {
        if (post.type == MediaType.GALLERY) {
            return "Trinity_${post.source.name}_${post.id}.zip"
        }

        var extension = when (post.type) {
            MediaType.VIDEO -> ".mp4"
            MediaType.GIF -> ".gif"
            MediaType.IMAGE -> ".jpg"
            else -> ".jpg"
        }
        val cleanUrl = post.url.substringBefore("?")
        if (cleanUrl.endsWith(".png", true)) extension = ".png"
        if (cleanUrl.endsWith(".jpeg", true)) extension = ".jpg"
        if (cleanUrl.endsWith(".webm", true)) extension = ".webm"

        if (post.source == SourceType.REDDIT && post.type == MediaType.GIF && !post.url.endsWith(".gif")) {
            extension = ".mp4"
        }
        return "Trinity_${post.source.name}_${post.id}$extension"
    }

    fun calculateMimeType(post: UnifiedPost): String {
        if (post.type == MediaType.GALLERY) {
            return "application/zip"
        }

        val fileName = calculateFileName(post)
        return when {
            fileName.endsWith(".mp4") -> "video/mp4"
            fileName.endsWith(".webm") -> "video/webm"
            fileName.endsWith(".gif") -> "image/gif"
            fileName.endsWith(".png") -> "image/png"
            fileName.endsWith(".jpg") -> "image/jpeg"
            fileName.endsWith(".jpeg") -> "image/jpeg"
            else -> "*/*"
        }
    }

    suspend fun downloadToUri(context: Context, post: UnifiedPost, destinationUri: Uri) {
        if (post.type == MediaType.GALLERY) {
            downloadGalleryAsZip(context, post, destinationUri)
        } else {
            downloadSingleFile(context, post, destinationUri)
        }
    }

    private suspend fun downloadSingleFile(context: Context, post: UnifiedPost, destinationUri: Uri) = withContext(Dispatchers.IO) {
        val notifyId = notificationIdCounter++
        val manager = NotificationManagerCompat.from(context)
        setupChannel(manager)

        val builder = createBaseNotification(context, "Descargando ${post.source.name}...")

        try {
            try { manager.notify(notifyId, builder.build()) } catch (e: SecurityException) { }
            val client = createClient()
            val request = buildRequest(post, post.url)

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Error HTTP ${response.code}")

            val body = response.body ?: throw Exception("Sin contenido")
            val inputStream: InputStream = body.byteStream()
            val outputStream: OutputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw Exception("Error al escribir archivo")

            val totalLength = body.contentLength()
            var bytesCopied: Long = 0
            val buffer = ByteArray(8 * 1024)
            var bytes = inputStream.read(buffer)
            var lastUpdate = 0L

            while (bytes >= 0) {
                outputStream.write(buffer, 0, bytes)
                bytesCopied += bytes
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 500) {
                    updateProgress(manager, builder, notifyId, bytesCopied, totalLength)
                    lastUpdate = now
                }
                bytes = inputStream.read(buffer)
            }
            outputStream.close()
            inputStream.close()
            response.close()

            finishNotification(context, manager, builder, notifyId, destinationUri, response.header("Content-Type"))

        } catch (e: Exception) {
            failNotification(context, manager, builder, notifyId, e.message)
        }
    }

    private suspend fun downloadGalleryAsZip(context: Context, post: UnifiedPost, destinationUri: Uri) = withContext(Dispatchers.IO) {
        val notifyId = notificationIdCounter++
        val manager = NotificationManagerCompat.from(context)
        setupChannel(manager)

        val builder = createBaseNotification(context, "Preparando Galería...")
        builder.setContentText("Obteniendo enlaces...")
        try { manager.notify(notifyId, builder.build()) } catch (e: SecurityException) { }

        try {
            val client = createClient()
            val imageUrls = fetchGalleryImages(post)

            if (imageUrls.isEmpty()) throw Exception("No se encontraron imágenes en la galería.")

            builder.setContentTitle("Descargando ${imageUrls.size} imágenes")
            builder.setProgress(imageUrls.size, 0, false)
            try { manager.notify(notifyId, builder.build()) } catch (e: SecurityException) { }

            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw Exception("Error creando ZIP")
            val zipOutputStream = ZipOutputStream(outputStream)
            var successCount = 0

            for ((index, url) in imageUrls.withIndex()) {
                try {
                    builder.setContentText("Imagen ${index + 1} de ${imageUrls.size}")
                    builder.setProgress(imageUrls.size, index, false)
                    try { manager.notify(notifyId, builder.build()) } catch (e: SecurityException) { }

                    val request = buildRequest(post, url)
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful && response.body != null) {
                        val ext = url.substringAfterLast(".", "jpg").take(3)
                        val entryName = "imagen_${String.format("%03d", index + 1)}.$ext"

                        zipOutputStream.putNextEntry(ZipEntry(entryName))

                        val input = BufferedInputStream(response.body!!.byteStream())
                        val buffer = ByteArray(4096)
                        var len: Int
                        while (input.read(buffer).also { len = it } > 0) {
                            zipOutputStream.write(buffer, 0, len)
                        }

                        input.close()
                        zipOutputStream.closeEntry()
                        response.close()
                        successCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            zipOutputStream.close()
            outputStream.close()

            if (successCount == 0) throw Exception("Fallaron todas las descargas")

            finishNotification(context, manager, builder, notifyId, destinationUri, "application/zip", "Galería guardada ($successCount imgs)")

        } catch (e: Exception) {
            failNotification(context, manager, builder, notifyId, e.message)
        }
    }

    private suspend fun fetchGalleryImages(post: UnifiedPost): List<String> {
        return try {
            when (post.source) {
                SourceType.VERCOMICS -> VerComicsModule.getChapterImages(post.url).map { it.viewerUrl }
                SourceType.REDDIT -> RedditModule.getGalleryImages(post.id).map { it.viewerUrl }
                SourceType.EHENTAI -> {
                    val (_, chunk, _) = EHentaiModule.getGalleryPageChunk(post.url, 0)
                    chunk.map { it.viewerUrl }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun setupChannel(manager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createBaseNotification(context: Context, title: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Iniciando...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, true)
    }

    private fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private suspend fun buildRequest(post: UnifiedPost, targetUrl: String): Request {
        val requestBuilder = Request.Builder().url(targetUrl)
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        requestBuilder.header("User-Agent", userAgent)

        when (post.source) {
            SourceType.VERCOMICS -> {
                requestBuilder.header("Cookie", VerComicsModule.getCookiesAsString())
                requestBuilder.header("Referer", "https://vercomicsporno.com/")
                requestBuilder.header("User-Agent", VerComicsModule.USER_AGENT)
            }
            SourceType.EHENTAI -> {
                requestBuilder.header("Cookie", "nw=1")
                requestBuilder.header("Referer", "https://e-hentai.org/")

                if (targetUrl.contains("/s/")) {
                    val realUrl = EHentaiModule.getRealImageUrl(targetUrl)
                    if (realUrl.isNotEmpty()) {
                        return Request.Builder().url(realUrl)
                            .header("Cookie", "nw=1")
                            .header("Referer", "https://e-hentai.org/")
                            .header("User-Agent", userAgent)
                            .build()
                    }
                }
            }
            SourceType.REALBOORU -> {
                requestBuilder.header("Referer", "https://realbooru.com/")
            }
            else -> {}
        }
        return requestBuilder.build()
    }

    private fun updateProgress(manager: NotificationManagerCompat, builder: NotificationCompat.Builder, id: Int, current: Long, total: Long) {
        val progress = if (total > 0) ((current * 100) / total).toInt() else 0
        builder.setProgress(100, progress, total <= 0)
        builder.setContentText(if (total > 0) "$progress%" else "Descargando...")
        try { manager.notify(id, builder.build()) } catch (e: SecurityException) { }
    }

    private suspend fun finishNotification(context: Context, manager: NotificationManagerCompat, builder: NotificationCompat.Builder, id: Int, uri: Uri, mimeType: String?, customMsg: String? = null) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val pendingIntent = PendingIntent.getActivity(context, id, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setContentTitle("Descarga Completada")
            .setContentText(customMsg ?: "Guardado correctamente")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try { manager.notify(id, builder.build()) } catch (e: SecurityException) { }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, customMsg ?: "Guardado exitoso", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun failNotification(context: Context, manager: NotificationManagerCompat, builder: NotificationCompat.Builder, id: Int, error: String?) {
        builder.setContentTitle("Error").setContentText(error).setOngoing(false).setProgress(0, 0, false)
        try { manager.notify(id, builder.build()) } catch (ex: SecurityException) { }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Falló: $error", Toast.LENGTH_LONG).show()
        }
    }
}

class CreateSmartDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.second
            putExtra(Intent.EXTRA_TITLE, input.first)
        }
    }
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}