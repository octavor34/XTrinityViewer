package com.xtrinityviewer.data

import android.content.Context
import android.content.SharedPreferences

object BlacklistManager {
    private const val PREFS_NAME = "blacklist_prefs"
    private const val KEY_GLOBAL = "global_blacklist"

    // Cache en memoria para velocidad (HashSet es O(1) para búsquedas)
    private var globalCache = HashSet<String>()
    private var sourceCache = HashMap<SourceType, HashSet<String>>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Cargar Global
        val globalString = prefs.getString(KEY_GLOBAL, "") ?: ""
        globalCache.addAll(globalString.split(",").map { it.trim() }.filter { it.isNotEmpty() })

        // Cargar por Fuente
        SourceType.values().forEach { source ->
            val sourceString = prefs.getString("blacklist_${source.name}", "") ?: ""
            sourceCache[source] = HashSet(sourceString.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        }
    }

    fun isBlocked(tag: String, source: SourceType): Boolean {
        // Normalizamos el tag (todo minúsculas y sin espacios extra)
        val cleanTag = tag.trim().lowercase()
        // Chequeo 1: ¿Está en la lista global?
        if (globalCache.contains(cleanTag)) return true
        // Chequeo 2: ¿Está en la lista específica de esta fuente?
        return sourceCache[source]?.contains(cleanTag) == true
    }

    fun addTag(context: Context, tag: String, source: SourceType? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanTag = tag.trim().lowercase()

        if (source == null) {
            // Agregar a Global
            globalCache.add(cleanTag)
            prefs.edit().putString(KEY_GLOBAL, globalCache.joinToString(",")).apply()
        } else {
            // Agregar a Fuente Específica
            val set = sourceCache.getOrPut(source) { HashSet() }
            set.add(cleanTag)
            prefs.edit().putString("blacklist_${source.name}", set.joinToString(",")).apply()
        }
    }

    fun removeTag(context: Context, tag: String, source: SourceType? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanTag = tag.trim().lowercase()

        if (source == null) {
            globalCache.remove(cleanTag)
            prefs.edit().putString(KEY_GLOBAL, globalCache.joinToString(",")).apply()
        } else {
            sourceCache[source]?.remove(cleanTag)
            prefs.edit().putString("blacklist_${source.name}", sourceCache[source]?.joinToString(",") ?: "").apply()
        }
    }

    fun getListString(source: SourceType?): String {
        return if (source == null) {
            globalCache.joinToString(", ")
        } else {
            sourceCache[source]?.joinToString(", ") ?: ""
        }
    }

    // Función para guardar edición masiva desde SetupScreen
    fun saveListString(context: Context, rawString: String, source: SourceType?) {
        val newSet = rawString.split(",", " ").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toHashSet()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (source == null) {
            globalCache = newSet
            prefs.edit().putString(KEY_GLOBAL, globalCache.joinToString(",")).apply()
        } else {
            sourceCache[source] = newSet
            prefs.edit().putString("blacklist_${source.name}", newSet.joinToString(",")).apply()
        }
    }
}