package com.janhelmich.coeus.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The one seam between the AR/UI layers and whatever is actually switching lights.
 *
 * Two implementations exist: a simulated home that needs no hardware, and a Home Assistant
 * client. The UI never knows which one it is talking to.
 */
interface SmartHomeRepository {

    /** Human-readable backend name, shown in the UI so it is obvious which one is active. */
    val name: String

    /** Devices this backend exposes. May change over time (Home Assistant reloads). */
    val devices: Flow<List<Device>>

    /** Latest known state of every device, keyed by [Device.id]. */
    val states: Flow<Map<String, DeviceState>>

    /** Errors worth telling the user about, e.g. an unreachable Home Assistant. `null` clears. */
    val error: Flow<String?>

    suspend fun toggle(deviceId: String)

    /** [brightness] is 0..1. Ignored for anything that is not a dimmable light. */
    suspend fun setBrightness(deviceId: String, brightness: Float)

    /** Ask the backend for fresh data now rather than at its next poll. */
    suspend fun refresh()

    /** Convenience: the state of one device, [DeviceState.Unavailable] until known. */
    fun state(deviceId: String): Flow<DeviceState> =
        states.map { it[deviceId] ?: DeviceState.Unavailable }

    /** Devices paired with their current state, for list UIs. */
    val devicesWithState: Flow<List<Pair<Device, DeviceState>>>
        get() = combine(devices, states) { devices, states ->
            devices.map { it to (states[it.id] ?: DeviceState.Unavailable) }
        }
}
