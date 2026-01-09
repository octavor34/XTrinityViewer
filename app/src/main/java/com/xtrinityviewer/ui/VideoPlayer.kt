package com.xtrinityviewer.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xtrinityviewer.util.Optimizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.xtrinityviewer.util.VideoCacheManager

private enum class SeekAction { NONE, FORWARD, REWIND }

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    isVisible: Boolean,
    showControls: Boolean = false,
    autoPlay: Boolean = false,
    headers: Map<String, String> = emptyMap(),
    onPlayingChange: (Boolean) -> Unit = {},
    onLongPress: () -> Unit = {},
    onControllerVisibilityChanged: (Boolean) -> Unit = {}
) {

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val optimizedUrl = remember(url) { Optimizer.optimize(url) }
    var isLoading by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isPlayingState by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUiVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isUiVisible) {
        onControllerVisibilityChanged(isUiVisible)
    }
    var seekActionState by remember { mutableStateOf(SeekAction.NONE) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var gestureProgress by remember { mutableFloatStateOf(0f) }
    var isGestureVisible by remember { mutableStateOf(false) }
    var startBrightness by remember { mutableFloatStateOf(0f) }
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    fun toggleFullscreen(enable: Boolean) {
        val act = activity ?: return
        isFullscreen = enable
        if (enable) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val exoPlayer = remember {
        val cacheDataSourceFactory = VideoCacheManager.getDataSourceFactory(context)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val mediaItem = androidx.media3.common.MediaItem.fromUri(optimizedUrl)
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                playWhenReady = true
                prepare()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            isLoading = false
                            errorMessage = null
                            totalTime = duration.coerceAtLeast(0L)
                        }
                        if (state == Player.STATE_BUFFERING) isLoading = true
                        if (state == Player.STATE_ENDED) {
                            isPlayingState = false
                            isUiVisible = true
                        }
                        isPlayingState = isPlaying
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        onPlayingChange(isPlaying)
                        isPlayingState = isPlaying
                        if (isPlaying) isUiVisible = false else isUiVisible = true
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        isLoading = false
                        val cause = error.cause?.message ?: error.message
                        errorMessage = "Error: $cause"
                        isUiVisible = true
                    }
                })
            }
    }
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    fun seekRelative(seconds: Int) {
        val current = exoPlayer.currentPosition
        val newPos = (current + seconds * 1000).coerceIn(0L, exoPlayer.duration)
        exoPlayer.seekTo(newPos)

        scope.launch {
            seekActionState = if (seconds > 0) SeekAction.FORWARD else SeekAction.REWIND
            delay(600)
            seekActionState = SeekAction.NONE
        }
    }

    fun formatTime(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying && !isSeeking) {
                currentTime = exoPlayer.currentPosition
                totalTime = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(1000)
        }
    }

    BackHandler(enabled = isFullscreen) { toggleFullscreen(false) }

    LaunchedEffect(isVisible) {
        if (isVisible && autoPlay) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    controllerAutoShow = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
                    player = exoPlayer

                }
            },
            update = { view ->
                if (view.player != exoPlayer) view.player = exoPlayer
                view.useController = false
                view.controllerAutoShow = false
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            isUiVisible = !isUiVisible
                        },
                        onDoubleTap = { offset ->
                            val width = size.width
                            if (offset.x < width / 2) {
                                seekRelative(-5)
                            } else {
                                seekRelative(5)
                            }
                        }
                    )
                }
        )

        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.2f)
                    .align(Alignment.CenterStart)
                    .pointerInput(Unit) { detectTapGestures(onTap = { isUiVisible = !isUiVisible }) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                startBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                if (startBrightness < 0) startBrightness = 0.5f
                                gestureProgress = startBrightness
                                accumulatedDrag = 0f
                                isGestureVisible = true
                            },
                            onDragEnd = { scope.launch { delay(500); isGestureVisible = false } },
                            onDragCancel = { isGestureVisible = false },
                            onVerticalDrag = { _, dragAmount ->
                                accumulatedDrag -= dragAmount
                                val sensitivity = 1000f
                                val change = accumulatedDrag / sensitivity
                                val newBright = (startBrightness + change).coerceIn(0.01f, 1f)
                                val lp = activity?.window?.attributes
                                if (lp != null) {
                                    lp.screenBrightness = newBright
                                    activity.window.attributes = lp
                                    gestureProgress = newBright
                                }
                            }
                        )
                    }
            )
        }

        if (isLoading && isVisible) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF43A047))

        AnimatedVisibility(
            visible = isUiVisible && !isPlayingState && !isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(modifier = Modifier.size(90.dp).background(Color.Black.copy(0.4f), CircleShape), contentAlignment = Alignment.Center) {
                IconButton(onClick = { togglePlayPause() }) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(60.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = seekActionState != SeekAction.NONE,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (seekActionState == SeekAction.FORWARD) Icons.Default.Forward5 else Icons.Default.Replay5, // Iconos de 5s
                        null, tint = Color.White, modifier = Modifier.size(40.dp)
                    )
                    Text(
                        if (seekActionState == SeekAction.FORWARD) "+5s" else "-5s",
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(20.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(8.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(40.dp))
                Text(text = errorMessage ?: "Error", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                Button(
                    onClick = { errorMessage = null; isLoading = true; exoPlayer.prepare(); exoPlayer.play() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("REINTENTAR", color = Color.Black) }
            }
        }

        if (isGestureVisible && isFullscreen) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp).size(100.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Brightness7, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Text("${(gestureProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = isUiVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = if (isFullscreen) 24.dp else 40.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { togglePlayPause() }) {
                        Icon(
                            if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White
                        )
                    }

                    Text(formatTime(currentTime), color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)

                    Slider(
                        value = currentTime.toFloat(),
                        onValueChange = { isSeeking = true; currentTime = it.toLong() },
                        onValueChangeFinished = { exoPlayer.seekTo(currentTime); isSeeking = false },
                        valueRange = 0f..totalTime.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFC0CA33), activeTrackColor = Color(0xFFC0CA33), inactiveTrackColor = Color.Gray),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

                    Text(formatTime(totalTime), color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)

                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = { toggleFullscreen(!isFullscreen) }) {
                        Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, null, tint = Color.White)
                    }
                }
            }
        }
    }
}