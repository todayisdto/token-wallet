package io.github.balancewidget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsStore private constructor(context: Context) {

    private val appContext = context.applicationContext

    // Only secrets need the Keystore. Theme, interval and the recents flag used
    // to live here too, which meant every widget redraw paid for a Keystore
    // round trip on the main thread. They now live in plain prefs.
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences? by lazy { openSecurePrefs(appContext) }

    /** False when the Keystore entry is unusable; keys then read as empty. */
    val secureStorageAvailable: Boolean
        get() = securePrefs != null

    var deepSeekKey: String
        get() = secureString(KEY_DEEPSEEK)
        set(value) = putSecure(KEY_DEEPSEEK, value)

    var aliyunId: String
        get() = secureString(KEY_ALIYUN_ID)
        set(value) = putSecure(KEY_ALIYUN_ID, value)

    var aliyunSecret: String
        get() = secureString(KEY_ALIYUN_SECRET)
        set(value) = putSecure(KEY_ALIYUN_SECRET, value)

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

    /**
     * Theme/interval/recents used to live in the encrypted file. Copy them over
     * once so upgrading users keep their choices. Only called from the app, not
     * from widgets, so the widget path never touches the Keystore for this.
     */
    fun migrateLegacySettings() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val legacy = securePrefs ?: return
        val editor = prefs.edit()

        val minutes = legacy.getInt(KEY_REFRESH_MINUTES, 0)
        if (minutes in MIN_REFRESH_MINUTES..MAX_REFRESH_MINUTES) {
            editor.putInt(KEY_REFRESH_MINUTES, minutes)
        }
        val theme = legacy.getString(KEY_THEME_MODE, null)
        if (theme == THEME_SYSTEM || theme == THEME_LIGHT || theme == THEME_DARK) {
            editor.putString(KEY_THEME_MODE, theme)
        }
        if (legacy.contains(KEY_HIDE_RECENTS)) {
            editor.putBoolean(KEY_HIDE_RECENTS, legacy.getBoolean(KEY_HIDE_RECENTS, false))
        }
        editor.putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun secureString(key: String): String {
        val store = securePrefs ?: return ""
        return runCatching { store.getString(key, "") }.getOrNull().orEmpty()
    }

    private fun putSecure(key: String, value: String) {
        val store = securePrefs ?: return
        runCatching { store.edit().putString(key, value).apply() }
    }

    private fun openSecurePrefs(context: Context): SharedPreferences? {
        runCatching { createSecurePrefs(context) }.getOrNull()?.let { return it }

        // Changing the device lock (or restoring app data) can invalidate the
        // master key. Wipe the file and start over instead of crashing on every
        // launch; the user only has to re-enter the keys.
        Log.w(TAG, "Encrypted settings are unreadable, recreating the store")
        runCatching { context.deleteSharedPreferences(SECURE_FILE) }
        return runCatching { createSecurePrefs(context) }.getOrNull()
    }

    private fun createSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val TAG = "SettingsStore"
        private const val PREFS_FILE = "settings"
        private const val SECURE_FILE = "secure_settings"
        private const val KEY_DEEPSEEK = "deepseek_api_key"
        private const val KEY_ALIYUN_ID = "aliyun_access_key_id"
        private const val KEY_ALIYUN_SECRET = "aliyun_access_key_secret"
        private const val KEY_REFRESH_MINUTES = "refresh_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HIDE_RECENTS = "hide_recents"
        private const val KEY_MIGRATED = "legacy_settings_migrated"
        private const val DEFAULT_REFRESH_MINUTES = 60
        private const val MIN_REFRESH_MINUTES = 15
        private const val MAX_REFRESH_MINUTES = 360

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        @Volatile
        private var instance: SettingsStore? = null

        /** Process-wide instance: opening the encrypted store is expensive. */
        fun get(context: Context): SettingsStore {
            return instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
