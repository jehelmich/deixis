package com.janhelmich.deixis.data.simulated

import com.janhelmich.deixis.domain.Device
import com.janhelmich.deixis.domain.DeviceKind
import com.janhelmich.deixis.domain.DeviceState
import com.janhelmich.deixis.domain.SmartHomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A small pretend home so the app is usable without any hardware.
 *
 * Plugs draw a device-specific load with a little noise while on, and accumulate energy; the
 * climate sensor random-walks within a plausible indoor range. Everything is deterministic for a
 * given [random], which is what the unit tests rely on.
 */
class SimulatedSmartHome(
    scope: CoroutineScope,
    private val random: Random = Random.Default,
    private val tick: Duration = 1.seconds,
) : SmartHomeRepository {

    override val name = "Simulated home"

    private val loads = mapOf(
        KETTLE to 2_100.0,
        MONITOR to 34.0,
    )

    private val _devices = MutableStateFlow(
        listOf(
            Device(KETTLE, "Kettle", DeviceKind.PLUG),
            Device(MONITOR, "Monitor", DeviceKind.PLUG),
            Device(DESK_LAMP, "Desk lamp", DeviceKind.LIGHT),
            Device(FLOOR_LAMP, "Floor lamp", DeviceKind.LIGHT),
            Device(CLIMATE, "Living room climate", DeviceKind.SENSOR),
        ),
    )
    override val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _states = MutableStateFlow<Map<String, DeviceState>>(
        mapOf(
            KETTLE to DeviceState.Plug(isOn = false, powerW = 0.0, currentA = 0.0, voltageV = 230.0,
                energyTodayKwh = 0.42, energyTotalKwh = 118.6),
            MONITOR to DeviceState.Plug(isOn = true, powerW = 34.0, currentA = 0.15, voltageV = 230.0,
                energyTodayKwh = 0.21, energyTotalKwh = 57.3),
            DESK_LAMP to DeviceState.Light(isOn = true, brightness = 0.7f),
            FLOOR_LAMP to DeviceState.Light(isOn = false, brightness = 0.4f),
            CLIMATE to DeviceState.Sensor(temperatureC = 21.4, humidityPercent = 43.0),
        ),
    )
    override val states: StateFlow<Map<String, DeviceState>> = _states.asStateFlow()

    override val error: Flow<String?> = flowOf(null)

    private val ticker: Job = scope.launch {
        while (true) {
            delay(tick)
            advance(tick)
        }
    }

    /** One simulation step. Public so tests can drive time without a scheduler. */
    fun advance(elapsed: Duration) {
        val hours = elapsed.inWholeMilliseconds / 3_600_000.0
        _states.update { states ->
            states.mapValues { (id, state) ->
                when (state) {
                    is DeviceState.Plug -> state.advance(loads.getValue(id), hours)
                    is DeviceState.Sensor -> state.drift()
                    is DeviceState.Light, DeviceState.Unavailable -> state
                }
            }
        }
    }

    private fun DeviceState.Plug.advance(load: Double, hours: Double): DeviceState.Plug {
        val voltage = 230.0 + random.nextDouble(-1.5, 1.5)
        val power = if (isOn) load * (1 + random.nextDouble(-0.03, 0.03)) else 0.0
        val kwh = power * hours / 1_000
        return copy(
            powerW = power,
            currentA = power / voltage,
            voltageV = voltage,
            energyTodayKwh = (energyTodayKwh ?: 0.0) + kwh,
            energyTotalKwh = (energyTotalKwh ?: 0.0) + kwh,
        )
    }

    private fun DeviceState.Sensor.drift() = copy(
        temperatureC = ((temperatureC ?: 21.0) + random.nextDouble(-0.05, 0.05)).coerceIn(17.0, 27.0),
        humidityPercent = ((humidityPercent ?: 45.0) + random.nextDouble(-0.2, 0.2)).coerceIn(30.0, 65.0),
    )

    override suspend fun toggle(deviceId: String) {
        _states.update { states ->
            val next = when (val state = states[deviceId]) {
                is DeviceState.Plug -> state.copy(isOn = !state.isOn)
                is DeviceState.Light -> state.copy(isOn = !state.isOn)
                else -> return@update states
            }
            states + (deviceId to next)
        }
        // Reflect the new load immediately rather than at the next tick.
        advance(Duration.ZERO)
    }

    override suspend fun setBrightness(deviceId: String, brightness: Float) {
        _states.update { states ->
            val state = states[deviceId] as? DeviceState.Light ?: return@update states
            states + (deviceId to state.copy(isOn = brightness > 0f, brightness = brightness.coerceIn(0f, 1f)))
        }
    }

    override suspend fun refresh() = Unit

    fun close() = ticker.cancel()

    companion object {
        const val KETTLE = "plug.kettle"
        const val MONITOR = "plug.monitor"
        const val DESK_LAMP = "light.desk"
        const val FLOOR_LAMP = "light.floor"
        const val CLIMATE = "sensor.living_room"
    }
}
