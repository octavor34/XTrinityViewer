package com.xtrinityviewer.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.imageLoader
import com.xtrinityviewer.data.BlacklistManager
import com.xtrinityviewer.data.SettingsStore
import com.xtrinityviewer.data.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onFinished: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentCreds = remember { SettingsStore.getCredentials(context) }
    var isBackupEnabled by remember { mutableStateOf(SettingsStore.isBackupEnabled(context)) }
    var r34User by remember { mutableStateOf(currentCreds["r34_user"] ?: "") }
    var r34Key by remember { mutableStateOf(currentCreds["r34_key"] ?: "") }
    var e621User by remember { mutableStateOf(currentCreds["e621_user"] ?: "") }
    var e621Key by remember { mutableStateOf(currentCreds["e621_key"] ?: "") }
    var globalBlacklist by remember { mutableStateOf(BlacklistManager.getListString(null)) }
    var selectedSourceForBlacklist by remember { mutableStateOf(SourceType.R34) }
    var specificBlacklist by remember(selectedSourceForBlacklist) {
        mutableStateOf(BlacklistManager.getListString(selectedSourceForBlacklist))
    }
    var isSourceDropdownExpanded by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showErrorHelpDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Configuración",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text("PRIVACIDAD", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isBackupEnabled) Icons.Default.Cloud else Icons.Default.CloudOff,
                                null,
                                tint = if (isBackupEnabled) Color(0xFF4285F4) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Copia en la Nube", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isBackupEnabled) "Tus llaves se guardan en Google (Encriptadas)."
                            else "Datos solo locales. Se borran al desinstalar.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = isBackupEnabled,
                        onCheckedChange = {
                            isBackupEnabled = it
                            SettingsStore.setBackupEnabled(context, it)
                            val msg = if(it) "Backup Activado" else "Backup Desactivado"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4285F4),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("LLAVES DE API", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            Text("Rule34", color = Color(0xFF8BC34A), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))

            Text("Configuración de Cuentas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))

            ApiConfigCard(context = context) {
            }
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Block, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("GESTIÓN DE BLACKLIST", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = globalBlacklist,
                onValueChange = {
                    globalBlacklist = it
                    BlacklistManager.saveListString(context, it, null)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.Red, unfocusedBorderColor = Color.Gray,
                    containerColor = Color(0xFF111111), focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray
                ),
                minLines = 2, maxLines = 4
            )

            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { isSourceDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                ) {
                    Text(selectedSourceForBlacklist.name)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }

                DropdownMenu(
                    expanded = isSourceDropdownExpanded,
                    onDismissRequest = { isSourceDropdownExpanded = false },
                    modifier = Modifier.background(Color(0xFF222222))
                ) {
                    SourceType.values().forEach { source ->
                        DropdownMenuItem(
                            text = { Text(source.name, color = Color.White) },
                            onClick = {
                                selectedSourceForBlacklist = source
                                specificBlacklist = BlacklistManager.getListString(source)
                                isSourceDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = specificBlacklist,
                onValueChange = {
                    specificBlacklist = it
                    BlacklistManager.saveListString(context, it, selectedSourceForBlacklist)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tags prohibidos solo en ${selectedSourceForBlacklist.name}") },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.Red, unfocusedBorderColor = Color.Gray,
                    containerColor = Color(0xFF111111), focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray
                ),
                minLines = 2, maxLines = 4
            )


            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("MANTENIMIENTO", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Limpiar Caché de Imágenes", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Elimina las imágenes guardadas para liberar espacio.",
                            color = Color.Gray, fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                context.imageLoader.diskCache?.clear()
                                context.imageLoader.memoryCache?.clear()

                                try {
                                    val videoCacheDir = java.io.File(context.cacheDir, "trinity_video_cache")
                                    if (videoCacheDir.exists()) {
                                        videoCacheDir.deleteRecursively()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Imágenes y Videos eliminados", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("BORRAR TODO", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    SettingsStore.saveCredentials(context, r34User, r34Key, e621User, e621Key)
                    BlacklistManager.saveListString(context, globalBlacklist, null)
                    BlacklistManager.saveListString(context, specificBlacklist, selectedSourceForBlacklist)
                    onFinished()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("GUARDAR Y SALIR", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.clickable { showHelpDialog = true }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.HelpOutline, null, tint = Color.Gray)
                Spacer(Modifier.width(8.dp))
                Text("¿Cómo obtengo mis keys?", color = Color.Gray)
            }

            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier
                    .clickable { showErrorHelpDialog = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BugReport, null, tint = Color.Red.copy(alpha = 0.6f))
                Spacer(Modifier.width(8.dp))
                Text("¿Qué hago si la app falla?", color = Color.Gray)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))) {
                Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    Text("Ayuda Rápida", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("Para Rule34:", color = Color(0xFF8BC34A), fontWeight = FontWeight.Bold)
                    Text("1. Ve a 'My Account' > 'Options' > 'API Access Credentials'.", color = Color.LightGray, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Para E621:", color = Color(0xFF003E6B), fontWeight = FontWeight.Bold)
                    Text("1. Ve a 'Settings' > 'API Key'.", color = Color.LightGray, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showHelpDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Entendido")
                    }
                }
            }
        }
    }
    if (showErrorHelpDialog) {
        Dialog(onDismissRequest = { showErrorHelpDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, null, tint = Color.Red)
                        Spacer(Modifier.width(12.dp))
                        Text("Reporte de Errores", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Si ves una pantalla roja con código extraño:",
                        color = Color.LightGray, fontSize = 14.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("1. Toca el botón 'COPIAR' en esa pantalla.", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("2. Mándame el texto copiado.", color = Color.Gray, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("3. Toca 'REINICIAR' para volver a usar la app.", color = Color.Gray, fontSize = 13.sp)

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { showErrorHelpDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text("ENTENDIDO", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    color: Color
) {
    var isVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = color, unfocusedBorderColor = Color.Gray,
            focusedLabelColor = color, cursorColor = color,
            containerColor = Color(0xFF111111), focusedTextColor = Color.White, unfocusedTextColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val image = if (isVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
            val description = if (isVisible) "Ocultar" else "Mostrar"

            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(imageVector = image, contentDescription = description, tint = Color.Gray)
            }
        }
    )
}
@Composable
fun ApiConfigCard(
    context: android.content.Context,
    onCredentialsSaved: () -> Unit
) {
    // 1. Lista de sitios disponibles
    val apiSites = remember {
        listOf(
            SourceType.R34,
            SourceType.E621,
            SourceType.GELBOORU,
        )
    }

    // 2. Estados
    var expanded by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf(SourceType.R34) }
    var userId by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    val prefs = remember { context.getSharedPreferences("trinity_settings", android.content.Context.MODE_PRIVATE) }

    // 3. FUNCIÓN PARA OBTENER LAS CLAVES CORRECTAS (Mantiene compatibilidad)
    fun getPrefKeys(source: SourceType): Pair<String, String> {
        return when (source) {
            SourceType.R34 -> "r34_user" to "r34_key"      // Mantiene tus claves antiguas
            SourceType.E621 -> "e621_user" to "e621_key"   // Mantiene tus claves antiguas
            else -> "${source.name}_user" to "${source.name}_key" // Genera nuevas: GELBOORU_user, etc.
        }
    }

    // 4. Cargar datos al cambiar de sitio
    LaunchedEffect(selectedSource) {
        val (userKey, passKey) = getPrefKeys(selectedSource)

        userId = SettingsStore.getSecureCredential(context, userKey)
        apiKey = SettingsStore.getSecureCredential(context, passKey)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Gestor de APIs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Configura tus cuentas para acceder a contenido completo.", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // Selector de Sitio
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text(selectedSource.name)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF252525))
                ) {
                    apiSites.forEach { site ->
                        DropdownMenuItem(
                            text = { Text(site.name, color = Color.White) },
                            onClick = { selectedSource = site; expanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Campos de Texto
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("Usuario / ID") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFFFFDD00),
                    unfocusedLabelColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            SecretField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = "API Key / Hash",
                color = Color(0xFFFFDD00)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val (userKey, passKey) = getPrefKeys(selectedSource)

                    SettingsStore.saveSecureCredential(context, userKey, userId.trim())
                    SettingsStore.saveSecureCredential(context, passKey, apiKey.trim())

                    Toast.makeText(context, "Guardado para ${selectedSource.name}", Toast.LENGTH_SHORT).show()
                    onCredentialsSaved()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00AAFF))
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("GUARDAR DATOS", fontWeight = FontWeight.Bold)
            }
        }
    }
}