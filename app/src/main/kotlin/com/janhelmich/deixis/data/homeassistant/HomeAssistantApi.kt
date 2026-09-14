package com.janhelmich.deixis.data.homeassistant

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The slice of Home Assistant's REST API this app uses.
 * See <https://developers.home-assistant.io/docs/api/rest/>.
 *
 * Authenticated with a long-lived access token (Profile → Security in the HA frontend). The
 * token is supplied at runtime from settings and never lives in source.
 */
class HomeAssistantApi(
    baseUrl: String,
    token: String,
    engine: HttpClientEngine = OkHttp.create(),
) : AutoCloseable {

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 10_000
        }
        defaultRequest {
            url(baseUrl.trimEnd('/') + "/api/")
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
        }
    }

    @Serializable
    private data class ApiStatus(val message: String)

    /** `GET /api/` — succeeds with "API running." when the URL and token are right. */
    suspend fun ping(): String = client.get("").body<ApiStatus>().message

    /** `GET /api/states` — every entity HA knows about. */
    suspend fun states(): List<HaEntity> = client.get("states").body()

    /**
     * `POST /api/services/{domain}/{service}` targeting one entity.
     * [data] carries extra service fields such as `brightness_pct`.
     */
    suspend fun callService(domain: String, service: String, entityId: String, data: JsonObject = EMPTY) {
        client.post("services/$domain/$service") {
            setBody(buildJsonObject {
                put("entity_id", JsonPrimitive(entityId))
                data.forEach { (k, v) -> put(k, v) }
            })
        }
    }

    override fun close() = client.close()

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val EMPTY = JsonObject(emptyMap())
    }
}
