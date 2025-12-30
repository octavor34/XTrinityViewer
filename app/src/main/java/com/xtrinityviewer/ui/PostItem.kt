package com.xtrinityviewer.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.videoFrameMillis
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asImageBitmap
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.xtrinityviewer.data.MediaType
import com.xtrinityviewer.data.SourceType
import com.xtrinityviewer.data.UnifiedPost
import com.xtrinityviewer.data.VerComicsModule
import com.xtrinityviewer.util.Optimizer
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PostItem(
    post: UnifiedPost,
    isVisible: Boolean,
    onDetailsClick: () -> Unit,
    onContentClick: () -> Unit,
    onDownloadClick: (UnifiedPost) -> Unit,
    onBlockTag: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val interactionSource = remember { MutableInteractionSource() }
    var showOverlay by remember { mutableStateOf(true) }
    var expandedTagIndex by remember { mutableStateOf(-1) }
    val isBooruSource = remember(post.source) {
        post.source == SourceType.R34 ||
                post.source == SourceType.E621 ||
                post.source == SourceType.REALBOORU
    }

    // Estado de reproducción manual
    var isPlaying by remember { mutableStateOf(false) }

    // DETECCIÓN CORRECTA DE VIDEO (Incluyendo Reddit GIFs)
    val isVideoFeed = (post.type == MediaType.VIDEO && post.source != SourceType.CHAN) ||
            (post.source == SourceType.REDDIT && post.type == MediaType.GIF)

    // DETECCIÓN CORRECTA DE GIF (Excluyendo los de Reddit que ahora son video)
    val isGifFeed = post.type == MediaType.GIF && post.source != SourceType.REDDIT && post.source != SourceType.CHAN

    val autoPlayEnabled = remember(post.source) {
        when(post.source) {
            // Sitios Manuales
            SourceType.R34, SourceType.E621, SourceType.REALBOORU -> false
            // Sitios Auto (Reddit, Chan, etc)
            else -> true
        }
    }
    var imageLoadError by remember { mutableStateOf(false) }
    var retryHash by remember { mutableStateOf(0) }
    val isSprite = post.spriteWidth != null && post.spriteHeight != null

    LaunchedEffect(isVideoFeed, isVisible) {
        if (!isVideoFeed && isVisible) showOverlay = true

        // REGLA DE ORO: Si no es visible, MATAMOS la reproducción.
        if (!isVisible) isPlaying = false

        // Si se hace visible y tiene autoplay, activamos.
        if (isVisible && autoPlayEnabled) isPlaying = true
    }

    // --- OPTIMIZACIÓN DE COIL (CACHE GLOBAL) ---
    val imageLoader = LocalContext.current.imageLoader

    val requestBuilder = remember(post.url, post.previewUrl, isGifFeed, retryHash) {

        // 1. ELEGIR LA FUENTE DE LA IMAGEN
        val targetUrl = when {
            post.source == SourceType.CHAN && post.type == MediaType.GIF -> post.url
            post.source == SourceType.CHAN -> post.previewUrl
            post.source == SourceType.VERCOMICS -> post.previewUrl
            // PARA R34, E621 y REALBOORU: Usamos PREVIEW (Sample) en el feed
            post.source == SourceType.R34 -> post.previewUrl
            post.source == SourceType.E621 -> post.previewUrl
            post.source == SourceType.REALBOORU -> post.previewUrl
            post.source == SourceType.REDDIT && isVideoFeed -> post.url
            isGifFeed -> post.url
            isVideoFeed -> post.url
            post.previewUrl.isNotEmpty() -> post.previewUrl
            else -> Optimizer.optimize(post.url)
        }

        val userAgent = if (post.source == SourceType.VERCOMICS) VerComicsModule.USER_AGENT
        else "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val builder = ImageRequest.Builder(context)
            .data(targetUrl)
            .crossfade(false)
            .diskCacheKey(targetUrl)
            .setHeader("User-Agent", userAgent)
            .size(width = 600, height = 1000)
            .precision(coil.size.Precision.INEXACT)
            .apply {
                if (isVideoFeed && !isGifFeed) {
                    videoFrameMillis(2000)
                }
            }
        // NOTA: Borré el .videoFrameMillis(2000) que tenías aquí afuera duplicado
        // porque eso estaba congelando los GIFs normales.

        // 2. HEADERS
        if (post.source == SourceType.EHENTAI) {
            builder.addHeader("Referer", "https://e-hentai.org/")
            builder.addHeader("Cookie", "nw=1")
        }
        else if (post.source == SourceType.VERCOMICS) {
            builder.addHeader("Referer", "https://vercomicsporno.com/")
            builder.addHeader("Cookie", VerComicsModule.getCookiesAsString())
        }
        else if (post.source == SourceType.REALBOORU) {
            builder.addHeader("Referer", "https://realbooru.com/")
        }

        builder.build()
    }

    var spriteBitmap by remember { mutableStateOf<Bitmap?>(null) }

    if (isSprite) {
        DisposableEffect(post.previewUrl, retryHash) {
            val job = scope.launch(Dispatchers.IO) {
                val req = ImageRequest.Builder(context)
                    .data(post.previewUrl)
                    .addHeader("Cookie", "nw=1")
                    .addHeader("Referer", "https://e-hentai.org/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(false) // Necesario para Canvas
                    .build()

                val res = ImageLoader(context).execute(req)
                if (res.drawable != null) {
                    spriteBitmap = res.drawable?.toBitmap()
                    imageLoadError = false
                } else {
                    imageLoadError = true
                }
            }
            onDispose { job.cancel() }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                if (isVideoFeed) {
                    if (!autoPlayEnabled) {
                        isPlaying = !isPlaying
                        showOverlay = !isPlaying
                    } else {
                        showOverlay = !showOverlay
                    }
                } else {
                    onContentClick()
                }
            },
            onLongClick = { onDownloadClick(post) }
        )
    ) { if (imageLoadError) {
            // [NUEVO] UI de Error cuando falla la imagen (en vez de negro)
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "Error",
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        imageLoadError = false
                        retryHash++ // Esto fuerza a Coil a reintentar
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reintentar", fontSize = 12.sp)
                }
            }
        } else {
        if (isSprite) {
            // MODO SPRITE (Canvas recorte)
            if (spriteBitmap != null) {
                val bmp = spriteBitmap!!.asImageBitmap()
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tX = post.spriteX ?: 0
                    val tY = post.spriteY ?: 0
                    val tW = post.spriteWidth ?: 100
                    val tH = post.spriteHeight ?: 100

                    // Dibujar recortado y escalado para llenar el item
                    // Calculamos escala para 'Fit' o 'Crop' según prefieras. Aquí simulamos 'Fit/FillWidth'
                    val scale = size.width / tW
                    val dstHeight = tH * scale

                    // Centrar verticalmente si sobra espacio
                    val yOffset = (size.height - dstHeight) / 2

                    drawImage(
                        image = bmp,
                        srcOffset = IntOffset(tX, tY),
                        srcSize = IntSize(tW, tH),
                        dstSize = IntSize(size.width.toInt(), dstHeight.toInt()),
                        dstOffset = IntOffset(0, yOffset.toInt()),
                        filterQuality = FilterQuality.Low
                    )
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFE91E63))
            }
        }
        else if (isVideoFeed && isVisible && isPlaying) {
            // MODO VIDEO PLAYER
            VideoPlayer(
                url = post.url, isVisible = true, showControls = true, autoPlay = true,
                headers = emptyMap(), onPlayingChange = { playing -> showOverlay = !playing },
                onLongPress = { onDownloadClick(post) }
            )
        } else {
            // MODO IMAGEN NORMAL
            AsyncImage(
                model = requestBuilder,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = { imageLoadError = true }
            )
        }
            // --- OVERLAY (INFO DEL POST) ---
            AnimatedVisibility(visible = showOverlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    var badgeText = "IMG"
                    var badgeColor = Color(0xFF4CAF50)

                    when (post.type) {
                        MediaType.GALLERY -> { badgeText = "GAL"; badgeColor = Color(0xFFE91E63) }
                        MediaType.VIDEO -> {
                            if (post.source == SourceType.REDDIT) { badgeText = "GIF"; badgeColor = Color(0xFF9C27B0) }
                            else { badgeText = "VID"; badgeColor = Color(0xFFE91E63) }
                        }
                        MediaType.GIF -> { badgeText = "GIF"; badgeColor = Color(0xFF9C27B0) }
                        MediaType.IMAGE -> {
                            val cleanUrl = post.url.substringBefore('?')
                            val ext = cleanUrl.substringAfterLast('.', "").uppercase()
                            badgeText = if (ext.length in 3..4) ext else "IMG"
                            badgeColor = Color(0xFF4CAF50)
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 16.dp).background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small).padding(6.dp)) {
                        Text(badgeText, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Box(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(0.9f))))
                            .clickable { onDetailsClick() }
                            .padding(16.dp).padding(bottom = 30.dp)
                    ) {
                        Column {
                            Row (verticalAlignment = Alignment.CenterVertically) {
                                val sourceName = post.source.name
                                val sourceColor = when(post.source) {
                                    SourceType.EHENTAI -> Color(0xFFE91E63)
                                    SourceType.REALBOORU -> Color(0xFFFF9800)
                                    SourceType.E621 -> Color(0xFF003E6B)
                                    SourceType.CHAN -> Color(0xFF1B5E20)
                                    SourceType.REDDIT -> Color(0xFFFF5700)
                                    SourceType.VERCOMICS -> Color(0xFFC0CA33)
                                    else -> Color(0xFFAAE5A4)
                                }
                                Badge(text = sourceName, color = sourceColor)
                                Spacer(modifier = Modifier.width(8.dp))

                                // Lógica de visualización condicional
                                if (isBooruSource) {
                                    Badge(text = "Lista de Tags", color = Color.Gray)
                                } else {
                                    if (post.source == SourceType.CHAN) {
                                        val replyTag = post.tags.find { it.startsWith("R:") }
                                        if (replyTag != null) {
                                            val count = replyTag.removePrefix("R:")
                                            Badge(text = "$count Posts", color = Color(0xFFFFD600))
                                        }
                                    }
                                    // No ponemos nada más aquí en la fila del título, dejamos el texto abajo
                                }
                            }

                            // Título/Descripción siempre visible abajo
                            if (!isBooruSource && post.title.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = post.title,
                                    color = Color.White,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    val textColor = if (color == Color(0xFFAAE5A4)) Color.Black else Color.White
    Text(text = text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 2.dp))
}