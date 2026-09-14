package com.janhelmich.coeus.data.homeassistant

import com.janhelmich.coeus.domain.Device
import com.janhelmich.coeus.domain.DeviceKind
import com.janhelmich.coeus.domain.DeviceState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * One entry of Home Assistant's `GET /api/states` response. Attributes are kept as raw JSON:
 * their shape depends entirely on the integration behind the entity.
 */
@Serializable
data class HaEntity(
    @SerialName("entity_id") val entityId: String,
    val state: String,
    val attributes: JsonObject = JsonObject(emptyMap()),
) {
    /** `switch` in `switch.kettle`. */
    val domain: String get() = entityId.substringBefore('.')

    val friendlyName: String get() = attributes.string("friendly_name") ?: entityId

    val deviceClass: String? get() = attributes.string("device_class")
}

/** The Home Assistant domains this app knows how to draw and control. */
val SUPPORTED_DOMAINS = setOf("switch", "light", "sensor")

/**
 * Maps an entity to a [Device], or `null` when it is something the app has no use for — a
 * `sensor` that is not a temperature or humidity reading, an automation, and so on.
 */
fun HaEntity.toDeviceOrNull(): Device? {
    val kind = when (domain) {
        "switch" -> DeviceKind.PLUG
        "light" -> DeviceKind.LIGHT
        "sensor" -> if (deviceClass in CLIMATE_CLASSES) DeviceKind.SENSOR else return null
        else -> return null
    }
    return Device(id = entityId, name = friendlyName, kind = kind)
}

/**
 * Maps an entity to a [DeviceState].
 *
 * Power readings on a switch follow the attribute names TP-Link plugs exposed in 2019, which
 * is what the thesis hardware was. Newer Home Assistant versions split those into separate
 * `sensor.*` entities, in which case they simply come back `null` here.
 */
fun HaEntity.toDeviceState(): DeviceState {
    if (state == "unavailable" || state == "unknown") return DeviceState.Unavailable
    return when (domain) {
        "switch" -> DeviceState.Plug(
            isOn = state == "on",
            powerW = attributes.double("current_power_w"),
            currentA = attributes.double("current_a"),
            voltageV = attributes.double("voltage"),
            energyTodayKwh = attributes.double("today_energy_kwh"),
            energyTotalKwh = attributes.double("total_energy_kwh"),
        )
        "light" -> DeviceState.Light(
            isOn = state == "on",
            brightness = attributes.double("brightness")?.let { (it / 255.0).toFloat().coerceIn(0f, 1f) },
        )
        "sensor" -> when (deviceClass) {
            "temperature" -> DeviceState.Sensor(temperatureC = state.toDoubleOrNull())
            "humidity" -> DeviceState.Sensor(humidityPercent = state.toDoubleOrNull())
            else -> DeviceState.Unavailable
        }
        else -> DeviceState.Unavailable
    }
}

private val CLIMATE_CLASSES = setOf("temperature", "humidity")

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
