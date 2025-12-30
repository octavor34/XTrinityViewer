package com.xtrinityviewer.ui

import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.text.DecimalFormat

// Variable GLOBAL para controlar la visibilidad desde cualquier parte
var isDebugVisible = mutableStateOf(false)

@Composable
fun DebugMonitor() {
    // Si está invisible, no dibujamos nada (ni espacio vacío)
    if (!isDebugVisible.value) return

    var ramUsage by remember { mutableStateOf("0 MB") }
    var netUsage by remember { mutableStateOf("0 MB") }
    var diskUsage by remember { mutableStateOf("0 MB") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val df = DecimalFormat("#.##")
        val startRx = TrafficStats.getUidRxBytes(Process.myUid())
        while (true) {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            val totalPss = memInfo.totalPss / 1024.0
            ramUsage = "${totalPss.toInt()} MB"

            val currentRx = TrafficStats.getUidRxBytes(Process.myUid())
            val delta = (currentRx - startRx) / (1024.0 * 1024.0)
            netUsage = "${df.format(delta)} MB"

            val cacheDir = context.cacheDir.resolve("image_cache")
            val sizeMb = getFolderSize(cacheDir) / (1024.0 * 1024.0)
            diskUsage = "${df.format(sizeMb)} MB"

            delay(1000)
        }
    }

    // POSICIÓN EXACTA: Arriba del logo
    Box(
        modifier = Modifier
            .padding(top = 20.dp, start = 20.dp) // Ajustado para estar sobre el logo
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { isDebugVisible.value = false } // Tocar para OCULTAR TOTALMENTE
            .padding(8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DEBUG", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.height(4.dp))
            DebugText("RAM: $ramUsage")
            DebugText("NET: $netUsage")
            DebugText("DISK: $diskUsage")
        }
    }
}

@Composable
fun DebugText(text: String) {
    Text(
        text = text,
        color = Color(0xFF00FF00),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
    )
}

fun getFolderSize(file: File): Long {
    if (!file.exists()) return 0
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { getFolderSize(it) } ?: 0
}