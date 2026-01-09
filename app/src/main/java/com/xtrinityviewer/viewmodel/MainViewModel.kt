package com.xtrinityviewer.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xtrinityviewer.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

enum class FileFilter(val label: String, val tagToInject: String) {
    ALL("TODO", ""),
    IMG("IMAGEN", "-video"),
    GIF("GIF", ""),
    VID("VIDEO", "video"),
    GAL("GALERIA", ""),
    COUNT("POPULARES", "")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var cachedChanBoards: List<AutocompleteDto> = emptyList()
    private var cachedVerComicsTags: List<AutocompleteDto> = emptyList()
    private val _currentSource = MutableStateFlow(SourceType.R34)
    val currentSource = _currentSource.asStateFlow()
    private val _currentGalleryPages = MutableStateFlow<List<GalleryPageDto>?>(null)
    val currentGalleryPages = _currentGalleryPages.asStateFlow()
    var currentGalleryTitle = ""
    var currentGallerySource = SourceType.R34
    var currentGalleryUrl = ""
    private var currentGalleryWebPage = 0
    private var totalGalleryWebPages = 1
    private var isGalleryLoadingMore = false
    private var galleryJob: Job? = null
    var galleryScrollIndex = 0
    var galleryScrollOffset = 0
    var feedScrollIndex = 0
    private val _feed = MutableStateFlow<List<UnifiedPost>>(emptyList())
    val feed = _feed.asStateFlow()
    private val _endReached = MutableStateFlow(false)
    val endReached = _endReached.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _suggestions = MutableStateFlow<List<AutocompleteDto>>(emptyList())
    val suggestions = _suggestions.asStateFlow()
    private val _tagsList = MutableStateFlow<List<String>>(emptyList())
    val tagsList = _tagsList.asStateFlow()
    private val _currentFilter = MutableStateFlow(FileFilter.ALL)
    val currentFilter = _currentFilter.asStateFlow()
    private var currentPage = 0
    private var searchJob: Job? = null
    private var prefetchJob: Job? = null
    private val _verComicsReady = MutableStateFlow(false)
    val verComicsReady = _verComicsReady.asStateFlow()
    private val autocompleteCache = mutableMapOf<String, List<AutocompleteDto>>()
    private val _readerPosts = MutableStateFlow<List<UnifiedPost>?>(null)
    val readerPosts = _readerPosts.asStateFlow()
    private val _readerIndex = MutableStateFlow(0)
    val readerIndex = _readerIndex.asStateFlow()
    private val _startInGalleryMode = MutableStateFlow(false)
    val startInGalleryMode = _startInGalleryMode.asStateFlow()
    var isBypassCancelled by mutableStateOf(false)
        private set
    private val _showBypassWebView = MutableStateFlow(false)
    val showBypassWebView = _showBypassWebView.asStateFlow()
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState = _errorState.asStateFlow()


    fun loadMoreReaderContent() {
        if (_loading.value) {
            Log.d("TrinityDebug", "Intento de carga ignorado: YA ESTÁ CARGANDO ALGO.")
            return
        }

        viewModelScope.launch {
            Log.d("TrinityDebug", "1. Iniciando 'loadMoreReaderContent'. Tamaño actual Feed: ${_feed.value.size}")

            val job = loadContent()
            if (job == null) {
                Log.d("TrinityDebug", "2. loadContent devolvió null. (Quizás fin alcanzado o sin tags).")
                return@launch
            }

            Log.d("TrinityDebug", "2. Esperando a que el servidor responda...")
            job.join() // <--- ESTA ES LA CLAVE. Esperamos a que 'feed' se actualice.

            Log.d("TrinityDebug", "3. Carga finalizada. Nuevo tamaño Feed: ${_feed.value.size}")

            var currentList = _feed.value
            val MAX_ITEMS = 150
            val ITEMS_TO_TRIM = 50

            if (currentList.size > MAX_ITEMS) {
                Log.d("TrinityDebug", "4. Limpiando memoria (Exceso de items). Borrando $ITEMS_TO_TRIM antiguos.")
                val trimmedList = currentList.drop(ITEMS_TO_TRIM)

                _feed.value = trimmedList
                _readerPosts.value = trimmedList

                val newIndex = (_readerIndex.value - ITEMS_TO_TRIM).coerceAtLeast(0)
                _readerIndex.value = newIndex
                feedScrollIndex = (feedScrollIndex - ITEMS_TO_TRIM).coerceAtLeast(0)
            } else {
                Log.d("TrinityDebug", "4. Actualizando Lector sin borrar nada.")
                _readerPosts.value = currentList
            }
            Toast.makeText(getApplication(), "Cargados: ${currentList.size} posts", Toast.LENGTH_SHORT).show()
        }
    }

