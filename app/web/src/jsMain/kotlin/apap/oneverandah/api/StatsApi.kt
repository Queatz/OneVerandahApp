package apap.oneverandah.api

import apap.oneverandah.state.AdminAuth
import apap.oneverandah.state.DeviceTokenStore
import apap.oneverandah.stats.AdminStatsResponse
import apap.oneverandah.stats.StatsApiError
import apap.oneverandah.stats.VisitPingRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

object StatsApi {
    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun base(): String = ImageApi.apiBaseUrl

    suspend fun recordVisit(path: String, languageTag: String) {
        val token = runCatching { DeviceTokenStore.ensureToken() }.getOrNull() ?: return
        runCatching {
            client.post("${base()}/api/v1/visits") {
                header("X-Device-Token", token)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        VisitPingRequest.serializer(),
                        VisitPingRequest(path = path, languageTag = languageTag),
                    ),
                )
            }
        }
    }

    suspend fun fetchAdmin(): AdminStatsResponse {
        val response = client.get("${base()}/api/v1/admin/stats") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw StatsApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(AdminStatsResponse.serializer(), text)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        val token = AdminAuth.token
        if (!token.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(StatsApiError.serializer(), body).error }
            .getOrElse { "Request failed ($status)" }
}

class StatsApiException(message: String) : RuntimeException(message)
