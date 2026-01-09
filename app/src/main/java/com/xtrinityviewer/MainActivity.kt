package com.xtrinityviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.xtrinityviewer.data.SettingsStore
import com.xtrinityviewer.data.SourceType
import com.xtrinityviewer.ui.CloudflareBypass
import com.xtrinityviewer.ui.DebugMonitor
import com.xtrinityviewer.ui.FeedScreen
import com.xtrinityviewer.ui.FullScreenReader
import com.xtrinityviewer.ui.GalleryReader
import com.xtrinityviewer.ui.SetupScreen
import com.xtrinityviewer.ui.WelcomeScreen
import com.xtrinityviewer.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler(
            com.xtrinityviewer.util.GlobalExceptionHandler(this)
        )
        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        val imageLoader = ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizePercent(0.02).build() }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val readerPosts by viewModel.readerPosts.collectAsState()
            val readerIndex by viewModel.readerIndex.collectAsState()
            val startInGallery by viewModel.startInGalleryMode.collectAsState()
            val galleryPages by viewModel.currentGalleryPages.collectAsState()
            val currentSource by viewModel.currentSource.collectAsState()
            val verComicsReady by viewModel.verComicsReady.collectAsState()
            val context = LocalContext.current

            var isWelcomeSeen by remember { mutableStateOf(SettingsStore.isWelcomeSeen(context)) }
            var showSetupOverlay by remember { mutableStateOf(false) }

            var pendingDownloadUrl by remember { mutableStateOf("") }
            val saveFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("image/jpeg")
            ) { uri ->
                if (uri != null && pendingDownloadUrl.isNotEmpty()) {
                    viewModel.downloadFile(context, pendingDownloadUrl, uri)
                }
            }

            LaunchedEffect(Unit) {
                viewModel.reloadCredentials(context)
            }
            val showBypassWebView by viewModel.showBypassWebView.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isWelcomeSeen) {
                    WelcomeScreen(onStartClick = {
                        SettingsStore.setWelcomeSeen(context)
                        isWelcomeSeen = true
                    })
                }
                else if (!readerPosts.isNullOrEmpty()) {
                    FullScreenReader(
                        posts = readerPosts!!,
                        initialIndex = readerIndex,
                        initialGalleryMode = startInGallery,
                        onBack = { finalIndex -> viewModel.closeReader(finalIndex) },
                        onLoadHd = { post -> viewModel.resolveHdUrl(post) },
                        onDownload = { url ->
                            pendingDownloadUrl = url
                            saveFileLauncher.launch("file_${System.currentTimeMillis()}.jpg")
                        },
                        onLoadMore = { viewModel.loadMoreReaderContent() },
                        onOpenGallery = { post -> viewModel.openGallery(context, post) }
                    )
                    BackHandler { viewModel.closeReader() }
                }
                else if (galleryPages != null) {
                    GalleryReader(
                        pages = galleryPages!!,
                        title = viewModel.currentGalleryTitle,
                        initialIndex = viewModel.galleryScrollIndex,
                        initialOffset = viewModel.galleryScrollOffset,
                        source = viewModel.currentGallerySource,
                        onBack = { viewModel.closeGallery() },
                        onPageClick = { clickedUrl -> viewModel.openGalleryAsReader(galleryPages!!, clickedUrl) },
                        onSaveScroll = { index, offset -> viewModel.saveGalleryPosition(index, offset) },
                        onLoadMore = { viewModel.loadMoreGalleryPages() }
                    )
                    BackHandler { viewModel.closeGallery() }
                }
                else {
                    FeedScreen(
                        onRequestSetup = { showSetupOverlay = true }
                    )
                }

                if (showSetupOverlay) {
                    Box(modifier = Modifier.fillMaxSize().zIndex(50f)) {
                        SetupScreen(
                            onFinished = {
                                showSetupOverlay = false
                                viewModel.reloadCredentials(context)
                            },
                            onCancel = { showSetupOverlay = false }
                        )
                    }
                    BackHandler { showSetupOverlay = false }
                }

                if (currentSource == SourceType.VERCOMICS && !verComicsReady && !viewModel.isBypassCancelled) {
                    Box(modifier = Modifier.fillMaxSize().zIndex(99f)) {
                        CloudflareBypass(
                            onBypassSuccess = { viewModel.onVerComicsBypassSuccess() },
                            onCancel = { viewModel.cancelBypass() }
                        )
                    }
                }

                Box(modifier = Modifier.zIndex(100f)) {
                    DebugMonitor()
                }
            }
        }
    }
}