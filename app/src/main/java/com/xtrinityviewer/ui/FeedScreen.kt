package com.xtrinityviewer.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import com.xtrinityviewer.R
import com.xtrinityviewer.R.drawable
import com.xtrinityviewer.data.MediaType
import com.xtrinityviewer.data.SourceType
import com.xtrinityviewer.data.UnifiedPost
import com.xtrinityviewer.viewmodel.FileFilter
import com.xtrinityviewer.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.xtrinityviewer.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(onRequestSetup: () -> Unit) {
    val viewModel: MainViewModel = viewModel()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* No hace falta acción, solo que el usuario acepte */ }
    )
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val keyboardController = LocalSoftwareKeyboardController.current
    val feed by viewModel.feed.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val errorMsg by viewModel.errorState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val addedTags by viewModel.tagsList.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val currentSource by viewModel.currentSource.collectAsState()

    val r34Color = Color(0xFF8BC34A)
    val ehentaiColor = Color(0xFFE91E63)
    val e621Color = Color(0xFF003E6B)
    val realbooruColor = Color(0xFFFF9800)
    val redditColor = Color(0xFFFF5700)
    val chanColor = Color(0xFF43A047)
    val verComicsColor = Color(0xFFC0CA33)
    val themeColor = when(currentSource) {
        SourceType.EHENTAI -> ehentaiColor
        SourceType.E621 -> e621Color
        SourceType.REALBOORU -> realbooruColor
        SourceType.CHAN -> chanColor
        SourceType.REDDIT -> redditColor
        SourceType.VERCOMICS -> verComicsColor
        else -> r34Color
    }


    LaunchedEffect(currentSource) {
        context.imageLoader.memoryCache?.clear()
    }

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Black.toArgb()
    }

    var searchText by remember { mutableStateOf("") }
    var uiVisible by remember { mutableStateOf(true) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    var selectedPostInfo by remember { mutableStateOf<UnifiedPost?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var pendingDownloadUrl by remember { mutableStateOf("") }

    val redditRecs = remember(currentSource) {
        listOf("nsfw", "gonewild", "realgirls", "hentai", "rule34", "ecchi", "paizuri", "thighdeology", "anal", "cumsluts", "collegesluts", "legalteens", "milf", "tinyclits", "godpussy", "holdthemoan", "gettingherselfoff").shuffled()
    }

    val chanRecs = remember {
        listOf("b", "h", "gif", "s", "d", "e", "u", "r", "aco", "trash")
    }
    var pendingPost by remember { mutableStateOf<UnifiedPost?>(null) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = com.xtrinityviewer.util.CreateSmartDocument()
    ) { uri ->
        // Verificamos que tengamos URI y que tengamos el Post guardado en memoria
        if (uri != null && pendingPost != null) {
            val postToDownload = pendingPost!! // Copia local segura
            Toast.makeText(context, "Iniciando descarga...", Toast.LENGTH_SHORT).show()

            scope.launch {
                // Pasamos el post completo para que el Downloader sepa qué cookies usar
                com.xtrinityviewer.util.Downloader.downloadToUri(context, postToDownload, uri)
            }
        } else {
            // Solo mostramos error si el URI es null (Usuario canceló)
            if (uri == null) {
                Toast.makeText(context, "Selección cancelada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error interno: Post perdido", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val endReached by viewModel.endReached.collectAsState()
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Si desliza hacia abajo (available.y < 0) -> Ocultar UI
                if (available.y < -5f && uiVisible) {
                    uiVisible = false
                }
                // Si desliza hacia arriba (available.y > 0) -> Mostrar UI
                else if (available.y > 5f && !uiVisible) {
                    uiVisible = true
                }
                return Offset.Zero
            }
        }
    }
    var showBlockDialog by remember { mutableStateOf(false) }
    var tagToBlock by remember { mutableStateOf("") }


    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF111111),
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Brush.verticalGradient(colors = listOf(Color.Black, Color(0xFF1A1A1A))))
                        .nestedScroll(nestedScrollConnection)
                ) {
                    IconButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            onRequestSetup()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd) // Parte superior derecha
                            .padding(8.dp)           // Un poco de margen
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración",
                            tint = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .offset(x = 180.dp, y = (-20).dp).background(themeColor.copy(alpha = 0.1f), CircleShape))
                    Column(modifier = Modifier.align(Alignment.BottomStart)
                        .padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        isDebugVisible.value = true
                                        Toast.makeText(context, "Debug Habilitado", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = themeColor.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .size(60.dp) // Lo hice un poquito más grande para que luzca la imagen
                                    .border(2.dp, themeColor, CircleShape)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_icon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = null
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text("XTRINITY", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF333333))
                Spacer(Modifier.height(16.dp))

                val items = listOf(
                    Triple("Rule34", R.drawable.ic_r34, SourceType.R34),
                    Triple("E-Hentai-Galleries", R.drawable.ic_ehentai, SourceType.EHENTAI),
                    Triple("VerComicsPorno", R.drawable.ic_vercomics, SourceType.VERCOMICS),
                    Triple("E621", R.drawable.ic_e621, SourceType.E621),
                    Triple("Realbooru", R.drawable.ic_realbooru, SourceType.REALBOORU),
                    Triple("4Chan", R.drawable.ic_4chan, SourceType.CHAN),
                    Triple("Reddit", R.drawable.ic_reddit, SourceType.REDDIT)
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f) // Ocupa el espacio sobrante, empujando el footer abajo pero sin ocultarlo
                        .fillMaxWidth()
                ) {
                    items(items) { (label, icon, source) ->
                        val selected = currentSource == source
                        val itemColor = if (source == SourceType.VERCOMICS) verComicsColor else themeColor

                        NavigationDrawerItem(
                            label = {
                                Text(
                                    label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                Image(
                                    painter = painterResource(id = icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            selected = selected,
                            onClick = {
                                var isLocked = false
                                if (source == SourceType.R34 && !SettingsStore.hasR34Credentials(context)) isLocked = true
                                if (source == SourceType.E621 && !SettingsStore.hasE621Credentials(context)) isLocked = true

                                if (isLocked) {
                                    scope.launch { drawerState.close() }
                                    onRequestSetup()
                                    Toast.makeText(context, "Se requiere API Key para $label", Toast.LENGTH_LONG).show()
                                } else {
                                    keyboardController?.hide()
                                    searchText = ""
                                    viewModel.onSearchTextChange("")
                                    viewModel.setSource(source)
                                    scope.launch { drawerState.close() }
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = itemColor.copy(0.15f),
                                selectedTextColor = itemColor,
                                unselectedTextColor = Color.LightGray,
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF333333))

                Column(modifier = Modifier.padding(16.dp)) {

                    Text(
                        "Apoya a las páginas originales ❤️",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Esta app es solo un visor. Por favor visita los sitios web oficiales y apoya a los artistas.",
                        color = Color.DarkGray,
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    // BOTÓN DONAR
                    Button(
                        onClick = {
                            // Pon aquí tu link real
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.buymeacoffee.com/octaviopdm7"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDD00)), // Color BuyMeACoffee
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Finánciame una paja", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            val prefs = context.getSharedPreferences("trinity_settings", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("welcome_seen_v1", false).apply()
                            val intent = (context as? android.app.Activity)?.intent
                            (context as? android.app.Activity)?.finish()
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ver Novedades", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(Color(0xFF151515), Color.Black), radius = 1500f, center = Offset.Zero))) {

            val pagerState = rememberPagerState(
                initialPage = viewModel.feedScrollIndex,
                pageCount = {
                    if (feed.isNotEmpty()) feed.size + if (endReached) 1 else 0 else 0
                }
            )

            LaunchedEffect(feed) { if (feed.isNotEmpty() && viewModel.feedScrollIndex == 0 && pagerState.currentPage != 0) pagerState.scrollToPage(0) }

            LaunchedEffect(pagerState.currentPage) {
                viewModel.saveFeedPosition(pagerState.currentPage)
                if (pagerState.currentPage >= feed.size - 8 && !endReached) viewModel.loadContent()
            }

            if (feed.isNotEmpty()) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondBoundsPageCount = 3,
                    key = { index ->
                        if (index < feed.size) feed[index].id
                        else "end_of_feed_marker" // ID único para la pantalla final
                    }
                ) { page ->
                    if (page in feed.indices) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1a1a1a))) {
                            PostItem(
                                post = feed[page],
                                isVisible = (pagerState.currentPage == page),
                                onDetailsClick = { selectedPostInfo = feed[page]; showTagSheet = true },
                                onContentClick = {
                                    when(feed[page].source) {
                                        SourceType.EHENTAI -> viewModel.openGallery(context, feed[page])
                                        SourceType.VERCOMICS -> viewModel.openGallery(context, feed[page])
                                        SourceType.REDDIT -> if (feed[page].type == MediaType.GALLERY) viewModel.openGallery(context, feed[page]) else viewModel.openFeedInReader(page)
                                        SourceType.REALBOORU -> viewModel.openSinglePost(feed[page])
                                        SourceType.CHAN -> viewModel.openChanThread(feed[page])
                                        else -> viewModel.openFeedInReader(page)
                                    }
                                },
                                onDownloadClick = { postObj ->
                                    pendingPost = postObj
                                    val correctName = com.xtrinityviewer.util.Downloader.calculateFileName(postObj)
                                    val correctMime = com.xtrinityviewer.util.Downloader.calculateMimeType(postObj)
                                    saveFileLauncher.launch(Pair(correctName, correctMime))
                                }
                            )
                        }
                    } else {
                        // --- PANTALLA FINAL "FIN DE RESULTADOS" ---
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.DoNotDisturbOn,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    "Eso es todo mi querido degenerado",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 30.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Busca algo más.",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }else if (errorMsg != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Error de Conexión",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg ?: "Algo salió mal",
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.loadContent() },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("REINTENTAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (!loading) {
                Column(modifier = Modifier.align(Alignment.Center).padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                    if (currentSource == SourceType.REDDIT) {
                        // BUG 1 CORREGIDO:
                        // Solo mostramos sugerencias si NO has buscado nada todavía.
                        // Si ya buscaste algo (addedTags tiene cosas) y estamos aquí, significa que no hubo resultados -> Mensaje de error.
                        if (addedTags.isEmpty()) {
                            Text("😈", fontSize = 80.sp)
                            Text("Sugerencias", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(vertical = 10.dp))
                            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(redditRecs) { rec ->
                                    Surface(color = Color(0xFF222222), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, redditColor.copy(0.3f)), modifier = Modifier.clickable { viewModel.addTag("r/$rec"); uiVisible = false }) {
                                        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(rec, color = Color.White) }
                                    }
                                }
                            }
                        } else {
                            // Si buscaste algo (ej: GIFs) y no hay nada:
                            Icon(Icons.Default.SearchOff, null, tint = Color.Gray, modifier = Modifier.size(60.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("No hay nada aquí", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Intenta otro filtro o busca otra cosa.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                    else if (currentSource == SourceType.CHAN) {
                        if (addedTags.isEmpty()) {
                            Image(
                                painter = painterResource(R.drawable.ic_4chan),
                                contentDescription = null,
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Busca un tablón", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(chanRecs) { board ->
                                    Surface(color = Color(0xFF222222), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, chanColor.copy(0.3f)), modifier = Modifier.clickable { viewModel.addTag(board); uiVisible = false }) {
                                        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("/$board/", color = Color.White, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        } else {
                            Icon(Icons.Default.SearchOff, null, tint = Color.Gray, modifier = Modifier.size(60.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("No hay nada aquí", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Intenta otro filtro o busca otra cosa.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                    else {
                        // CORRECCIÓN 1: Agrupar E-HENTAI con el resto para mostrar "Busca un tag"
                        if (addedTags.isEmpty() && currentSource != SourceType.VERCOMICS) {
                            Icon(Icons.Default.Search, null, tint = themeColor, modifier = Modifier.size(80.dp).background(themeColor.copy(0.1f), CircleShape).padding(16.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Busca un tag para empezar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        } else {
                            // VERCOMICS o FALLBACK
                            if (currentSource == SourceType.VERCOMICS && addedTags.isEmpty()) {
                                Text("Busca algo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            // --- SISTEMA DE MENSAJES JSON ---

                            // Obtenemos el mensaje una sola vez (remember) para que no cambie al hacer scroll
                            val randomMsg = remember(feed) {
                                com.xtrinityviewer.data.MessageManager.getRandomMessage(context)
                            }

                            // Renderizamos el Icono
                            if (randomMsg.iconRes != null) {
                                // Es un icono personalizado (PNG/XML en drawable)
                                Image(
                                    painter = painterResource(id = randomMsg.iconRes),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(randomMsg.color),
                                    modifier = Modifier.size(80.dp)
                                )
                            } else if (randomMsg.vectorIcon != null) {
                                // Es un icono nativo de Android
                                Icon(
                                    imageVector = randomMsg.vectorIcon,
                                    contentDescription = null,
                                    tint = randomMsg.color,
                                    modifier = Modifier.size(80.dp)
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            // Título Principal
                            Text(
                                text = randomMsg.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 20.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(Modifier.height(8.dp))

                            // Subtítulo
                            Text(
                                text = randomMsg.subtitle,
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            if (currentSource == SourceType.VERCOMICS) {
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { viewModel.reviveBypass() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Security, null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("REPARAR CLOUDFLARE", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !uiVisible,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 50.dp, end = 16.dp)) {
                IconButton(onClick = { uiVisible = true },
                    modifier = Modifier.background(Color.Black.copy(0.4f), CircleShape)
                        .border(1.dp, Color.White.copy(0.1f), CircleShape)
                        .size(48.dp)) {
                    Icon(Icons.Default.Search,
                        "Mostrar búsqueda",
                        tint = Color.White.copy(0.8f)) }
            }

            AnimatedVisibility(visible = uiVisible,
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(top = 50.dp, start = 16.dp)) {
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        searchText = ""
                        viewModel.onSearchTextChange("")
                        scope.launch { drawerState.open() }
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(0.6f), CircleShape)
                        .border(1.dp, Color.White.copy(0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.Menu, "Menú", tint = Color.White)
                }
            }

            AnimatedVisibility(visible = uiVisible,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)) {
                Column(modifier = Modifier
                    .fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp)
                        .background(Color.Transparent)
                        .padding(top = 45.dp, end = 12.dp, bottom = 0.dp)) {
                        Row(modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(Color(0xFF1E1E1E).copy(alpha = 0.95f), RoundedCornerShape(28.dp))
                                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(28.dp))
                                .clickable {
                                    focusRequester.requestFocus()
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        )
                        {
                            Icon(Icons.Default.Search,
                                null,
                                tint = themeColor,
                                modifier = Modifier
                                    .size(24.dp))
                            Spacer(modifier = Modifier
                                .width(12.dp))
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. TAGS CON MENÚ
                                items(addedTags) { tag ->
                                    Surface(
                                        color = themeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable {
                                            viewModel.removeTag(tag) // <--- ACCIÓN DIRECTA
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(tag, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.Close, null, tint = themeColor, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }

                                // 2. CAMPO DE TEXTO
                                item {
                                    val placeholder = if (currentSource == SourceType.CHAN) "Tablón..." else "Tags..."
                                    Box(modifier = Modifier.widthIn(min = 100.dp).height(40.dp), contentAlignment = Alignment.CenterStart) {
                                        if (searchText.isEmpty() && addedTags.isEmpty()) {
                                            Text(placeholder, color = Color.Gray, fontSize = 16.sp)
                                        }
                                        BasicTextField(
                                            value = searchText,
                                            onValueChange = { searchText = it; viewModel.onSearchTextChange(it) },
                                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                                            cursorBrush = SolidColor(themeColor),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = {
                                                if (searchText.isNotBlank()) {
                                                    viewModel.addTag(searchText.trim())
                                                    searchText = ""
                                                    focusManager.clearFocus()
                                                    uiVisible = false
                                                }
                                            }),
                                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                                        )
                                    }
                                }
                            }

                            if (currentSource != SourceType.VERCOMICS && currentSource != SourceType.EHENTAI) {
                                Box {
                                    IconButton(onClick = {
                                        searchText = ""
                                        viewModel.onSearchTextChange("")
                                        focusManager.clearFocus()
                                        showFilterMenu = true
                                    }) {
                                        Icon(
                                            Icons.Default.FilterList,
                                            "Filtro",
                                            tint = if (currentFilter != FileFilter.ALL) themeColor else Color.Gray
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showFilterMenu,
                                        onDismissRequest = { showFilterMenu = false },
                                        modifier = Modifier.background(Color(0xFF1a1a1a))
                                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                                    ) {
                                        FileFilter.values().forEach { filter ->
                                            val showItem = when(filter) {
                                                FileFilter.GAL -> currentSource == SourceType.REDDIT
                                                FileFilter.COUNT -> currentSource == SourceType.CHAN
                                                else -> true
                                            }

                                            if (showItem) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            filter.label,
                                                            color = if (filter == currentFilter) themeColor else Color.White,
                                                            fontWeight = if (filter == currentFilter) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        viewModel.setFilter(filter)
                                                        showFilterMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                }
                            }
                        }
                        if (
                            suggestions.isNotEmpty()
                            ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .padding(top=4.dp)
                                    .background(Color(0xFF222222),
                                        RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(0.1f),
                                        RoundedCornerShape(12.dp))
                                    .padding(bottom=10.dp)) {
                                items(suggestions)
                                { item -> Text(
                                    item.label,
                                    color = Color.LightGray,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addTag(
                                                item.value
                                            );
                                            searchText = "";
                                            focusManager.clearFocus()
                                            uiVisible = false
                                        }
                                        .padding(16.dp))
                                }
                            }
                        }
                    }
                    IconButton(onClick = { uiVisible = false }, modifier = Modifier.offset(y = (-5).dp).background(Color.Black.copy(0.7f), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).size(60.dp, 25.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Ocultar", tint = Color.White) }
                }
            }
            if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = themeColor)
        }

        if (showTagSheet && selectedPostInfo != null) {
            val post = selectedPostInfo!!
            val showInteractiveTagsList = (
                    post.source == SourceType.R34 ||
                    post.source == SourceType.E621 ||
                    post.source == SourceType.REALBOORU ||
                    post.source == SourceType.EHENTAI
                    )
            ModalBottomSheet(onDismissRequest = { showTagSheet = false }, sheetState = sheetState, containerColor = Color(0xFF121212), contentColor = Color.White) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!showInteractiveTagsList) {
                        Text(post.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    } else {
                        // Si ES booru, mostramos cabecera "Detalles"
                        Text("Tags:", color = Color.Gray, fontSize = 14.sp)
                    }
                    // 2. LISTA DE TAGS (Solo visible si es R34, E621 o Realbooru)
                    if (showInteractiveTagsList) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Etiquetas:", color = Color.Gray, fontSize = 12.sp)

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(post.tags) { tag ->
                                var isMenuExpanded by remember { mutableStateOf(false) }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    // EL BOTÓN DEL TAG CON MENÚ DE 3 PUNTOS
                                    Button(
                                        onClick = { isMenuExpanded = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF222222),
                                            contentColor = themeColor
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = tag,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Opciones",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // EL MENÚ DESPLEGABLE
                                    DropdownMenu(
                                        expanded = isMenuExpanded,
                                        onDismissRequest = { isMenuExpanded = false },
                                        modifier = Modifier
                                            .background(Color(0xFF1a1a1a))
                                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("Añadir a búsqueda", color = Color.White, fontWeight = FontWeight.Bold)
                                                    Text("Se suma a tus tags actuales", color = Color.Gray, fontSize = 10.sp)
                                                }
                                            },
                                            leadingIcon = { Icon(Icons.Default.Add, null, tint = themeColor) },
                                            onClick = {
                                                viewModel.addTag(tag)
                                                showTagSheet = false
                                            }
                                        )

                                        HorizontalDivider(color = Color.White.copy(0.1f))

                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("Buscar SOLO esto", color = Color(0xFFFFD600), fontWeight = FontWeight.Bold)
                                                    Text("Borra los filtros actuales", color = Color.Gray, fontSize = 10.sp)
                                                }
                                            },
                                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFFFD600)) },
                                            onClick = {
                                                viewModel.setExclusiveTag(tag)
                                                showTagSheet = false
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("BLOQUEAR TAG", color = Color.Red, fontWeight = FontWeight.Bold) },
                                            leadingIcon = { Icon(Icons.Default.Block, null, tint = Color.Red) },
                                            onClick = {
                                                isMenuExpanded = false
                                                tagToBlock = tag
                                                showBlockDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Bloquear '$tagToBlock'", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Quieres desterrar este tag de todas las páginas (Global) o solo de ${viewModel.currentSource.collectAsState().value.name}?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.blockTag(tagToBlock, global = true)
                        showBlockDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("GLOBAL", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.blockTag(tagToBlock, global = false)
                        showBlockDialog = false
                    },
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("SOLO AQUÍ", color = Color.White)
                }
            }
        )
    }
}