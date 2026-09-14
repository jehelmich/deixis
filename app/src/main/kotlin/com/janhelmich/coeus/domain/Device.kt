package com.janhelmich.coeus.domain

/** What a device fundamentally is, which decides both its 3D shape and the controls it gets. */
enum class DeviceKind { PLUG, LIGHT, SENSOR }

/** Static identity of a smart-home device. Live readings live in [DeviceState]. */
data class Device(
    val id: String,
    val name: String,
    val kind: DeviceKind,
)

/**
 * A snapshot of a device's live state. Readings a backend cannot supply are `null` rather than
 * zero so the UI can show "—" instead of a misleading value.
 */
sealed interface DeviceState {

    /** The backend could not reach the device, or it is not one we know how to read. */
    data object Unavailable : DeviceState

    data class Plug(
        val isOn: Boolean,
        val powerW: Double? = null,
        val currentA: Double? = null,
        val voltageV: Double? = null,
        val energyTodayKwh: Double? = null,
        val energyTotalKwh: Double? = null,
    ) : DeviceState

    data class Light(
        val isOn: Boolean,
        /** 0..1, or `null` when the light is not dimmable. */
        val brightness: Float? = null,
    ) : DeviceState

    data class Sensor(
        val temperatureC: Double? = null,
        val humidityPercent: Double? = null,
    ) : DeviceState
}

val DeviceState.isOn: Boolean
    get() = when (this) {
        is DeviceState.Plug -> isOn
        is DeviceState.Light -> isOn
        is DeviceState.Sensor, DeviceState.Unavailable -> false
    }
