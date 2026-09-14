package com.janhelmich.coeus.data.homeassistant

import com.janhelmich.coeus.domain.Device
import com.janhelmich.coeus.domain.DeviceState
import com.janhelmich.coeus.domain.SmartHomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [SmartHomeRepository] backed by a Home Assistant instance.
 *
 * Polls `/api/states` while anything is collecting, and immediately after every command so the
 * UI reflects the result without waiting for the next poll. Losing the connection keeps the
 * device list but marks every state [DeviceState.Unavailable].
 */
class HomeAssistantRepository(
    private val api: HomeAssistantApi,
    scope: CoroutineScope,
    private val pollInterval: Duration = 5.seconds,
) : SmartHomeRepository, AutoCloseable {

    override val name = "Home Assistant"

    private val _error = MutableStateFlow<String?>(null)
    override val error: Flow<String?> = _error

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val entities = flow {
        var known = emptyList<HaEntity>()
        while (true) {
            known = try {
                api.states().filter { it.domain in SUPPORTED_DOMAINS }.also {
                    _error.value = null
                    emit(it)
                }
            } catch (e: Exception) {
                _error.value = "Home Assistant unreachable: ${e.message ?: e::class.simpleName}"
                emit(known.map { it.copy(state = "unavailable") })
                known
            }
            // Sleep until the next poll, or until someone asks for a refresh.
            withTimeoutOrNull(pollInterval) { refreshRequests.first() }
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    override val devices: Flow<List<Device>> =
        entities.map { list -> list.mapNotNull { it.toDeviceOrNull() } }

    override val states: Flow<Map<String, DeviceState>> =
        entities.map { list -> list.associate { it.entityId to it.toDeviceState() } }

    override suspend fun toggle(deviceId: String) {
        command { api.callService(deviceId.substringBefore('.'), "toggle", deviceId) }
    }

    override suspend fun setBrightness(deviceId: String, brightness: Float) {
        val pct = (brightness.coerceIn(0f, 1f) * 100).roundToInt()
        command {
            if (pct == 0) {
                api.callService("light", "turn_off", deviceId)
            } else {
                api.callService("light", "turn_on", deviceId, buildJsonObject {
                    put("brightness_pct", JsonPrimitive(pct))
                })
            }
        }
    }

    override suspend fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private suspend fun command(block: suspend () -> Unit) {
        try {
            block()
            _error.value = null
        } catch (e: Exception) {
            _error.value = "Command failed: ${e.message ?: e::class.simpleName}"
        }
        refresh()
    }

    override fun close() = api.close()
}
