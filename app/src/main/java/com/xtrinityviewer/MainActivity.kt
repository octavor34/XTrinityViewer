package com.xtrinityviewer

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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

        // Configuración de Coil (Cargador de imágenes)
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
            val readerIndex by viewModel.readerIndex.collectAsState() // Necesario para FullScreenReader
            val galleryPages by viewModel.currentGalleryPages.collectAsState()
            val currentSource by viewModel.currentSource.collectAsState()
            val verComicsReady by viewModel.verComicsReady.collectAsState()
            val context = LocalContext.current

            // ESTADOS DE NAVEGACIÓN Y CONFIGURACIÓN
            var isWelcomeSeen by remember { mutableStateOf(SettingsStore.isWelcomeSeen(context)) }
            var showSetupOverlay by remember { mutableStateOf(false) }

            // ESTADO PARA DESCARGAS
            var pendingDownloadUrl by remember { mutableStateOf("") }
            val saveFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("image/jpeg")
            ) { uri ->
                if (uri != null && pendingDownloadUrl.isNotEmpty()) {
                    viewModel.downloadFile(context, pendingDownloadUrl, uri)
                }
            }

            // Recargar credenciales al iniciar si ya están guardadas
            LaunchedEffect(Unit) {
                viewModel.reloadCredentials(context)
            }
            val showBypassWebView by viewModel.showBypassWebView.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {

                // --- CAPA 1: FLUJO PRINCIPAL DE PANTALLAS ---

                if (!isWelcomeSeen) {
                    // 1. PANTALLA DE BIENVENIDA (Solo la primera vez)
                    WelcomeScreen(onStartClick = {
                        SettingsStore.setWelcomeSeen(context)
                        isWelcomeSeen = true
                    })
                }
                else if (!readerPosts.isNullOrEmpty()) {
                    // 2. LECTOR DE IMÁGENES / HILOS (FullScreen)
                    FullScreenReader(
                        posts = readerPosts!!,
                        initialIndex = readerIndex, // Aquí usamos la variable que ya declaramos arriba
                        onBack = { viewModel.closeReader() },
                        onLoadHd = { post -> viewModel.resolveHdUrl(post) },
                        onDownload = { url ->
                            pendingDownloadUrl = url // Usamos la variable declarada arriba
                            saveFileLauncher.launch("file_${System.currentTimeMillis()}.jpg") // Usamos el launcher declarado arriba
                        }
                    )
                    BackHandler { viewModel.closeReader() }
                }
                else if (galleryPages != null) {
                    // 3. LECTOR DE GALERÍAS (Scroll Vertical)
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
                    // 4. PANTALLA PRINCIPAL (Feed)
                    FeedScreen(
                        onRequestSetup = { showSetupOverlay = true }
                    )
                }

                // --- CAPA 2: OVERLAY DE CONFIGURACIÓN (SETUP) ---
                if (showSetupOverlay) {
                    Box(modifier = Modifier.fillMaxSize().zIndex(50f)) {
                        SetupScreen(
                            onFinished = {
                                showSetupOverlay = false
                                viewModel.reloadCredentials(context) // Recargar al guardar
                            },
                            onCancel = { showSetupOverlay = false }
                        )
                    }
                    BackHandler { showSetupOverlay = false }
                }

                // --- CAPA 3: BYPASS CLOUDFLARE ---
                if (currentSource == SourceType.VERCOMICS && !verComicsReady && !viewModel.isBypassCancelled) {
                    Box(modifier = Modifier.fillMaxSize().zIndex(99f)) {
                        CloudflareBypass(
                            onBypassSuccess = { viewModel.onVerComicsBypassSuccess() },
                            onCancel = { viewModel.cancelBypass() }
                        )
                    }
                }

                // --- CAPA 4: MONITOR DE RECURSOS ---
                Box(modifier = Modifier.zIndex(100f)) {
                    DebugMonitor()
                }
            }
        }
    }
}