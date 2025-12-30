package com.xtrinityviewer.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class) // ExoPlayer Cache API es marcada como inestable pero es estándar usarla
object VideoCacheManager {
    private var simpleCache: SimpleCache? = null

    // Tamaño máximo de la caché: 500 MB (Ajustable)
    // Si pasa de esto, borrará los videos más viejos automáticamente.
    private const val MAX_CACHE_SIZE = 500 * 1024 * 1024L

    // Obtener la instancia única de la caché
    fun getInstance(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "trinity_video_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)

            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }

    // Crear la "Fábrica de Datos" que sabe leer de la caché o de internet
    fun getDataSourceFactory(context: Context): DataSource.Factory {
        val cache = getInstance(context)

        // El "Upstream" es la conexión real a internet (si no está en caché)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("TrinityViewer/1.0")
            .setAllowCrossProtocolRedirects(true)

        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // CacheDataSource: Intenta leer del disco -> si falla, va a internet -> y guarda en disco
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}