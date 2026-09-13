package apap.oneverandah.api

import apap.oneverandah.clubs.AdminClubsResponse
import apap.oneverandah.clubs.Club
import apap.oneverandah.clubs.ClubActionResponse
import apap.oneverandah.clubs.ClubApiError
import apap.oneverandah.clubs.ClubDetailResponse
import apap.oneverandah.clubs.ClubPhotoUploadResponse
import apap.oneverandah.clubs.ClubUpsertRequest
import apap.oneverandah.clubs.ClubsFeedResponse
import apap.oneverandah.clubs.CreateClubRequestPayload
import apap.oneverandah.clubs.JoinClubRequest
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

object ClubApi {
    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun base(): String = ImageApi.apiBaseUrl

    suspend fun fetchFeed(): ClubsFeedResponse = try {
        val response = client.get("${base()}/api/v1/clubs")
        json.decodeFromString(ClubsFeedResponse.serializer(), response.bodyAsText())
    } catch (e: Throwable) {
        console.error("Failed to load clubs: ${e.message}")
        ClubsFeedResponse()
    }

    suspend fun fetchClub(id: String): ClubDetailResponse? = try {
        val response = client.get("${base()}/api/v1/clubs/$id")
        if (!response.status.isSuccess()) return null
        json.decodeFromString(ClubDetailResponse.serializer(), response.bodyAsText())
    } catch (e: Throwable) {
        console.error("Failed to load club: ${e.message}")
        null
    }

    suspend fun requestCreate(
        name: String,
        tagline: String,
        description: String,
        hostName: String,
        hostZalo: String,
    ) {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post("${base()}/api/v1/clubs/requests") {
            header("X-Device-Token", token)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateClubRequestPayload.serializer(),
                    CreateClubRequestPayload(
                        name = name,
                        tagline = tagline,
                        description = description,
                        hostName = hostName,
                        hostZalo = hostZalo,
                    ),
                ),
            )
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ClubApiException(errorMessage(text, response.status.value))
        runCatching { json.decodeFromString(ClubActionResponse.serializer(), text) }
    }

    suspend fun join(
        id: String,
        name: String,
        zaloNumber: String,
        answers: List<String>,
        message: String,
    ): ClubDetailResponse {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post("${base()}/api/v1/clubs/$id/joins") {
            header("X-Device-Token", token)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JoinClubRequest.serializer(),
                    JoinClubRequest(
                        name = name,
                        zaloNumber = zaloNumber,
                        answers = answers,
                        message = message,
                    ),
                ),
            )
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ClubApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(ClubDetailResponse.serializer(), text)
    }

    suspend fun fetchAdmin(): AdminClubsResponse {
        val response = client.get("${base()}/api/v1/admin/clubs") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ClubApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(AdminClubsResponse.serializer(), text)
    }

    suspend fun create(request: ClubUpsertRequest): Club {
        val response = client.post("${base()}/api/v1/admin/clubs") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ClubUpsertRequest.serializer(), request.copy(id = null)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ClubApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Club.serializer(), text)
    }

    suspend fun update(id: String, request: ClubUpsertRequest): Club {
        val response = client.put("${base()}/api/v1/admin/clubs/$id") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ClubUpsertRequest.serializer(), request.copy(id = id)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ClubApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Club.serializer(), text)
    }

    suspend fun delete(id: String) {
        val response = client.delete("${base()}/api/v1/admin/clubs/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw ClubApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun deleteCreateRequest(id: String) {
        val response = client.delete("${base()}/api/v1/admin/clubs/requests/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw ClubApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun deleteJoin(id: String) {
        val response = client.delete("${base()}/api/v1/admin/clubs/joins/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw ClubApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun uploadPhoto(file: File): String {
        val token = AdminAuth.token ?: throw ClubApiException("Not signed in")
        val form = FormData()
        form.append("file", file)
        val init = js("({})")
        init.method = "POST"
        val headers = js("({})")
        headers[HttpHeaders.Authorization] = "Bearer $token"
        init.headers = headers
        init.body = form
        val response = window.fetch("${base()}/api/v1/admin/clubs/photos", init.unsafeCast<RequestInit>()).await()
        val text = response.text().await()
        if (!response.ok) throw ClubApiException(errorMessage(text, response.status.toInt()))
        return json.decodeFromString(ClubPhotoUploadResponse.serializer(), text).url
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        val token = AdminAuth.token
        if (!token.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(ClubApiError.serializer(), body).error }
            .getOrElse { "Request failed ($status)" }
}

class ClubApiException(message: String) : RuntimeException(message)
