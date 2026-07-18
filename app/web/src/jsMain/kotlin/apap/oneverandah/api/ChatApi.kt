package apap.oneverandah.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

object ChatApi {
    private val client by lazy { HttpClient() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
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

    suspend fun speechDataUrl(request: SpeechRequest): String {
        val response = client.post(chatApiUrl("/speech")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SpeechRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        val speech = json.decodeFromString(SpeechResponse.serializer(), text)
        return "data:${speech.contentType};base64,${speech.audioBase64}"
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(ChatErrorResponse.serializer(), body).error }
            .getOrElse { "Chat request failed ($status)" }
}

internal fun chatApiUrl(path: String = ""): String = "${ImageApi.apiBaseUrl}/api/chat$path"

class ChatApiException(message: String) : RuntimeException(message)