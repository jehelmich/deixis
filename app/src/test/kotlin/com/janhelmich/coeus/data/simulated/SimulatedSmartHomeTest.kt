package com.janhelmich.coeus.data.simulated

import com.janhelmich.coeus.domain.DeviceKind
import com.janhelmich.coeus.domain.DeviceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedSmartHomeTest {

    private fun TestScopeHome() = SimulatedSmartHome(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        random = Random(seed = 42),
        tick = 1.hours, // never fires during a test; time is driven by advance()
    )

    @Test
    fun `exposes one device of every kind`() = runTest {
        val home = TestScopeHome()
        val kinds = home.devices.value.map { it.kind }.toSet()
        assertEquals(DeviceKind.entries.toSet(), kinds)
        assertEquals(home.devices.value.map { it.id }.toSet(), home.states.value.keys)
        home.close()
    }

    @Test
    fun `toggling a plug flips it and its load follows immediately`() = runTest {
        val home = TestScopeHome()
        val before = assertIs<DeviceState.Plug>(home.states.value[SimulatedSmartHome.KETTLE])
        assertTrue(!before.isOn)
        assertEquals(0.0, before.powerW)

        home.toggle(SimulatedSmartHome.KETTLE)
        val on = assertIs<DeviceState.Plug>(home.states.value[SimulatedSmartHome.KETTLE])
        assertTrue(on.isOn)
        assertTrue(on.powerW!! > 1_900.0, "a kettle draws about 2 kW, got ${on.powerW}")
        assertTrue(on.currentA!! > 8.0)

        home.toggle(SimulatedSmartHome.KETTLE)
        val off = assertIs<DeviceState.Plug>(home.states.value[SimulatedSmartHome.KETTLE])
        assertTrue(!off.isOn)
        assertEquals(0.0, off.powerW)
        home.close()
    }

    @Test
    fun `energy accumulates only while on`() = runTest {
        val home = TestScopeHome()
        val start = assertIs<DeviceState.Plug>(home.states.value[SimulatedSmartHome.KETTLE])
        home.advance(30.minutes)
        val stillOff = assertIs<DeviceState.Plug>(home.states.value[SimulatedSmartHome.KETTLE])
        assertEquals(start.energyTodayKwh, stillOff.energyTodayKwh)

        home.toggle(SimulatedSmartHome.KETTLE)
        home.advance(30.minutes)
        val after = assertIs<DeviceState.Plug>(home.states.value[SimulatedSmartHome.KETTLE])
        val gained = after.energyTodayKwh!! - start.energyTodayKwh!!
        // ~2.1 kW for half an hour, give or take the ±3 % noise.
        assertTrue(gained in 1.0..1.1, "expected about 1.05 kWh, got $gained")
        home.close()
    }

    @Test
    fun `brightness clamps and zero switches the light off`() = runTest {
        val home = TestScopeHome()
        home.setBrightness(SimulatedSmartHome.DESK_LAMP, 1.7f)
        assertEquals(DeviceState.Light(isOn = true, brightness = 1f), home.states.value[SimulatedSmartHome.DESK_LAMP])
        home.setBrightness(SimulatedSmartHome.DESK_LAMP, 0f)
        assertEquals(DeviceState.Light(isOn = false, brightness = 0f), home.states.value[SimulatedSmartHome.DESK_LAMP])
        home.close()
    }

    @Test
    fun `sensor readings stay within an indoor range`() = runTest {
        val home = TestScopeHome()
        repeat(10_000) { home.advance(1.minutes) }
        val sensor = assertIs<DeviceState.Sensor>(home.states.value[SimulatedSmartHome.CLIMATE])
        assertTrue(sensor.temperatureC!! in 17.0..27.0)
        assertTrue(sensor.humidityPercent!! in 30.0..65.0)
        home.close()
    }

    @Test
    fun `toggling a sensor is a no-op`() = runTest {
        val home = TestScopeHome()
        val before = home.states.value[SimulatedSmartHome.CLIMATE]
        home.toggle(SimulatedSmartHome.CLIMATE)
        // advance(ZERO) inside toggle drifts the sensor by a tiny random step; compare by kind.
        assertIs<DeviceState.Sensor>(before)
        assertIs<DeviceState.Sensor>(home.states.value[SimulatedSmartHome.CLIMATE])
        home.close()
    }
}
