package com.janhelmich.deixis.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.janhelmich.deixis.data.homeassistant.HomeAssistantApi
import com.janhelmich.deixis.data.homeassistant.HomeAssistantRepository
import com.janhelmich.deixis.data.homeassistant.toDeviceOrNull
import com.janhelmich.deixis.data.settings.ConnectionSettings
import com.janhelmich.deixis.data.settings.SettingsRepository
import com.janhelmich.deixis.data.simulated.SimulatedSmartHome
import com.janhelmich.deixis.domain.SmartHomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Hand-rolled dependency graph. The app has one of everything, so a container object is clearer
 * than a DI framework and keeps the build free of annotation processing.
 */
class AppContainer(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context.applicationContext.settingsDataStore)

    private val _repository = MutableStateFlow<SmartHomeRepository>(SimulatedSmartHome(scope))

    /** The active backend. Swaps when the user changes settings; collectors see the new one. */
    val repository: StateFlow<SmartHomeRepository> = _repository.asStateFlow()

    init {
        scope.launch {
            settings.settings
                .map { if (it.isHomeAssistantConfigured) it else ConnectionSettings() }
                .distinctUntilChanged()
                .collect { config ->
                    val previous = _repository.value
                    _repository.value = build(config)
                    (previous as? AutoCloseable)?.close()
                    (previous as? SimulatedSmartHome)?.close()
                }
        }
    }

    private fun build(config: ConnectionSettings): SmartHomeRepository =
        if (config.isHomeAssistantConfigured) {
            HomeAssistantRepository(HomeAssistantApi(config.baseUrl, config.token), scope)
        } else {
            SimulatedSmartHome(scope)
        }

    /** Try the given settings without saving them. Returns a one-line human-readable verdict. */
    suspend fun testConnection(config: ConnectionSettings): Result<String> = runCatching {
        HomeAssistantApi(config.baseUrl, config.token).use { api ->
            val status = api.ping()
            val usable = api.states().count { it.toDeviceOrNull() != null }
            "$status $usable usable device(s) found."
        }
    }
}
