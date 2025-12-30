package com.xtrinityviewer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.xtrinityviewer.data.GalleryPageDto
import com.xtrinityviewer.data.SourceType
import com.xtrinityviewer.data.VerComicsModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun GalleryReader(
    pages: List<GalleryPageDto>,
    title: String,
    initialIndex: Int,
    initialOffset: Int,
    source: SourceType,
    onBack: () -> Unit,
    onPageClick: (String) -> Unit,
    onSaveScroll: (Int, Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyGridState(initialIndex, initialOffset)

    DisposableEffect(Unit) {
        onDispose { onSaveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= (totalItems - 10)
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // BUG 6 CORREGIDO: Aumentado padding top a 60.dp
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(top = 60.dp, bottom = 10.dp, start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Atrás", tint = Color.White) }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(pages, key = { it.index }) { page ->
                if (page.thumbWidth != null && page.thumbHeight != null && page.thumbX != null && page.thumbY != null) {
                    SpriteImage(page = page, onClick = { onPageClick(page.viewerUrl) })
                } else {
                    StandardImage(page = page, source = source, onClick = { onPageClick(page.viewerUrl) })
                }
            }
        }
    }
}

@Composable
fun SpriteImage(page: GalleryPageDto, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    val requestHeaders = remember {
        mapOf(
            "Cookie" to "nw=1",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer" to "https://e-hentai.org/"
        )
    }

    DisposableEffect(page.thumbUrl) {
        val job = scope.launch(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(page.thumbUrl)
                .memoryCacheKey(page.thumbUrl)
                .diskCacheKey(page.thumbUrl)
                .networkCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .size(coil.size.Size.ORIGINAL)
                .apply { requestHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .allowHardware(false)
                .build()

            val result = ImageLoader(context).execute(request)
            if (result.drawable != null) {
                bitmap = result.drawable?.toBitmap()
            }
        }
        onDispose { job.cancel() }
    }

    val ratio = if (page.thumbHeight != null && page.thumbHeight > 0)
        page.thumbWidth!!.toFloat() / page.thumbHeight.toFloat()
    else 0.7f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(Color(0xFF222222))
            .clickable(onClick = onClick)
    ) {
        if (bitmap != null) {
            val bmp = bitmap!!.asImageBitmap()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val tX = page.thumbX ?: 0
                val tY = page.thumbY ?: 0
                val tW = page.thumbWidth ?: 100
                val tH = page.thumbHeight ?: 100

                drawImage(
                    image = bmp,
                    srcOffset = IntOffset(tX, tY),
                    srcSize = IntSize(tW, tH),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low
                )
            }
        }
    }
}

@Composable
fun StandardImage(page: GalleryPageDto, source: SourceType, onClick: () -> Unit) {
    val context = LocalContext.current

    val requestBuilder = remember(source, page.thumbUrl) {
        val builder = ImageRequest.Builder(context)
            .data(page.thumbUrl)
            .crossfade(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)

        if (source == SourceType.EHENTAI) {
            builder.addHeader("Cookie", "nw=1")
            builder.addHeader("Referer", "https://e-hentai.org/")
        }
        else if (source == SourceType.VERCOMICS) {
            builder.addHeader("Cookie", VerComicsModule.getCookiesAsString())
            builder.addHeader("User-Agent", VerComicsModule.USER_AGENT)
            builder.addHeader("Referer", "https://vercomicsporno.com/")
        }
        builder.build()
    }

    AsyncImage(
        model = requestBuilder,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .aspectRatio(0.7f)
            .background(Color(0xFF222222))
            .clickable(onClick = onClick)
    )
}