package apap.oneverandah.api

import apap.oneverandah.challenges.AdminChallengesResponse
import apap.oneverandah.challenges.Challenge
import apap.oneverandah.challenges.ChallengeApiError
import apap.oneverandah.challenges.ChallengeDetailResponse
import apap.oneverandah.challenges.ChallengeLogic
import apap.oneverandah.challenges.ChallengeRenameRequest
import apap.oneverandah.challenges.ChallengeUploadResponse
import apap.oneverandah.challenges.ChallengeVoteRequest
import apap.oneverandah.challenges.ChallengesFeedResponse
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.w3c.files.File
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ChallengeApi {
    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun base(): String = ImageApi.apiBaseUrl

    suspend fun fetchFeed(): ChallengesFeedResponse = try {
        val token = runCatching { DeviceTokenStore.ensureToken() }.getOrNull()
        val response = client.get("${base()}/api/v1/challenges") {
            if (!token.isNullOrBlank()) header("X-Device-Token", token)
        }
        json.decodeFromString(ChallengesFeedResponse.serializer(), response.bodyAsText())
    } catch (e: Throwable) {
        console.error("Failed to load challenges: ${e.message}")
        ChallengesFeedResponse()
    }

    suspend fun fetchChallenge(id: String): ChallengeDetailResponse? = try {
        val token = runCatching { DeviceTokenStore.ensureToken() }.getOrNull()
        val response = client.get("${base()}/api/v1/challenges/$id") {
            if (!token.isNullOrBlank()) header("X-Device-Token", token)
        }
        if (!response.status.isSuccess()) return null
        json.decodeFromString(ChallengeDetailResponse.serializer(), response.bodyAsText())
    } catch (e: Throwable) {
        console.error("Failed to load challenge: ${e.message}")
        null
    }

    suspend fun upload(
        name: String,
        file: File,
        challengeId: String? = null,
        onProgress: (Int) -> Unit = {},
    ): ChallengeUploadResponse {
        val token = DeviceTokenStore.ensureToken()
        val path = if (challengeId.isNullOrBlank()) {
            "${base()}/api/v1/challenges"
        } else {
            "${base()}/api/v1/challenges/$challengeId/submissions"
        }
        val form = FormData()
        form.append("name", name)
        form.append("file", file)
        val accepted = xhrPost(path, form, token, onProgress)
        val pending = json.decodeFromString(ChallengeUploadResponse.serializer(), accepted)
        return pollJob(pending.jobId, onProgress)
    }

    suspend fun vote(challengeId: String, submissionId: String, emoji: String): ChallengeDetailResponse {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post("${base()}/api/v1/challenges/$challengeId/submissions/$submissionId/vote") {
            header("X-Device-Token", token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChallengeVoteRequest.serializer(), ChallengeVoteRequest(emoji)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChallengeApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(ChallengeDetailResponse.serializer(), text)
    }

    suspend fun fetchAdmin(): AdminChallengesResponse {
        val response = client.get("${base()}/api/v1/admin/challenges") { authed() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChallengeApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(AdminChallengesResponse.serializer(), text)
    }

    suspend fun publish(submissionId: String) {
        val response = client.post("${base()}/api/v1/admin/submissions/$submissionId/publish") { authed() }
        if (!response.status.isSuccess()) {
            throw ChallengeApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun rename(id: String, name: String): Challenge {
        val response = client.post("${base()}/api/v1/admin/challenges/$id/rename") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChallengeRenameRequest.serializer(), ChallengeRenameRequest(name)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChallengeApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Challenge.serializer(), text)
    }

    suspend fun deleteChallenge(id: String) {
        val response = client.delete("${base()}/api/v1/admin/challenges/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw ChallengeApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun deleteSubmission(id: String) {
        val response = client.delete("${base()}/api/v1/admin/submissions/$id") { authed() }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw ChallengeApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    private suspend fun pollJob(jobId: String, onProgress: (Int) -> Unit): ChallengeUploadResponse {
        repeat(80) {
            val response = client.get("${base()}/api/v1/challenges/jobs/$jobId")
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) throw ChallengeApiException(errorMessage(text, response.status.value))
            val job = json.decodeFromString(ChallengeUploadResponse.serializer(), text)
            onProgress(90 + (job.progress.coerceIn(0, 100) / 10))
            when (job.status) {
                ChallengeLogic.JOB_READY -> return job
                ChallengeLogic.JOB_FAILED -> throw ChallengeApiException(job.error ?: "Processing failed")
            }
            delay(400)
        }
        throw ChallengeApiException("Processing is taking longer than expected")
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
                onProgress(40 + (fraction * 50).toInt())
            }
        }
        xhr.onreadystatechange = {
            if (xhr.readyState == XMLHttpRequest.DONE) {
                if (xhr.status in 200..299) {
                    if (cont.isActive) cont.resume(xhr.responseText)
                } else if (cont.isActive) {
                    cont.resumeWithException(
                        ChallengeApiException(errorMessage(xhr.responseText, xhr.status.toInt())),
                    )
                }
            }
        }
        xhr.onerror = {
            if (cont.isActive) cont.resumeWithException(ChallengeApiException("Upload failed"))
        }
        cont.invokeOnCancellation { xhr.abort() }
        xhr.send(form)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        val token = AdminAuth.token
        if (!token.isNullOrBlank()) header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(ChallengeApiError.serializer(), body).error }
            .getOrElse { "Request failed ($status)" }
}

class ChallengeApiException(message: String) : RuntimeException(message)
