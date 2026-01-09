package com.xtrinityviewer.ui

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.xtrinityviewer.data.VerComicsModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareBypass(onBypassSuccess: () -> Unit, onCancel: () -> Unit) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    var successTriggered by remember { mutableStateOf(false) }

    fun checkSuccess(url: String?, title: String) {
        if (successTriggered) return

        if (url == null || !url.contains("vercomicsporno")) return

        val cookies = CookieManager.getInstance().getCookie(url) ?: ""
        val hasClearance = cookies.contains("cf_clearance")
        val isChallenge = title.contains("Just a moment", true) ||
                title.contains("One more step", true) ||
                title.contains("Attention Required", true) ||
                title.contains("Cloudflare", true)

        if (hasClearance && !isChallenge) {
            Log.d("CloudflareBypass", "¡Bypass Exitoso! Cookies: $cookies")
            VerComicsModule.saveCookiesFromWebView(url, cookies)
            successTriggered = true
            scope.launch {
                delay(1500)
                onBypassSuccess()
            }
        } else {
            Log.d("CloudflareBypass", "Esperando... Cookie: $hasClearance | Título Challenge: $isChallenge")
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this

                    settings.userAgentString = VerComicsModule.USER_AGENT
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val title = view?.title ?: ""
                            checkSuccess(url, title)
                        }
                    }

                    loadUrl("https://vercomicsporno.com/")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .zIndex(100f)
        ) {
            IconButton(
                onClick = { onCancel() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Red.copy(0.8f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(0.2f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancelar")
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .zIndex(100f)
        ) {
            FloatingActionButton(
                onClick = {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    webView?.reload()
                    successTriggered = false
                },
                containerColor = Color(0xFFC0CA33),
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Resetear")
            }
        }
    }
}