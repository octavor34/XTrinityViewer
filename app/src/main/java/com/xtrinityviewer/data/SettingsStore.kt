package com.xtrinityviewer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import org.json.JSONObject

object SettingsStore {
    private const val OLD_PREFS_NAME = "trinity_settings"

    private const val SECURE_PREFS_NAME = "trinity_secure_settings"

    private const val NO_BACKUP_FILE = "local_credentials.json"

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

    private fun migrateIfNeeded(context: Context) {
        val oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
        if (oldPrefs.all.isNotEmpty()) {
            val securePrefs = getSecurePrefs(context)
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
            oldPrefs.edit().clear().apply()
        }
    }

    fun isBackupEnabled(context: Context): Boolean {
        migrateIfNeeded(context)
        return getSecurePrefs(context).getBoolean("backup_enabled", true)
    }

    fun setBackupEnabled(context: Context, enabled: Boolean) {
        val currentCreds = getCredentials(context)
        val prefs = getSecurePrefs(context)

        if (enabled) {
            prefs.edit().apply {
                putString("r34_user", currentCreds["r34_user"])
                putString("r34_key", currentCreds["r34_key"])
                putString("e621_user", currentCreds["e621_user"])
                putString("e621_key", currentCreds["e621_key"])
                putBoolean("backup_enabled", true)
                apply()
            }
            val file = File(context.filesDir, NO_BACKUP_FILE)
            if (file.exists()) file.delete()
        } else {
            val json = JSONObject().apply {
                put("r34_user", currentCreds["r34_user"])
                put("r34_key", currentCreds["r34_key"])
                put("e621_user", currentCreds["e621_user"])
                put("e621_key", currentCreds["e621_key"])
            }
            val file = File(context.filesDir, NO_BACKUP_FILE)
            file.writeText(json.toString())

            prefs.edit().apply {
                remove("r34_user")
                remove("r34_key")
                remove("e621_user")
                remove("e621_key")
                putBoolean("backup_enabled", false)
                apply()
            }
        }
    }

    fun saveCredentials(context: Context, r34User: String, r34Key: String, e621User: String, e621Key: String) {
        if (isBackupEnabled(context)) {
            getSecurePrefs(context).edit().apply {
                putString("r34_user", r34User)
                putString("r34_key", r34Key)
                putString("e621_user", e621User)
                putString("e621_key", e621Key)
                apply()
            }
        } else {
            val json = JSONObject().apply {
                put("r34_user", r34User)
                put("r34_key", r34Key)
                put("e621_user", e621User)
                put("e621_key", e621Key)
            }
            val file = File(context.filesDir, NO_BACKUP_FILE)
            file.writeText(json.toString())
        }
    }

    fun getCredentials(context: Context): Map<String, String> {
        migrateIfNeeded(context)
        val file = File(context.filesDir, NO_BACKUP_FILE)
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                return mapOf(
                    "r34_user" to json.optString("r34_user", ""),
                    "r34_key" to json.optString("r34_key", ""),
                    "e621_user" to json.optString("e621_user", ""),
                    "e621_key" to json.optString("e621_key", "")
                )
            } catch (e: Exception) { }
        }

        val prefs = getSecurePrefs(context)
        val r34u = prefs.getString("r34_user", null) ?: ""
        val r34k = prefs.getString("r34_key", null) ?: ""
        val e621u = prefs.getString("e621_user", null) ?: ""
        val e621k = prefs.getString("e621_key", null) ?: ""

        return mapOf(
            "r34_user" to r34u,
            "r34_key" to r34k,
            "e621_user" to e621u,
            "e621_key" to e621k
        )
    }
    fun isWelcomeSeen(context: Context): Boolean {
        return getSecurePrefs(context).getBoolean("welcome_seen_v1", false)
    }

    fun setWelcomeSeen(context: Context) {
        getSecurePrefs(context).edit().putBoolean("welcome_seen_v1", true).apply()
    }

    fun hasR34Credentials(context: Context): Boolean {
        val creds = getCredentials(context)
        return !creds["r34_user"].isNullOrEmpty() && !creds["r34_key"].isNullOrEmpty()
    }

    fun hasE621Credentials(context: Context): Boolean {
        val creds = getCredentials(context)
        return !creds["e621_user"].isNullOrEmpty() && !creds["e621_key"].isNullOrEmpty()
    }
}