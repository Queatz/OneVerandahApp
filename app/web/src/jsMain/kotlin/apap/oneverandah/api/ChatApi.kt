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
        if (!response.status.isSuccess()) {
            throw ChatApiException(errorMessage(text, response.status.value), response.status.value)
        }
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

    /** Judge a learner sentence using a dissected word and start comic image generation. */
    suspend fun studyPlayground(request: StudyWordPlaygroundRequest): StudyWordPlaygroundResponse {
        val response = client.post(chatApiUrl("/study/playground")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyWordPlaygroundRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudyWordPlaygroundResponse.serializer(), text)
    }

    /** Poll async playground comic image status. */
    suspend fun studyPlaygroundImage(imageId: String): StudyPlaygroundImageResponse {
        val response = client.get(chatApiUrl("/study/playground/image/$imageId"))
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudyPlaygroundImageResponse.serializer(), text)
    }

    /**
     * Design main/secondary story characters and start reference-portrait jobs.
     * Persist the returned cast on the study session; poll each imageId via [studyImage].
     */
    suspend fun studyStoryCharacters(request: StudyStoryCharactersRequest): StudyStoryCharactersResponse {
        val response = client.post(chatApiUrl("/study/characters")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyStoryCharactersRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudyStoryCharactersResponse.serializer(), text)
    }

    /** Start async expressive visual for one Study-tab sentence. */
    suspend fun studySentenceImage(request: StudySentenceImageRequest): StudySentenceImageResponse {
        val response = client.post(chatApiUrl("/study/sentence-image")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudySentenceImageRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudySentenceImageResponse.serializer(), text)
    }

    /** Generate four related Play-tab scenario starters (covers are async image jobs). */
    suspend fun studyPlayScenarios(request: StudyPlayScenariosRequest): StudyPlayScenariosResponse {
        val response = client.post(chatApiUrl("/study/play/scenarios")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyPlayScenariosRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudyPlayScenariosResponse.serializer(), text)
    }

    /** Continue a Play-tab conversation after the learner sends a message. */
    suspend fun studyPlayTurn(request: StudyPlayTurnRequest): StudyPlayTurnResponse {
        val response = client.post(chatApiUrl("/study/play/turn")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyPlayTurnRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudyPlayTurnResponse.serializer(), text)
    }

    suspend fun studyWriteCheck(request: StudyWriteCheckRequest): StudyWriteCheckResponse {
        val response = client.post(chatApiUrl("/study/write/check")) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyWriteCheckRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(StudyWriteCheckResponse.serializer(), text)
    }

    /** Poll async study image status (playground comics, portraits, and sentence visuals). */
    suspend fun studyImage(imageId: String): StudyPlaygroundImageResponse = studyPlaygroundImage(imageId)

    suspend fun listStudySessions(): SavedStudySessionsResponse {
        val token = DeviceTokenStore.ensureToken()
        val response = client.get(chatApiUrl("/study/sessions")) {
            header(DEVICE_TOKEN_HEADER, token)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(SavedStudySessionsResponse.serializer(), text)
    }

    suspend fun saveStudySession(request: SaveStudySessionRequest): SavedStudySession {
        val token = DeviceTokenStore.ensureToken()
        val response = client.post(chatApiUrl("/study/sessions")) {
            header(DEVICE_TOKEN_HEADER, token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SaveStudySessionRequest.serializer(), request))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
        return json.decodeFromString(SavedStudySession.serializer(), text)
    }

    suspend fun deleteStudySession(id: String) {
        val token = DeviceTokenStore.ensureToken()
        val response = client.delete(chatApiUrl("/study/sessions/$id")) {
            header(DEVICE_TOKEN_HEADER, token)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ChatApiException(errorMessage(text, response.status.value))
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString(ChatErrorResponse.serializer(), body).error }
            .getOrElse { "Chat request failed ($status)" }
}

internal fun chatApiUrl(path: String = ""): String = "${ImageApi.apiBaseUrl}/api/chat$path"

class ChatApiException(
    message: String,
    val statusCode: Int = 0,
) : RuntimeException(message)
