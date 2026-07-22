package apap.oneverandah.api

import apap.oneverandah.phrases.AdminSignInRequest
import apap.oneverandah.phrases.AdminSignInResponse
import apap.oneverandah.phrases.GeneratePhraseImageRequest
import apap.oneverandah.phrases.GeneratePhraseImageResponse
import apap.oneverandah.phrases.Phrase
import apap.oneverandah.phrases.PhraseApiError
import apap.oneverandah.phrases.PhraseReactRequest
import apap.oneverandah.phrases.PhraseUpsertRequest
import apap.oneverandah.phrases.PhrasesCoverResponse
import apap.oneverandah.phrases.PhrasesResponse
import apap.oneverandah.phrases.ReferencePhoto
import apap.oneverandah.phrases.ReferencePhotosResponse
import apap.oneverandah.state.AdminAuth
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
import org.w3c.dom.HTMLInputElement
import org.w3c.fetch.RequestInit
import org.w3c.files.File
import org.w3c.xhr.FormData

object PhraseApi {
    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun base(): String = ImageApi.apiBaseUrl

    suspend fun fetchPhrases(): PhrasesResponse = try {
        val text = client.get("${base()}/api/v1/phrases").bodyAsText()
        json.decodeFromString(PhrasesResponse.serializer(), text)
    } catch (e: Throwable) {
        console.error("Failed to load phrases: ${e.message}")
        PhrasesResponse(emptyList())
    }

    suspend fun fetchCover(): String? = try {
        val text = client.get("${base()}/api/v1/phrases/cover").bodyAsText()
        json.decodeFromString(PhrasesCoverResponse.serializer(), text).imageUrl
    } catch (e: Throwable) {
        console.error("Failed to load phrases cover: ${e.message}")
        null
    }

    suspend fun react(phraseId: String, emoji: String): Phrase? = try {
        val response = client.post("${base()}/api/v1/phrases/$phraseId/react") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(PhraseReactRequest.serializer(), PhraseReactRequest(emoji)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) return null
        json.decodeFromString(Phrase.serializer(), text)
    } catch (e: Throwable) {
        console.error("Failed to react: ${e.message}")
        null
    }

    suspend fun signIn(password: String): String {
        val response = client.post("${base()}/api/v1/admin/signin") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AdminSignInRequest.serializer(), AdminSignInRequest(password)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw PhraseApiException(errorMessage(text, response.status.value))
        }
        return json.decodeFromString(AdminSignInResponse.serializer(), text).token
    }

    suspend fun fetchAdminPhrases(): PhrasesResponse {
        val response = client.get("${base()}/api/v1/admin/phrases") {
            authed()
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw PhraseApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(PhrasesResponse.serializer(), text)
    }

    suspend fun createPhrase(request: PhraseUpsertRequest): Phrase {
        val response = client.post("${base()}/api/v1/admin/phrases") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(PhraseUpsertRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw PhraseApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Phrase.serializer(), text)
    }

    suspend fun updatePhrase(id: String, request: PhraseUpsertRequest): Phrase {
        val response = client.put("${base()}/api/v1/admin/phrases/$id") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(PhraseUpsertRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw PhraseApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(Phrase.serializer(), text)
    }

    suspend fun deletePhrase(id: String) {
        val response = client.delete("${base()}/api/v1/admin/phrases/$id") {
            authed()
        }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw PhraseApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    suspend fun generateImage(request: GeneratePhraseImageRequest): GeneratePhraseImageResponse {
        val response = client.post("${base()}/api/v1/admin/phrases/generate-image") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GeneratePhraseImageRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw PhraseApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(GeneratePhraseImageResponse.serializer(), text)
    }

    suspend fun fetchReferencePhotos(): List<ReferencePhoto> {
        val response = client.get("${base()}/api/v1/admin/reference-photos") {
            authed()
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw PhraseApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(ReferencePhotosResponse.serializer(), text).photos
    }

    /**
     * Uploads a named reference photo via browser FormData (Ktor JS multipart
     * support is limited; fetch is the reliable path here).
     */
    suspend fun uploadReferencePhoto(name: String, file: File): ReferencePhoto {
        val token = AdminAuth.token ?: throw PhraseApiException("Not signed in")
        val form = FormData()
        form.append("name", name)
        form.append("file", file)
        // Use dynamic RequestInit so we can set Authorization without a typed Headers map.
        val init = js("({})")
        init.method = "POST"
        val headers = js("({})")
        headers[HttpHeaders.Authorization] = "Bearer $token"
        init.headers = headers
        init.body = form
        val response = window.fetch("${base()}/api/v1/admin/reference-photos", init.unsafeCast<RequestInit>()).await()
        val text = response.text().await()
        if (!response.ok) throw PhraseApiException(errorMessage(text, response.status.toInt()))
        return json.decodeFromString(ReferencePhoto.serializer(), text)
    }

    suspend fun deleteReferencePhoto(id: String) {
        val response = client.delete("${base()}/api/v1/admin/reference-photos/$id") {
            authed()
        }
        if (!response.status.isSuccess() && response.status.value != 204) {
            throw PhraseApiException(errorMessage(response.bodyAsText(), response.status.value))
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        val token = AdminAuth.token
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(PhraseApiError.serializer(), body).error }
            .getOrElse { "Request failed ($status)" }
}

class PhraseApiException(message: String) : RuntimeException(message)

/** Convenience helper used by the admin photo picker. */
fun HTMLInputElement.firstFileOrNull(): File? = files?.item(0)
