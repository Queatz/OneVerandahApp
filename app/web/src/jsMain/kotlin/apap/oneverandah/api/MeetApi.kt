package apap.oneverandah.api

import apap.oneverandah.meets.AdminMeetsResponse
import apap.oneverandah.meets.Meet
import apap.oneverandah.meets.MeetApiError
import apap.oneverandah.meets.MeetCard
import apap.oneverandah.meets.MeetDetailResponse
import apap.oneverandah.meets.MeetsFeedResponse
import apap.oneverandah.meets.ResetMeetRemainingRequest
import apap.oneverandah.meets.SendMeetPitchRequest
import apap.oneverandah.state.AdminAuth
import apap.oneverandah.state.DeviceTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.w3c.files.File
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MeetApi {
    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun base(): String = ImageApi.apiBaseUrl

    suspend fun fetchFeed(): MeetsFeedResponse = try {
        val token = runCatching { DeviceTokenStore.ensureToken() }.getOrNull()
        val response = client.get("${base()}/api/v1/meets") {
            if (!token.isNullOrBlank()) header("X-Device-Token", token)
        }
        json.decodeFromString(MeetsFeedResponse.serializer(), response.bodyAsText())
    } catch (e: Throwable) {
        console.error("Failed to load meets: ${e.message}")
        MeetsFeedResponse()
    }

    suspend fun fetchMeet(id: String): MeetDetailResponse? = try {
        val token = runCatching { DeviceTokenStore.ensureToken() }.getOrNull()
        val response = client.get("${base()}/api/v1/meets/$id") {
            if (!token.isNullOrBlank()) header("X-Device-Token", token)
        }
        if (!response.status.isSuccess()) return null
        json.decodeFromString(MeetDetailResponse.serializer(), response.bodyAsText())
    } catch (e: Throwable) {
        console.error("Failed to load meet: ${e.message}")
        null
    }

    suspend fun create(
        name: String,
        pitch: String,
        zaloNumber: String,
        pitchLimit: Int,
        file: File,
        onProgress: (Int) -> Unit = {},
    ): MeetCard {
        val token = DeviceTokenStore.ensureToken()
        val form = FormData()
        form.append("name", name)
        form.append("pitch", pitch)
        form.append("zaloNumber", zaloNumber)
        form.append("pitchLimit", pitchLimit.toString())
        form.append("file", file)
        val accepted = xhrPost("${base()}/api/v1/meets", form, token, onProgress)
        onProgress(100)
        return json.decodeFromString(MeetCard.serializer(), accepted)
    }

    suspend fun sendPitch(meetId: String, message: String, zaloNumber: String): MeetDetailResponse {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post("${base()}/api/v1/meets/$meetId/pitches") {
            header("X-Device-Token", token)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    SendMeetPitchRequest.serializer(),
                    SendMeetPitchRequest(message = message, zaloNumber = zaloNumber),
                ),
            )
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw MeetApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(MeetDetailResponse.serializer(), text)
    }

    suspend fun fetchAdmin(): AdminMeetsResponse {
        val response = client.get("${base()}/api/v1/admin/meets") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw MeetApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(AdminMeetsResponse.serializer(), text)
    }

    suspend fun approve(id: String) {
        val response = client.post("${base()}/api/v1/admin/meets/$id/approve") { authed() }
        if (!response.status.isSuccess()) {
            throw MeetApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun resetRemaining(id: String, remainingPitches: Int): Meet {
        val response = client.post("${base()}/api/v1/admin/meets/$id/reset") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ResetMeetRemainingRequest.serializer(),
                    ResetMeetRemainingRequest(remainingPitches = remainingPitches),
                ),
            )
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw MeetApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Meet.serializer(), text)
    }

    suspend fun deleteMeet(id: String) {
        val response = client.delete("${base()}/api/v1/admin/meets/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw MeetApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun completePitch(id: String) {
        val response = client.post("${base()}/api/v1/admin/meet-pitches/$id/complete") { authed() }
        if (!response.status.isSuccess()) {
            throw MeetApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun deletePitch(id: String) {
        val response = client.delete("${base()}/api/v1/admin/meet-pitches/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw MeetApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    private suspend fun xhrPost(
        url: String,
        form: FormData,
        token: String,
        onProgress: (Int) -> Unit,
    ): String = suspendCancellableCoroutine { cont ->
        val xhr = XMLHttpRequest()
        xhr.open("POST", url)
        xhr.setRequestHeader("X-Device-Token", token)
        xhr.upload.onprogress = { event ->
            val total = event.asDynamic().total as Double
            val loaded = event.asDynamic().loaded as Double
            if (total > 0.0) {
                val fraction = (loaded / total).coerceIn(0.0, 1.0)
                onProgress(10 + (fraction * 80).toInt())
            }
        }
        xhr.onreadystatechange = {
            if (xhr.readyState == XMLHttpRequest.DONE) {
                if (xhr.status in 200..299) {
                    if (cont.isActive) cont.resume(xhr.responseText)
                } else if (cont.isActive) {
                    cont.resumeWithException(
                        MeetApiException(errorMessage(xhr.responseText, xhr.status.toInt())),
                    )
                }
            }
        }
        xhr.onerror = {
            if (cont.isActive) cont.resumeWithException(MeetApiException("Upload failed"))
        }
        cont.invokeOnCancellation { xhr.abort() }
        xhr.send(form)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        val token = AdminAuth.token
        if (!token.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(MeetApiError.serializer(), body).error }
            .getOrElse { "Request failed ($status)" }
}

class MeetApiException(message: String) : RuntimeException(message)
