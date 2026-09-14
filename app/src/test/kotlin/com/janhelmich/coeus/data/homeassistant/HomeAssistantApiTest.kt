package com.janhelmich.coeus.data.homeassistant

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAssistantApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(body: String = "{}") = HomeAssistantApi(
        baseUrl = "http://ha.local:8123/",
        token = "secret-token",
        engine = MockEngine { request ->
            requests += request
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        },
    )

    @Test
    fun `ping hits api root with bearer token`() = runTest {
        val message = api("""{"message":"API running."}""").use { it.ping() }
        assertEquals("API running.", message)
        val request = requests.single()
        assertEquals("http://ha.local:8123/api/", request.url.toString())
        assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `states decodes the entity list`() = runTest {
        val states = api("""[{"entity_id":"switch.a","state":"on","attributes":{}}]""").use { it.states() }
        assertEquals("switch.a", states.single().entityId)
        assertEquals("http://ha.local:8123/api/states", requests.single().url.toString())
    }

    @Test
    fun `callService posts entity id plus extra fields as json`() = runTest {
        api().use {
            it.callService("light", "turn_on", "light.desk", buildJsonObject {
                put("brightness_pct", JsonPrimitive(40))
            })
        }
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://ha.local:8123/api/services/light/turn_on", request.url.toString())
        val body = (request.body as TextContent).text
        assertEquals("""{"entity_id":"light.desk","brightness_pct":40}""", body)
    }
}
