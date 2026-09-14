package com.janhelmich.coeus.data.homeassistant

import com.janhelmich.coeus.domain.Device
import com.janhelmich.coeus.domain.DeviceKind
import com.janhelmich.coeus.domain.DeviceState
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HaEntityMappingTest {

    private fun entity(json: String) = HomeAssistantApi.json.decodeFromString(HaEntity.serializer(), json)

    @Test
    fun `tp-link switch maps to a plug with power readings`() {
        val e = entity(
            """
            {"entity_id":"switch.tplink","state":"on","attributes":{
              "friendly_name":"Kettle","current_power_w":"1987.3","current_a":8.64,"voltage":"229.9",
              "today_energy_kwh":0.42,"total_energy_kwh":118.6}}
            """.trimIndent(),
        )
        assertEquals(Device("switch.tplink", "Kettle", DeviceKind.PLUG), e.toDeviceOrNull())
        assertEquals(
            DeviceState.Plug(isOn = true, powerW = 1987.3, currentA = 8.64, voltageV = 229.9,
                energyTodayKwh = 0.42, energyTotalKwh = 118.6),
            e.toDeviceState(),
        )
    }

    @Test
    fun `switch without energy attributes has null readings`() {
        val e = entity("""{"entity_id":"switch.plain","state":"off","attributes":{}}""")
        assertEquals(DeviceState.Plug(isOn = false), e.toDeviceState())
        assertEquals("switch.plain", e.toDeviceOrNull()?.name)
    }

    @Test
    fun `light brightness is scaled from 0-255`() {
        val e = entity("""{"entity_id":"light.hue","state":"on","attributes":{"brightness":128}}""")
        val state = e.toDeviceState() as DeviceState.Light
        assertEquals(true, state.isOn)
        assertEquals(128f / 255f, state.brightness!!, 1e-6f)
    }

    @Test
    fun `climate sensors map by device class, others are ignored`() {
        val temp = entity("""{"entity_id":"sensor.t","state":"21.4","attributes":{"device_class":"temperature"}}""")
        val hum = entity("""{"entity_id":"sensor.h","state":"43","attributes":{"device_class":"humidity"}}""")
        val power = entity("""{"entity_id":"sensor.p","state":"12","attributes":{"device_class":"power"}}""")
        assertEquals(DeviceKind.SENSOR, temp.toDeviceOrNull()?.kind)
        assertEquals(DeviceState.Sensor(temperatureC = 21.4), temp.toDeviceState())
        assertEquals(DeviceState.Sensor(humidityPercent = 43.0), hum.toDeviceState())
        assertNull(power.toDeviceOrNull())
    }

    @Test
    fun `unavailable and unknown states map to Unavailable`() {
        assertEquals(DeviceState.Unavailable,
            entity("""{"entity_id":"switch.x","state":"unavailable","attributes":{}}""").toDeviceState())
        assertEquals(DeviceState.Unavailable,
            entity("""{"entity_id":"light.x","state":"unknown","attributes":{}}""").toDeviceState())
    }

    @Test
    fun `unsupported domains are dropped`() {
        assertNull(entity("""{"entity_id":"automation.morning","state":"on","attributes":{}}""").toDeviceOrNull())
    }

    @Test
    fun `states payload decodes with unknown fields present`() {
        val list = HomeAssistantApi.json.decodeFromString(
            ListSerializer(HaEntity.serializer()),
            """[{"entity_id":"light.a","state":"off","attributes":{},"last_changed":"2026-01-01T00:00:00+00:00","context":{"id":"x"}}]""",
        )
        assertEquals(listOf("light.a"), list.map { it.entityId })
    }
}
