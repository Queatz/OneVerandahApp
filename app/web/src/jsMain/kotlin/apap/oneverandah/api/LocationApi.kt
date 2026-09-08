package apap.oneverandah.api

import apap.oneverandah.locations.AdminLocationsResponse
import apap.oneverandah.locations.CommitLocationFundsRequest
import apap.oneverandah.locations.ContactLocationRequest
import apap.oneverandah.locations.Location
import apap.oneverandah.locations.LocationApiError
import apap.oneverandah.locations.LocationCard
import apap.oneverandah.locations.LocationDetailResponse
import apap.oneverandah.locations.LocationPhotoUploadResponse
import apap.oneverandah.locations.LocationUpsertRequest
import apap.oneverandah.locations.LocationsFeedResponse
import apap.oneverandah.state.AdminAuth
import apap.oneverandah.state.DeviceTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import org.w3c.files.File
import org.w3c.fetch.RequestInit
import org.w3c.xhr.FormData

object LocationApi {
    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun base(): String = ImageApi.apiBaseUrl

    suspend fun fetchFeed(): LocationsFeedResponse = try {
        val token = runCatching { DeviceTokenStore.ensureToken() }.getOrNull()
        val response = client.get("${base()}/api/v1/locations") {
            if (!token.isNullOrBlank()) header("X-Device-Token", token)
        }
        json.decodeFromString(LocationsFeedResponse.serializer(), response.bodyAsText())
    } catch (e: Throwable) {
        console.error("Failed to load locations: ${e.message}")
        LocationsFeedResponse()
    }

    suspend fun fetchLocation(id: String): LocationDetailResponse? = try {
        val token = runCatching { DeviceTokenStore.ensureToken() }.getOrNull()
        val response = client.get("${base()}/api/v1/locations/$id") {
            if (!token.isNullOrBlank()) header("X-Device-Token", token)
        }
        if (!response.status.isSuccess()) return null
        json.decodeFromString(LocationDetailResponse.serializer(), response.bodyAsText())
    } catch (e: Throwable) {
        console.error("Failed to load location: ${e.message}")
        null
    }

    suspend fun toggleLike(id: String): LocationCard {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post("${base()}/api/v1/locations/$id/like") {
            header("X-Device-Token", token)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw LocationApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(LocationCard.serializer(), text)
    }

    suspend fun commitFunds(id: String, amountVnd: Long, message: String, zaloNumber: String): LocationDetailResponse {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post("${base()}/api/v1/locations/$id/commitments") {
            header("X-Device-Token", token)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CommitLocationFundsRequest.serializer(),
                    CommitLocationFundsRequest(amountVnd = amountVnd, message = message, zaloNumber = zaloNumber),
                ),
            )
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw LocationApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(LocationDetailResponse.serializer(), text)
    }

    suspend fun contactHost(id: String, message: String, zaloNumber: String): LocationDetailResponse {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post("${base()}/api/v1/locations/$id/contacts") {
            header("X-Device-Token", token)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ContactLocationRequest.serializer(),
                    ContactLocationRequest(message = message, zaloNumber = zaloNumber),
                ),
            )
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw LocationApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(LocationDetailResponse.serializer(), text)
    }

    suspend fun fetchAdmin(): AdminLocationsResponse {
        val response = client.get("${base()}/api/v1/admin/locations") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw LocationApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(AdminLocationsResponse.serializer(), text)
    }

    suspend fun create(request: LocationUpsertRequest): Location {
        val response = client.post("${base()}/api/v1/admin/locations") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LocationUpsertRequest.serializer(), request.copy(id = null)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw LocationApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Location.serializer(), text)
    }

    suspend fun update(id: String, request: LocationUpsertRequest): Location {
        val response = client.put("${base()}/api/v1/admin/locations/$id") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LocationUpsertRequest.serializer(), request.copy(id = id)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw LocationApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Location.serializer(), text)
    }

    suspend fun delete(id: String) {
        val response = client.delete("${base()}/api/v1/admin/locations/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw LocationApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun uploadPhoto(file: File): String {
        val token = AdminAuth.token ?: throw LocationApiException("Not signed in")
        val form = FormData()
        form.append("file", file)
        val init = js("({})")
        init.method = "POST"
        val headers = js("({})")
        headers[HttpHeaders.Authorization] = "Bearer $token"
        init.headers = headers
        init.body = form
        val response = window.fetch("${base()}/api/v1/admin/locations/photos", init.unsafeCast<RequestInit>()).await()
        val text = response.text().await()
        if (!response.ok) throw LocationApiException(errorMessage(text, response.status.toInt()))
        return json.decodeFromString(LocationPhotoUploadResponse.serializer(), text).url
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        val token = AdminAuth.token
        if (!token.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(LocationApiError.serializer(), body).error }
            .getOrElse { "Request failed ($status)" }
}

class LocationApiException(message: String) : RuntimeException(message)
