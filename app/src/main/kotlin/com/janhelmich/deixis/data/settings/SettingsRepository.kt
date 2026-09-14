package com.janhelmich.deixis.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Backend { SIMULATED, HOME_ASSISTANT }

/**
 * Everything needed to pick and reach a backend.
 *
 * The token is stored in app-private DataStore, which is plain-text on disk. That is fine for a
 * demo on your own phone; a production app would wrap it with the Android Keystore.
 */
data class ConnectionSettings(
    val backend: Backend = Backend.SIMULATED,
    val baseUrl: String = "http://homeassistant.local:8123",
    val token: String = "",
) {
    val isHomeAssistantConfigured: Boolean
        get() = backend == Backend.HOME_ASSISTANT && baseUrl.isNotBlank() && token.isNotBlank()
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<ConnectionSettings> = dataStore.data.map { prefs ->
        ConnectionSettings(
            backend = prefs[BACKEND]?.let { runCatching { Backend.valueOf(it) }.getOrNull() }
                ?: Backend.SIMULATED,
            baseUrl = prefs[BASE_URL] ?: ConnectionSettings().baseUrl,
            token = prefs[TOKEN] ?: "",
        )
    }

    suspend fun save(settings: ConnectionSettings) {
        dataStore.edit { prefs ->
            prefs[BACKEND] = settings.backend.name
            prefs[BASE_URL] = settings.baseUrl.trim()
            prefs[TOKEN] = settings.token.trim()
        }
    }

    private companion object {
        val BACKEND = stringPreferencesKey("backend")
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
    }
}
