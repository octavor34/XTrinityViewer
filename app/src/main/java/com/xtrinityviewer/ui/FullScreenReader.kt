package com.xtrinityviewer.ui

import android.os.Build.VERSION.SDK_INT
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.xtrinityviewer.data.MediaType
import com.xtrinityviewer.data.SourceType
import com.xtrinityviewer.data.UnifiedPost
import com.xtrinityviewer.data.VerComicsModule
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.lazy.grid.GridItemSpan


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenReader(
    posts: List<UnifiedPost>,
    initialIndex: Int,
    initialGalleryMode: Boolean = false,
    onBack: () -> Unit,
    onLoadHd: suspend (UnifiedPost) -> String,
    onDownload: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenGallery: (UnifiedPost) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ESTADOS
    var isGalleryMode by remember(initialGalleryMode) { mutableStateOf(initialGalleryMode) }
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { posts.size })
    var isZoomActive by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    val currentPost = posts.getOrNull(pagerState.currentPage)
    val isStaticBoard = remember(currentPost) {
        val src = currentPost?.source
        src == SourceType.R34 ||
                src == SourceType.E621 ||
                src == SourceType.REALBOORU
    }
    val allowSwipe = !isStaticBoard
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isGalleryMode) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(56.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.Default.ArrowBack, "Atrás", tint = Color.White)
                    }
                    Text("${posts.size} Archivos", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    IconButton(onClick = { isGalleryMode = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.ViewCarousel, "Modo Lector", tint = Color.White)
                    }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(posts) { index, post ->
                        Box(Modifier.aspectRatio(1f).padding(2.dp).clickable {
                            if (post.source == SourceType.EHENTAI ||
                                post.source == SourceType.VERCOMICS ||
                                (post.source == SourceType.REDDIT && post.type == MediaType.GALLERY)) {
                                onOpenGallery(post)
                            } else {
                                scope.launch { pagerState.scrollToPage(index) }
                                isGalleryMode = false
                            }
                        }) {
                            AsyncImage(
                                model = post.previewUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            var badgeText = "IMG"
                            var badgeColor = Color(0xFF4CAF50) // Verde por defecto

                            when (post.type) {
                                MediaType.GALLERY -> {
                                    badgeText = "GAL"
                                    badgeColor = Color(0xFFE91E63) // Rosa fuerte
                                }
                                MediaType.VIDEO -> {
                                    if (post.source == SourceType.REDDIT) {
                                        badgeText = "GIF"
                                        badgeColor = Color(0xFF9C27B0) // Morado
                                    } else {
                                        badgeText = "VID"
                                        badgeColor = Color(0xFFE91E63)
                                    }
                                }
                                MediaType.GIF -> {
                                    badgeText = "GIF"
                                    badgeColor = Color(0xFF9C27B0)
                                }
                                MediaType.IMAGE -> {
                                    val cleanUrl = post.url.substringBefore('?')
                                    val ext = cleanUrl.substringAfterLast('.', "").uppercase()
                                    badgeText = if (ext == "PNG") "PNG" else "IMG"
                                    badgeColor = Color(0xFF4CAF50)
                                }
                            }

                            Box(Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(badgeColor.copy(0.8f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp)
                            ) {
                                Text(badgeText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Button(
                                    onClick = onLoadMore,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("CARGAR SIGUIENTE LOTE", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                }
            }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomActive && allowSwipe,
                key = { index -> if (index in posts.indices) posts[index].id else index }
            ) { pageIndex ->
                if (pageIndex !in posts.indices) return@VerticalPager

                val post = posts[pageIndex]
                val isPageVisible = pagerState.currentPage == pageIndex

                val realUrlState = produceState(initialValue = "", key1 = post.id) {
                    value = onLoadHd(post)
                }
                val realUrl = realUrlState.value

                val resolvedType = remember(realUrl, post.type) {
                    if (realUrl.isEmpty()) post.type
                    else {
                        val cleanUrl = realUrl.substringBefore('?')
                        val ext = cleanUrl.substringAfterLast('.', "").lowercase()
                        when {
                            ext in listOf("mp4", "webm", "mkv", "mov", "avi") -> MediaType.VIDEO
                            ext == "gif" -> MediaType.GIF
                            else -> MediaType.IMAGE
                        }
                    }
                }

                val requestHeaders = remember(post.source) {
                    val headers = mutableMapOf<String, String>()
                    when (post.source) {
                        SourceType.EHENTAI -> {
                            headers["Referer"] = "https://e-hentai.org/"
                            headers["Cookie"] = "nw=1"
                        }
                        SourceType.VERCOMICS -> {
                            headers["Referer"] = "https://vercomicsporno.com/"
                            headers["Cookie"] = VerComicsModule.getCookiesAsString()
                            headers["User-Agent"] = VerComicsModule.USER_AGENT
                        }
                        SourceType.REALBOORU -> headers["Referer"] = "https://realbooru.com/"
                        SourceType.CHAN -> headers["Referer"] = "https://boards.4chan.org/"
                        SourceType.REDDIT -> headers["Referer"] = "https://www.reddit.com/"
                        else -> {}
                    }
                    headers
                }

                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    if (realUrl.isNotEmpty()) {
                        if (resolvedType == MediaType.VIDEO) {
                            if (isPageVisible) isZoomActive = false
                            VideoPlayer(
                                url = realUrl,
                                isVisible = isPageVisible,
                                showControls = false,
                                autoPlay = false,
                                headers = requestHeaders,
                                onPlayingChange = { isPlaying -> },
                                onControllerVisibilityChanged = { visible ->
                                    if (isPageVisible) showOverlay = visible
                                }
                            )
                        } else {
                            LaunchedEffect(isPageVisible) {
                                if (isPageVisible && !showOverlay) showOverlay = true
                            }
                            ZoomableImageContainer(
                                url = realUrl,
                                source = post.source,
                                onZoomChange = { zoomed -> if (isPageVisible) isZoomActive = zoomed },
                                onToggleUI = { showOverlay = !showOverlay },
                                onLongPress = {}
                            )
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                    }
                }
            }

            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 40.dp, start = 10.dp)
                            .background(Color.Black.copy(0.3f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 40.dp, end = 10.dp)
                    ) {
                        if (posts.isNotEmpty() && posts[0].source == SourceType.CHAN) {
                            IconButton(
                                onClick = { isGalleryMode = true },
                                modifier = Modifier
                                    .background(Color.Black.copy(0.3f), androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(Icons.Default.Apps, contentDescription = "Galería", tint = Color.White)
                            }
                        }
                        IconButton(
                            onClick = {
                                val urlToDownload = posts.getOrNull(pagerState.currentPage)?.let { post ->
                                    post.url
                                }
                                if (urlToDownload != null) onDownload(urlToDownload)
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(0.3f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Descargar", tint = Color.White)
                        }
                    }

                    OverlayInfo(
                        post = posts.getOrNull(pagerState.currentPage),
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomableImageContainer(
    url: String,
    source: SourceType,
    onZoomChange: (Boolean) -> Unit,
    onToggleUI: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(scale) { onZoomChange(scale > 1.01f) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    val imageRequest = remember(url) {
        val builder = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .addHeader("User-Agent", "Mozilla/5.0")

        when (source) {
            SourceType.EHENTAI -> {
                builder.addHeader("Referer", "https://e-hentai.org/")
                builder.addHeader("Cookie", "nw=1")
            }
            SourceType.VERCOMICS -> {
                builder.setHeader("User-Agent", VerComicsModule.USER_AGENT)
                builder.addHeader("Referer", "https://vercomicsporno.com/")
                builder.addHeader("Cookie", VerComicsModule.getCookiesAsString())
            }
            SourceType.REALBOORU -> builder.addHeader("Referer", "https://realbooru.com/")
            SourceType.CHAN -> builder.addHeader("Referer", "https://boards.4chan.org/")
            else -> {}
        }
        builder.build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGesturesAndTap(
                    shouldConsumePan = { scale > 1.01f },
                    onTap = onToggleUI,
                    onGesture = { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 50f)
                        scale = newScale
                        val extraWidth = (newScale - 1) * size.width
                        val extraHeight = (newScale - 1) * size.height
                        val maxX = extraWidth / 2
                        val maxY = extraHeight / 2
                        offset = Offset(
                            x = (offset.x + pan.x * newScale).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y * newScale).coerceIn(-maxY, maxY)
                        )
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onLongPress
            )
    ) {
        if (!loadError) {
            AsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = { loadError = true }
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                Text("Error de carga", color = Color.Gray, fontSize = 12.sp)
                Button(onClick = { loadError = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                    Text("Reintentar")
                }
            }
        }
    }
}

@Composable
fun BoxScope.OverlayInfo(post: UnifiedPost?, onBack: () -> Unit) {
    if (post == null) return

    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(0.9f))))
            .padding(16.dp)
            .padding(bottom = 30.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sourceColor = when(post.source) {
                    SourceType.EHENTAI -> Color(0xFFE91E63)
                    SourceType.REALBOORU -> Color(0xFFFF9800)
                    SourceType.E621 -> Color(0xFF003E6B)
                    SourceType.CHAN -> Color(0xFF1B5E20)
                    SourceType.REDDIT -> Color(0xFFFF5700)
                    else -> Color(0xFFAAE5A4)
                }
                ReaderBadge(text = post.source.name, color = sourceColor)
                Spacer(Modifier.width(8.dp))
                if (post.tags.isNotEmpty() && post.tags[0].startsWith("R:")) {
                    ReaderBadge(text = "Replies: " + post.tags[0].removePrefix("R:"), color = Color.Blue)
                }
                Spacer(modifier = Modifier.height(8.dp))

                var badgeText = "IMG"
                when (post.type) {
                    MediaType.VIDEO -> badgeText = "VIDEO"
                    MediaType.GIF -> badgeText = "GIF"
                    else -> {
                        val ext = post.url.substringAfterLast('.', "JPG").uppercase()
                        badgeText = if(ext.length in 3..4) ext else "IMG"
                    }
                }
                ReaderBadge(text = badgeText, color = Color.DarkGray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = post.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ReaderBadge(text: String, color: Color) {
    val textColor = if (color == Color(0xFFAAE5A4)) Color.Black else Color.White
    Text(text = text, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(color, MaterialTheme.shapes.small).padding(horizontal = 6.dp, vertical = 2.dp))
}

suspend fun PointerInputScope.detectTransformGesturesAndTap(
    shouldConsumePan: () -> Boolean,
    onTap: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        val touchSlop = viewConfiguration.touchSlop
        var pastTouchSlop = false
        val startTime = System.currentTimeMillis()

        awaitFirstDown(requireUnconsumed = false)

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }

            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()
                val isMultiTouch = event.changes.size > 1

                if (!pastTouchSlop) {
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoomChange) * centroidSize
                    val rotationMotion = abs(rotationChange * (Math.PI.toFloat() / 180f)) * centroidSize
                    val panMotion = panChange.getDistance()

                    if (zoomMotion > touchSlop || rotationMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (rotationChange != 0f || zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(centroid, panChange, zoomChange, rotationChange)
                    }

                    val isZoomingOrRotating = zoomChange != 1f || rotationChange != 0f

                    if (isMultiTouch || isZoomingOrRotating || shouldConsumePan()) {
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        if (!pastTouchSlop) {
            val endTime = System.currentTimeMillis()
            if (endTime - startTime < 300) {
                onTap()
            }
        }
    }
}