package com.janhelmich.deixis.data.homeassistant

import app.cash.turbine.test
import com.janhelmich.deixis.domain.DeviceKind
import com.janhelmich.deixis.domain.DeviceState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantRepositoryTest {

    private val statesJson = """
        [{"entity_id":"switch.kettle","state":"on","attributes":{"friendly_name":"Kettle"}},
         {"entity_id":"automation.x","state":"on","attributes":{}}]
    """.trimIndent()

    @Test
    fun `exposes supported devices with their state`() = runTest {
        var calls = 0
        val api = HomeAssistantApi("http://ha", "t", MockEngine {
            calls++
            respond(statesJson, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        val repo = HomeAssistantRepository(api, backgroundScope, pollInterval = 1.hours)

        val devices = repo.devices.first()
        assertEquals(listOf("switch.kettle"), devices.map { it.id })
        assertEquals(DeviceKind.PLUG, devices.single().kind)
        assertEquals(DeviceState.Plug(isOn = true), repo.state("switch.kettle").first())
        assertNull(repo.error.first())
        assertEquals(1, calls)
    }

    @Test
    fun `a failing backend keeps devices but marks them unavailable and reports the error`() = runTest {
        var fail = false
        val api = HomeAssistantApi("http://ha", "t", MockEngine {
            if (fail) respondError(HttpStatusCode.ServiceUnavailable)
            else respond(statesJson, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        val repo = HomeAssistantRepository(api, backgroundScope, pollInterval = 1.hours)

        repo.states.test {
            assertEquals(DeviceState.Plug(isOn = true), awaitItem()["switch.kettle"])
            fail = true
            repo.refresh()
            assertEquals(DeviceState.Unavailable, awaitItem()["switch.kettle"])
            assertNotNull(repo.error.first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle calls the domain toggle service and refreshes`() = runTest {
        val urls = mutableListOf<String>()
        val api = HomeAssistantApi("http://ha", "t", MockEngine { request ->
            urls += request.url.encodedPath
            respond(statesJson, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        val repo = HomeAssistantRepository(api, backgroundScope, pollInterval = 1.hours)

        repo.states.test {
            awaitItem()
            repo.toggle("switch.kettle")
            awaitItem() // the refresh triggered by the command
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("/api/states", "/api/services/switch/toggle", "/api/states"), urls)
    }
}
