package com.xtrinityviewer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtrinityviewer.R

@Composable
fun WelcomeScreen(onStartClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111111), Color.Black)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Logo
            Surface(
                shape = CircleShape,
                color = Color(0xFFC0CA33).copy(alpha = 0.1f),
                modifier = Modifier
                    .size(120.dp)
                    .border(2.dp, Color(0xFFC0CA33), CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "XTrinity Viewer",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                "Tu centro de cultura unificado",
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Sección Novedades
            Text(
                "NOVEDADES V2.0",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC0CA33),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            FeatureItem(
                icon = Icons.Default.Security,
                title = "Bypass Cloudflare Mejorado",
                desc = "Acceso restablecido a VerComics y E-Hentai con nuevo sistema de cookies."
            )
            FeatureItem(
                icon = Icons.Default.Star,
                title = "Acceso Libre",
                desc = "Usa Reddit, 4Chan y Galerías sin necesidad de configurar APIs al inicio."
            )
            FeatureItem(
                icon = Icons.Default.CheckCircle,
                title = "Limpieza de Código",
                desc = "Menos bugs, más velocidad y scroll infinito optimizado."
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0CA33)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "ENTRAR AHORA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.ArrowForward, null, tint = Color.Black)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Configura tus cuentas de R34/E621 solo cuando las necesites.",
                color = Color.DarkGray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FeatureItem(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}