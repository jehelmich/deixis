package com.janhelmich.deixis.data.homeassistant

import com.janhelmich.deixis.domain.DeviceKind
import com.janhelmich.deixis.domain.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the real client against a real Home Assistant — the demo instance under
 * `homeassistant/` — and skips itself unless `DEIXIS_HA_URL` and `DEIXIS_HA_TOKEN` are set.
 * Uses the entities HA's `demo` integration ships, and puts each one back as it found it.
 */
class HomeAssistantLiveTest {

    private val url = System.getenv("DEIXIS_HA_URL")
    private val token = System.getenv("DEIXIS_HA_TOKEN")
    private lateinit var api: HomeAssistantApi

    @Before
    fun connect() {
        assumeTrue("Set DEIXIS_HA_URL and DEIXIS_HA_TOKEN to run against a live Home Assistant",
            !url.isNullOrBlank() && !token.isNullOrBlank())
        api = HomeAssistantApi(url!!, token!!)
    }

    @Test
    fun `the api answers and the demo entities map to devices`() = runBlocking<Unit> {
        assertEquals("API running.", api.ping())
        val devices = api.states().mapNotNull { it.toDeviceOrNull() }.associateBy { it.id }
        assertEquals(DeviceKind.PLUG, devices.getValue("switch.decorative_lights").kind)
        assertEquals(DeviceKind.LIGHT, devices.getValue("light.ceiling_lights").kind)
        assertEquals(DeviceKind.SENSOR, devices.getValue("sensor.outside_temperature").kind)
        assertEquals("Outside Humidity", devices.getValue("sensor.outside_humidity").name)
    }

    @Test
    fun `toggling a switch through the repository is reflected by the next poll`() = runBlocking<Unit> {
        val repo = HomeAssistantRepository(api, this, pollInterval = 60.seconds)
        val before = assertIs<DeviceState.Plug>(repo.state(SWITCH).first())
        try {
            repo.toggle(SWITCH)
            awaitState(repo, SWITCH) { (it as? DeviceState.Plug)?.isOn == !before.isOn }
        } finally {
            repo.toggle(SWITCH)
            awaitState(repo, SWITCH) { (it as? DeviceState.Plug)?.isOn == before.isOn }
            repo.close()
            coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        }
    }

    @Test
    fun `brightness is written as a percentage and read back as 0-255`() = runBlocking<Unit> {
        val repo = HomeAssistantRepository(api, this, pollInterval = 60.seconds)
        try {
            repo.setBrightness(LIGHT, 0.5f)
            val state = awaitState(repo, LIGHT) { (it as? DeviceState.Light)?.let { l -> l.isOn && l.brightness != null } == true }
            val brightness = (state as DeviceState.Light).brightness!!
            // 50 % → HA stores round(255 * 0.5) = 128 → 128/255.
            assertTrue(brightness in 0.49f..0.51f, "expected ~0.5, got $brightness")
        } finally {
            repo.setBrightness(LIGHT, 0f)
            awaitState(repo, LIGHT) { (it as? DeviceState.Light)?.isOn == false }
            repo.close()
            coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        }
    }

    private suspend fun awaitState(
        repo: HomeAssistantRepository,
        id: String,
        predicate: (DeviceState) -> Boolean,
    ): DeviceState = withTimeout(15.seconds) {
        while (true) {
            val state = repo.state(id).first()
            if (predicate(state)) return@withTimeout state
            delay(300)
            repo.refresh()
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private companion object {
        const val SWITCH = "switch.ac"
        const val LIGHT = "light.bed_light"
    }
}
