package apap.oneverandah.api

import apap.oneverandah.state.DeviceTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

object ChatApi {
    private const val DEVICE_TOKEN_HEADER = "X-Device-Token"

    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    suspend fun createDeviceToken(): String {
        val response = client.post("${ImageApi.apiBaseUrl}/api/device")
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(DeviceTokenResponse.serializer(), text).deviceToken
    }

    suspend fun listSaved(): SavedChatMessagesResponse {
        val token = DeviceTokenStore.ensureToken()
        val response = client.get(chatApiUrl("/saved")) {
            header(DEVICE_TOKEN_HEADER, token)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(SavedChatMessagesResponse.serializer(), text)
    }

    suspend fun saveMessage(request: SaveChatMessageRequest): SavedChatMessage {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post(chatApiUrl("/saved")) {
            header(DEVICE_TOKEN_HEADER, token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SaveChatMessageRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(SavedChatMessage.serializer(), text)
    }

    suspend fun deleteSaved(id: String) {
        val token = DeviceTokenStore.ensureToken()
        val response = client.delete(chatApiUrl("/saved/$id")) {
            header(DEVICE_TOKEN_HEADER, token)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
    }

    suspend fun send(request: ChatRequest): ChatResponse {
        val response = client.post(chatApiUrl()) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChatRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(ChatResponse.serializer(), text)
    }

    suspend fun history(conversationId: String): ChatHistoryResponse {
        val response = client.get(chatApiUrl("/$conversationId"))
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(ChatHistoryResponse.serializer(), text)
    }

    suspend fun speech(request: SpeechRequest): SpeechResponse {
        val response = client.post(chatApiUrl("/speech")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SpeechRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(SpeechResponse.serializer(), text)
    }

    suspend fun speechDataUrl(request: SpeechRequest): String {
        val speech = speech(request)
        return "data:${speech.contentType};base64,${speech.audioBase64}"
    }

    /** Fast study skeleton: language + sentences + tokens (meanings still pending). */
    suspend fun study(request: StudySessionRequest): StudySessionResponse {
        val response = client.post(chatApiUrl("/study")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudySessionRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudySessionResponse.serializer(), text)
    }

    /** Full dissection for one skeleton sentence. */
    suspend fun studyDissect(request: StudyDissectSentenceRequest): StudyDissectSentenceResponse {
        val response = client.post(chatApiUrl("/study/dissect")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyDissectSentenceRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudyDissectSentenceResponse.serializer(), text)
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(ChatErrorResponse.serializer(), body).error }
            .getOrElse { "Chat request failed ($status)" }
}

internal fun chatApiUrl(path: String = ""): String = "${ImageApi.apiBaseUrl}/api/chat$path"

class ChatApiException(message: String) : RuntimeException(message)