    fun reloadCredentials(context: Context) {
        val creds = SettingsStore.getCredentials(context)
        SourceManager.updateCredentials(
            r34User = creds["r34_user"] ?: "",
            r34Key = creds["r34_key"] ?: "",
            e621User = creds["e621_user"] ?: "",
            e621Key = creds["e621_key"] ?: ""
        )
        BlacklistManager.init(context)
        resetAndReload()
    }

    fun saveFeedPosition(index: Int) {
        feedScrollIndex = index
        if (index > 25) {
            val itemsToRemove = index - 10
            if (itemsToRemove > 0 && itemsToRemove < _feed.value.size) {
                _feed.value = _feed.value.drop(itemsToRemove)
                feedScrollIndex = index - itemsToRemove
            }
        }
    }
    fun startGalleryPrefetch(context: Context, pages: List<GalleryPageDto>, source: SourceType) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val pagesToPreload = pages.take(40)
            Log.d("PREFETCH", "Iniciando precarga inteligente para $source...")

            for (page in pagesToPreload) {
                if (_currentGalleryPages.value == null) break

                try {
                    val request = ImageRequest.Builder(context)
                        .data(page.viewerUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)

                    if (source == SourceType.VERCOMICS) {
                        request.addHeader("Cookie", VerComicsModule.getCookiesAsString())
                        request.addHeader("User-Agent", VerComicsModule.USER_AGENT)
                        request.addHeader("Referer", currentGalleryUrl)
                    } else if (source == SourceType.EHENTAI) {
                        request.addHeader("Cookie", "nw=1")
                    }

                    val result = context.imageLoader.execute(request.build())

                    if (result.drawable == null) {
                        delay(500)
                        context.imageLoader.execute(request.build())
                    }

                    val delayMs = if (source == SourceType.VERCOMICS) 400L else 150L
                    delay(delayMs)

                } catch (e: Exception) {
                    Log.e("PREFETCH", "Error precarga: ${e.message}")
                }
            }
        }
    }
    fun onVerComicsBypassSuccess() {
        _verComicsReady.value = true
        isBypassCancelled = false
        _showBypassWebView.value = false
        loadContent()
        viewModelScope.launch {
            if (cachedVerComicsTags.isEmpty()) {
                cachedVerComicsTags = VerComicsModule.getTags()
            }
        }
    }
    fun setSource(source: SourceType) {
        if (_currentSource.value != source) {
            clearModuleState()
            _currentSource.value = source
            if (source == SourceType.CHAN) viewModelScope.launch { cachedChanBoards = try { FourChanModule.getBoardsList() } catch(e:Exception){ emptyList() } }
            if (source == SourceType.REDDIT) RedditModule.resetPagination()
            if (source == SourceType.VERCOMICS) {
                validateVerComicsSession()
                if (!_verComicsReady.value) {
                    _showBypassWebView.value = true
                } else {
                    loadContent()
                }
            } else {
                loadContent()
            }
        }
    }
    private fun clearModuleState() {
        searchJob?.cancel()
        galleryJob?.cancel()

        _feed.value = emptyList()
        _tagsList.value = emptyList()
        _suggestions.value = emptyList()
        _currentFilter.value = FileFilter.ALL
        _currentGalleryPages.value = null
        _readerPosts.value = null
        _endReached.value = false
        _errorState.value = null

        currentPage = 0
        feedScrollIndex = 0
        galleryScrollIndex = 0
        galleryScrollOffset = 0

        cachedChanBoards = emptyList()
        cachedVerComicsTags = emptyList()
        RedditModule.resetPagination()
        autocompleteCache.clear()

        System.gc()
        Runtime.getRuntime().gc()
    }
    fun loadContent(): Job? {
        if (_loading.value || _endReached.value) return null
        if (_tagsList.value.isEmpty()) {
            when (_currentSource.value) {
                SourceType.R34,
                SourceType.E621,
                SourceType.REALBOORU,
                SourceType.REDDIT,
                SourceType.CHAN,
                SourceType.EHENTAI -> return null;
                else -> {}
            }
        }
        _errorState.value = null
        _loading.value = true
        return viewModelScope.launch {
            try {
                val module = SourceManager.getModule(_currentSource.value)
                val rawPostsDownload = module.getPosts(currentPage, _tagsList.value, _currentFilter.value)
                val rawPosts = rawPostsDownload.filter { post ->
                    post.tags.none { tag -> BlacklistManager.isBlocked(tag, _currentSource.value) }
                }
                if (rawPosts.isEmpty()) {
                    if (rawPostsDownload.isNotEmpty()) {
                        currentPage++
                        loadContent()
                    } else {
                        _endReached.value = true
                    }
                    _loading.value = false
                    return@launch
                }

                val filteredPosts =  when (_currentFilter.value) {
                    FileFilter.ALL -> rawPosts
                    FileFilter.IMG -> rawPosts.filter { it.type == MediaType.IMAGE }
                    FileFilter.VID -> rawPosts.filter {
                        if (it.source == SourceType.REDDIT) {
                            it.type == MediaType.VIDEO && !it.url.contains("redgifs") && !it.url.contains("gfycat") && !it.url.contains("imgur")
                        } else {
                            it.type == MediaType.VIDEO
                        }
                    }
                    FileFilter.GIF -> rawPosts.filter {
                        if (it.source == SourceType.REDDIT) {
                            it.type == MediaType.GIF || (it.type == MediaType.VIDEO && (it.url.contains("redgifs") || it.url.contains("gfycat") || it.url.contains("imgur") || it.url.endsWith(".gif") || it.url.endsWith(".gifv")))
                        } else {
                            it.type == MediaType.GIF
                        }
                    }
                    FileFilter.GAL -> rawPosts.filter { it.type == MediaType.GALLERY }
                    FileFilter.COUNT -> rawPosts
                }

                val currentIds = _feed.value.map { it.id }.toSet()
                val uniquePosts = filteredPosts.filter { !currentIds.contains(it.id) }

                if (uniquePosts.isNotEmpty()) {
                    _feed.value += uniquePosts
                    currentPage++
                } else if (rawPosts.isNotEmpty()) {
                    currentPage++
                    loadContent()
                }

            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error loading: ${e.message}")
                _errorState.value = e.localizedMessage ?: "Error desconocido"
            } finally {
                _loading.value = false
            }
        }
    }
    fun addTag(tag: String) {
        val clean = tag.trim()
        if (clean.isBlank()) return
        val current = _tagsList.value.toMutableList()
        if (_currentSource.value == SourceType.CHAN || _currentSource.value == SourceType.REDDIT) {
            current.clear()
        }

        if (!current.contains(clean)) {
            current.add(clean)
            _tagsList.value = current
            resetAndReload()
        }
    }
    fun removeTag(tag: String) {
        val current = _tagsList.value.toMutableList();
        current.remove(tag);
        _tagsList.value = current;
        resetAndReload()
    }

    fun blockTag(tag: String, global: Boolean) {
        val context = getApplication<Application>().applicationContext
        val source = if (global) null else _currentSource.value
        BlacklistManager.addTag(context, tag, source)
        val currentPosts = _feed.value
        _feed.value = currentPosts.filter { post ->
            !post.tags.contains(tag)
        }
        Toast.makeText(context, "Tag '$tag' bloqueado", Toast.LENGTH_SHORT).show()
    }

    fun setFilter(filter: FileFilter) {
        if (_currentFilter.value != filter) {
            _currentFilter.value = filter;
            resetAndReload()
        }
    }
    private fun resetAndReload() {
        searchJob?.cancel()
        if (_currentSource.value == SourceType.REDDIT) RedditModule.resetPagination()

        currentPage = 0
        feedScrollIndex = 0
        _endReached.value = false
        _feed.value = emptyList()
        _suggestions.value = emptyList()

        loadContent()
    }
    fun onSearchTextChange(text: String) {
        searchJob?.cancel()
        if (text.length < 2 && _currentSource.value != SourceType.CHAN) { _suggestions.value = emptyList(); return }
        if (text.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            if (autocompleteCache.containsKey(text)) {
                _suggestions.value = autocompleteCache[text]!!
                return@launch
            }
            delay(300)
            try {
                val results = when (_currentSource.value) {
                    SourceType.CHAN -> {
                        if (cachedChanBoards.isEmpty()) cachedChanBoards = FourChanModule.getBoardsList()
                        val clean = text.replace("/", "").lowercase()
                        if (clean.isEmpty()) cachedChanBoards else cachedChanBoards.filter {
                            it.value.contains(clean) || it.label.lowercase().contains(clean)
                        }
                    }
                    SourceType.VERCOMICS -> {
                        if (cachedVerComicsTags.isEmpty()) cachedVerComicsTags = VerComicsModule.getTags()
                        val clean = text.trim().lowercase()
                        cachedVerComicsTags.filter {
                            it.label.lowercase().contains(clean) || it.value.contains(clean)
                        }
                    }
                    SourceType.REDDIT -> {
                        if (_tagsList.value.isNotEmpty()) emptyList() else RedditModule.searchSubreddits(text)
                    }
                    else -> {
                        val cleanText = text.replace(" ", "_")
                        NetworkModule.api.getAutocomplete(cleanText)
                    }
                }
                val optimizedResults = results.take(15)
                if (optimizedResults.isNotEmpty()) {
                    autocompleteCache[text] = optimizedResults
                }
                _suggestions.value = optimizedResults

            } catch (e: Exception) {
                _suggestions.value = emptyList()
            }
        }
    }
    fun downloadFile(context: Context, url: String, targetUri: Uri) {
        Toast.makeText(context, "Descargando...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0...")
                if (url.contains("e-hentai") || url.contains("ehgt")) requestBuilder.addHeader("Cookie", "nw=1")
                else if (url.contains("realbooru")) requestBuilder.addHeader("Referer", "https://realbooru.com/")

                val response = NetworkModule.client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful || response.body == null) return@launch
                context.contentResolver.openOutputStream(targetUri)?.use { output -> response.body!!.byteStream().use { input -> input.copyTo(output) } }
                withContext(Dispatchers.Main) { Toast.makeText(context, "¡Archivo guardado!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { Log.e("Trinity", "Error descarga: ${e.message}") }
        }
    }
    fun openGallery(context: Context, post: UnifiedPost) {
        if (post.type != MediaType.GALLERY) return
        currentGalleryTitle = post.title
        currentGallerySource = post.source
        currentGalleryUrl = post.id
        currentGalleryWebPage = 0
        totalGalleryWebPages = 1
        isGalleryLoadingMore = false
        galleryScrollIndex = 0
        _currentGalleryPages.value = emptyList()
        _readerPosts.value = null
        _loading.value = true
        galleryJob?.cancel()

        val appContext = context.applicationContext

        galleryJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val module = SourceManager.getModule(post.source)
                val images = module.getDetails(post)

                withContext(Dispatchers.Main) {
                    if (images.isNotEmpty()) {
                        _currentGalleryPages.value = images
                        startGalleryPrefetch(appContext, images, post.source)
                    }
                    if (post.source == SourceType.EHENTAI) {
                        val (coverUrl, firstChunk, totalPages) = EHentaiModule.getGalleryPageChunk(post.id, 0)
                        totalGalleryWebPages = totalPages
                        withContext(Dispatchers.Main) {
                            if (firstChunk.isNotEmpty()) {
                                _currentGalleryPages.value = firstChunk
                                if (coverUrl.isNotEmpty() && post.previewUrl != coverUrl) updateFeedCover(post.id, coverUrl)
                                startGalleryPrefetch(appContext, firstChunk, post.source)
                            }
                            _loading.value = false
                        }
                    } else if (post.source == SourceType.VERCOMICS) {
                        val images = VerComicsModule.getChapterImages(post.url)
                        withContext(Dispatchers.Main) {
                            _currentGalleryPages.value = images
                            _loading.value = false
                            if (images.isNotEmpty()) {
                                startGalleryPrefetch(appContext, images, post.source)
                            }
                        }
                    } else if (post.source == SourceType.REDDIT) {
                        val images = RedditModule.getGalleryImages(post.id)
                        withContext(Dispatchers.Main) {
                            if (images.isNotEmpty()) {
                                _currentGalleryPages.value = images
                                startGalleryPrefetch(appContext, images, post.source)
                            } else {
                                _currentGalleryPages.value = listOf(GalleryPageDto(0, post.url, post.url))
                            }
                        }
                    }
                    _loading.value = false
                }
            } catch (e: Exception) {
            }
        }
    }
    fun loadMoreGalleryPages() {
        if (isGalleryLoadingMore || currentGalleryWebPage >= totalGalleryWebPages - 1) return
        if (currentGallerySource != SourceType.EHENTAI) return
        isGalleryLoadingMore = true
        currentGalleryWebPage++
        viewModelScope.launch(Dispatchers.IO) {
            val (_, nextChunk, _) = EHentaiModule.getGalleryPageChunk(currentGalleryUrl, currentGalleryWebPage)
            withContext(Dispatchers.Main) {
                if (nextChunk.isNotEmpty()) {
                    val currentList = _currentGalleryPages.value ?: emptyList()
                    val offset = currentList.size
                    val correctedChunk = nextChunk.mapIndexed { i, page -> page.copy(index = offset + i) }
                    _currentGalleryPages.value = currentList + correctedChunk
                }
                isGalleryLoadingMore = false
            }
        }
    }
    private fun updateFeedCover(postId: String, newUrl: String) {
        val index = _feed.value.indexOfFirst { it.id == postId }
        if (index != -1) {
            val updatedPost = _feed.value[index].copy(previewUrl = newUrl)
            val mutableFeed = _feed.value.toMutableList()
            mutableFeed[index] = updatedPost
            _feed.value = mutableFeed
        }
    }
    fun closeGallery() { galleryJob?.cancel(); galleryJob = null; _currentGalleryPages.value = null; currentGalleryUrl = "" }
    fun openGalleryAsReader(pages: List<GalleryPageDto>, startUrl: String) {
        val convertedPosts = pages.map { page ->
            UnifiedPost(page.viewerUrl, page.viewerUrl, page.thumbUrl, MediaType.IMAGE, currentGallerySource, "Página ${page.index + 1}",emptyList()) }
        _readerPosts.value = convertedPosts
        val startIndex = pages.indexOfFirst { it.viewerUrl == startUrl }
        _readerIndex.value = if (startIndex >= 0) startIndex else 0
        _startInGalleryMode.value = false
    }
    suspend fun resolveHdUrl(post: UnifiedPost): String {
        return try {
            SourceManager.getModule(post.source).resolveDirectLink(post)
        } catch (e: Exception) {
            post.url
        }
    }
    fun openSinglePost(post: UnifiedPost) { _readerPosts.value = listOf(post); _readerIndex.value = 0 }
    fun openChanThread(post: UnifiedPost) {
        if (post.source != SourceType.CHAN) return

        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val boardTag = post.tags.find { it.startsWith("/") && it.endsWith("/") }
            val board = boardTag?.replace("/", "") ?: "gif"

            val threadId = post.id.toLongOrNull() ?: 0L

            Log.d("4ChanDebug", "Intentando cargar Hilo: /$board/$threadId")

            val threadPosts = FourChanModule.getThreadPosts(board, threadId)

            withContext(Dispatchers.Main) {
                if (threadPosts.isNotEmpty()) {
                    _readerPosts.value = threadPosts.distinctBy { it.id }
                    _readerIndex.value = 0
                } else {
                    _readerPosts.value = listOf(post)
                    _readerIndex.value = 0
                }
                _loading.value = false
            }
        }
    }
    fun setExclusiveTag(tag: String) {
        _tagsList.value = listOf(tag)
        resetAndReload()
    }
    fun openFeedInReader(index: Int, startGallery: Boolean = false) {
        _readerPosts.value = _feed.value;
        _readerIndex.value = index
        _startInGalleryMode.value = startGallery
    }
    fun closeReader() { _readerPosts.value = null }
    fun saveGalleryPosition(index: Int, offset: Int) {
        galleryScrollIndex = index;
        galleryScrollOffset = offset
    }
    fun reviveBypass() {
        isBypassCancelled = false
        _verComicsReady.value = false
    }
    fun cancelBypass() {
        isBypassCancelled = true
    }
    fun validateVerComicsSession() {
        if (_verComicsReady.value) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("CookieCheck", "Iniciando validación silenciosa...")
                val url = "https://vercomicsporno.com/"
                val cookieManager = android.webkit.CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url)

                if (cookies.isNullOrEmpty() || !cookies.contains("cf_clearance")) {
                    Log.d("CookieCheck", "No hay cookies válidas. Lanzando Bypass.")
                    withContext(Dispatchers.Main) { _verComicsReady.value = false }
                    return@launch
                }

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", VerComicsModule.USER_AGENT)
                    .header("Cookie", cookies)
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                response.close()

                Log.d("CookieCheck", "Respuesta del servidor: $code")

                withContext(Dispatchers.Main) {
                    if (code == 200 || code == 404) {
                        Log.d("CookieCheck", "¡Cookies válidas! Bypass saltado.")
                        VerComicsModule.saveCookiesFromWebView(url, cookies)
                        _verComicsReady.value = true
                        isBypassCancelled = false
                    } else {
                        Log.d("CookieCheck", "Cookies caducadas o inválidas. Activando WebView.")
                        _verComicsReady.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("CookieCheck", "Error al validar: ${e.message}")
                withContext(Dispatchers.Main) { _verComicsReady.value = false }
            }
        }
    }
}