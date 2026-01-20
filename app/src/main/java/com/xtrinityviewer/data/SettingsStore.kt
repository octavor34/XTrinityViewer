package com.xtrinityviewer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import org.json.JSONObject

object SettingsStore {
    // Nombre del archivo antiguo (Texto plano - solo para migración)
    private const val OLD_PREFS_NAME = "trinity_settings"
    // Nombre del archivo nuevo (Encriptado)
    private const val SECURE_PREFS_NAME = "trinity_secure_settings"
    // Archivo para cuando el usuario desactiva el backup (Modo Local)
    private const val NO_BACKUP_FILE = "local_credentials.json"

    // --- CONFIGURACIÓN DE SEGURIDAD ---
    private fun getSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- MIGRACIÓN (Compatibilidad con versiones viejas) ---
    private fun migrateIfNeeded(context: Context) {
        val oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
        if (oldPrefs.all.isNotEmpty()) {
            val securePrefs = getSecurePrefs(context)
            // Solo migramos si el destino está vacío para no sobrescribir datos nuevos
            if (securePrefs.all.isEmpty()) {
                val editor = securePrefs.edit()
                oldPrefs.all.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                    }
                }
                editor.apply()
            }
            // Borramos los datos inseguros viejos
            oldPrefs.edit().clear().apply()
        }
    }

    // ========================================================================
    //  NUEVO SISTEMA DINÁMICO (Soporta Gelbooru, Xbooru, etc.)
    // ========================================================================

    fun isBackupEnabled(context: Context): Boolean {
        migrateIfNeeded(context)
        return getSecurePrefs(context).getBoolean("backup_enabled", true)
    }

    /**
     * Guarda CUALQUIER credencial de forma segura.
     * Automáticamente decide si usar Encriptación en Nube o Archivo Local JSON.
     */
    fun saveSecureCredential(context: Context, key: String, value: String) {
        if (isBackupEnabled(context)) {
            // Modo Backup Activado: Guardar en EncryptedSharedPreferences
            getSecurePrefs(context).edit().putString(key, value).apply()
        } else {
            // Modo Local (Sin Nube): Guardar en JSON local
            val file = File(context.filesDir, NO_BACKUP_FILE)
            val json = if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) else JSONObject()
            json.put(key, value)
            file.writeText(json.toString())
        }
    }

    /**
     * Lee CUALQUIER credencial segura.
     * Busca primero en JSON local, luego en Prefs Encriptadas.
     */
    fun getSecureCredential(context: Context, key: String): String {
        migrateIfNeeded(context)

        // 1. Prioridad: JSON Local (si existe y tiene la llave)
        val file = File(context.filesDir, NO_BACKUP_FILE)
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                if (json.has(key)) return json.getString(key)
            } catch (e: Exception) { }
        }

        // 2. Fallback: Prefs Encriptadas
        return getSecurePrefs(context).getString(key, "") ?: ""
    }

    // ========================================================================
    //  GESTIÓN DE BACKUP (Refactorizado para mover TODO)
    // ========================================================================

    fun setBackupEnabled(context: Context, enabled: Boolean) {
        val prefs = getSecurePrefs(context)
        val file = File(context.filesDir, NO_BACKUP_FILE)

        if (enabled) {
            // CAMBIO A MODO NUBE (Mover de JSON -> Prefs)
            if (file.exists()) {
                try {
                    val json = JSONObject(file.readText())
                    val editor = prefs.edit()

                    // Iteramos TODAS las claves del JSON (R34, E621, Gelbooru...)
                    json.keys().forEach { key ->
                        val value = json.optString(key)
                        if (value.isNotEmpty()) editor.putString(key, value)
                    }
                    editor.putBoolean("backup_enabled", true)
                    editor.apply()

                    file.delete() // Borramos el archivo local
                } catch (e: Exception) { e.printStackTrace() }
            } else {
                prefs.edit().putBoolean("backup_enabled", true).apply()
            }
        } else {
            // CAMBIO A MODO LOCAL (Mover de Prefs -> JSON)
            val allEntries = prefs.all
            val json = JSONObject()
            val editor = prefs.edit()

            allEntries.forEach { (key, value) ->
                // Movemos solo los Strings (Credenciales)
                // Ignoramos los Booleans (como 'welcome_seen') para que no se pierdan configs de la app
                if (value is String && key != "backup_enabled") {
                    json.put(key, value)
                    editor.remove(key) // Lo borramos de la nube
                }
            }

            file.writeText(json.toString())

            editor.putBoolean("backup_enabled", false)
            editor.apply()
        }
    }

    // ========================================================================
    //  COMPATIBILIDAD LEGACY (Para que no rompa el resto de tu app)
    // ========================================================================

    // Usamos el sistema nuevo internamente
    fun saveCredentials(context: Context, r34User: String, r34Key: String, e621User: String, e621Key: String) {
        saveSecureCredential(context, "r34_user", r34User)
        saveSecureCredential(context, "r34_key", r34Key)
        saveSecureCredential(context, "e621_user", e621User)
        saveSecureCredential(context, "e621_key", e621Key)
    }

    // Reconstruimos el mapa usando el sistema nuevo
    fun getCredentials(context: Context): Map<String, String> {
        return mapOf(
            "r34_user" to getSecureCredential(context, "r34_user"),
            "r34_key" to getSecureCredential(context, "r34_key"),
            "e621_user" to getSecureCredential(context, "e621_user"),
            "e621_key" to getSecureCredential(context, "e621_key")
        )
    }

    // Helpers rápidos actualizados
    fun hasR34Credentials(context: Context): Boolean {
        return getSecureCredential(context, "r34_user").isNotEmpty() &&
                getSecureCredential(context, "r34_key").isNotEmpty()
    }

    fun hasE621Credentials(context: Context): Boolean {
        return getSecureCredential(context, "e621_user").isNotEmpty() &&
                getSecureCredential(context, "e621_key").isNotEmpty()
    }

    fun isWelcomeSeen(context: Context): Boolean {
        return getSecurePrefs(context).getBoolean("welcome_seen_v1", false)
    }

    fun setWelcomeSeen(context: Context) {
        getSecurePrefs(context).edit().putBoolean("welcome_seen_v1", true).apply()
    }
}