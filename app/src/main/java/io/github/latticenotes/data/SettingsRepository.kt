package io.github.latticenotes.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.latticenotes.util.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: String = "system",
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1f,
    val appLockEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val defaultFormat: String = NoteFormat.TEXT.name,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavAutoSync: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val fontScale = floatPreferencesKey("font_scale")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val defaultFormat = stringPreferencesKey("default_format")
        val webDavUrl = stringPreferencesKey("webdav_url")
        val webDavUsername = stringPreferencesKey("webdav_username")
        val webDavAutoSync = booleanPreferencesKey("webdav_auto_sync")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            AppSettings(
                themeMode = preferences[Keys.themeMode] ?: "system",
                dynamicColor = preferences[Keys.dynamicColor] ?: false,
                fontScale = (preferences[Keys.fontScale] ?: 1f).coerceIn(0.8f, 1.5f),
                appLockEnabled = preferences[Keys.lockEnabled] ?: false,
                pinHash = preferences[Keys.pinHash] ?: "",
                pinSalt = preferences[Keys.pinSalt] ?: "",
                defaultFormat = preferences[Keys.defaultFormat] ?: NoteFormat.TEXT.name,
                webDavUrl = preferences[Keys.webDavUrl] ?: "",
                webDavUsername = preferences[Keys.webDavUsername] ?: "",
                webDavAutoSync = preferences[Keys.webDavAutoSync] ?: true,
            )
        }

    suspend fun setThemeMode(value: String) {
        context.settingsDataStore.edit { it[Keys.themeMode] = value }
    }

    suspend fun setDynamicColor(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.dynamicColor] = value }
    }

    suspend fun setFontScale(value: Float) {
        context.settingsDataStore.edit { it[Keys.fontScale] = value.coerceIn(0.8f, 1.5f) }
    }

    suspend fun setDefaultFormat(value: NoteFormat) {
        context.settingsDataStore.edit { it[Keys.defaultFormat] = value.name }
    }

    suspend fun setWebDav(url: String, username: String) {
        context.settingsDataStore.edit {
            it[Keys.webDavUrl] = url.trim()
            it[Keys.webDavUsername] = username.trim()
        }
    }

    suspend fun setWebDavAutoSync(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.webDavAutoSync] = enabled }
    }

    suspend fun clearWebDav() {
        context.settingsDataStore.edit {
            it.remove(Keys.webDavUrl)
            it.remove(Keys.webDavUsername)
        }
    }

    suspend fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        context.settingsDataStore.edit {
            it[Keys.lockEnabled] = true
            it[Keys.pinSalt] = salt
            it[Keys.pinHash] = hash
        }
    }

    suspend fun clearPin() {
        context.settingsDataStore.edit {
            it[Keys.lockEnabled] = false
            it.remove(Keys.pinSalt)
            it.remove(Keys.pinHash)
        }
    }
}
