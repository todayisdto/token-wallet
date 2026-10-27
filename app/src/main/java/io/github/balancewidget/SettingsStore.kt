package io.github.balancewidget

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    var deepSeekKey: String
        get() = prefs.getString(KEY_DEEPSEEK, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEEPSEEK, value).apply()

    var aliyunId: String
        get() = prefs.getString(KEY_ALIYUN_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ALIYUN_ID, value).apply()

    var aliyunSecret: String
        get() = prefs.getString(KEY_ALIYUN_SECRET, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ALIYUN_SECRET, value).apply()

    var refreshMinutes: Int
        get() = prefs.getInt(KEY_REFRESH_MINUTES, DEFAULT_REFRESH_MINUTES)
            .coerceIn(MIN_REFRESH_MINUTES, MAX_REFRESH_MINUTES)
        set(value) = prefs.edit()
            .putInt(KEY_REFRESH_MINUTES, value.coerceIn(MIN_REFRESH_MINUTES, MAX_REFRESH_MINUTES))
            .apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM)
            ?.takeIf { it in setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK) }
            ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var hideRecents: Boolean
        get() = prefs.getBoolean(KEY_HIDE_RECENTS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_RECENTS, value).apply()

    val hasAnyKey: Boolean
        get() = deepSeekKey.isNotBlank() ||
            (aliyunId.isNotBlank() && aliyunSecret.isNotBlank())

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val FILE_NAME = "secure_settings"
        private const val KEY_DEEPSEEK = "deepseek_api_key"
        private const val KEY_ALIYUN_ID = "aliyun_access_key_id"
        private const val KEY_ALIYUN_SECRET = "aliyun_access_key_secret"
        private const val KEY_REFRESH_MINUTES = "refresh_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HIDE_RECENTS = "hide_recents"
        private const val DEFAULT_REFRESH_MINUTES = 60
        private const val MIN_REFRESH_MINUTES = 15
        private const val MAX_REFRESH_MINUTES = 360

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
