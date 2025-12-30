package com.xtrinityviewer.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
    // Evitamos disparar el éxito múltiples veces
    var successTriggered by remember { mutableStateOf(false) }

    // --- LÓGICA INTELIGENTE DE DETECCIÓN (Tu código mejorado) ---
    fun checkSuccess(url: String?, title: String) {
        if (successTriggered) return
        if (url == null || !url.contains("vercomicsporno")) return

        val cookies = CookieManager.getInstance().getCookie(url) ?: ""

        // 1. ¿Tenemos la cookie mágica?
        val hasClearance = cookies.contains("cf_clearance")

        // 2. ¿Seguimos en la sala de espera? (Títulos típicos de Cloudflare)
        val isChallenge = title.contains("Just a moment", true) ||
                title.contains("One more step", true) ||
                title.contains("Attention Required", true) ||
                title.contains("Cloudflare", true)

        // CONDICIÓN DE ÉXITO:
        // Tenemos cookie Y el título NO parece ser el del captcha.
        if (hasClearance && !isChallenge) {
            Log.d("CloudflareBypass", "¡Bypass Exitoso! Cookies: $cookies")

            // Guardamos las cookies en el módulo inmediatamente
            VerComicsModule.saveCookiesFromWebView(url, cookies)

            successTriggered = true

            // Damos un pequeño respiro de 1.5s para que el usuario vea que entró a la home
            // y luego cerramos la ventana automáticamente.
            scope.launch {
                delay(1500)
                onBypassSuccess()
            }
        } else {
            Log.d("CloudflareBypass", "Esperando... Cookie: $hasClearance | Título Challenge: $isChallenge")
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // CAPA 1: WEBVIEW (FONDO)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this

                    // IMPORTANTE: UserAgent idéntico al del módulo de datos
                    settings.userAgentString = VerComicsModule.USER_AGENT
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    // Cookies de terceros habilitadas para CF
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val title = view?.title ?: ""
                            // Cada vez que carga algo, chequeamos si ya pasamos el reto
                            checkSuccess(url, title)
                        }
                    }

                    loadUrl("https://vercomicsporno.com/")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // CAPA 2: BOTONES FLOTANTES (CON Z-INDEX PARA QUE FUNCIONEN)

        // Botón CERRAR (Cancelar manualmente si te cansas de esperar)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .zIndex(100f) // <--- ESTO ASEGURA QUE EL TOQUE LO RECIBA EL BOTÓN
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

        // Botón REFRESCAR (Si se traba el captcha)
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
                    successTriggered = false // Reseteamos lógica por si acaso
                },
                containerColor = Color(0xFFC0CA33),
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Resetear")
            }
        }
    }
}