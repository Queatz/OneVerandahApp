package apap.oneverandah.api

import apap.oneverandah.state.DeviceTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.preparePost
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

object ChatApi {
    private const val DEVICE_TOKEN_HEADER = "X-Device-Token"
    private const val REQUEST_TIMEOUT_MILLIS = 180_000L
    private const val REQUEST_SLOT_WAIT_TIMEOUT_MILLIS = 60_000L
    private const val STUDY_SESSION_READ_TIMEOUT_MILLIS = 60_000L
    private const val REQUEST_TIMEOUT_MESSAGE = "Chat request timed out"
    private const val STREAM_IDLE_TIMEOUT_MILLIS = 60_000L
    private val STUDY_EVENT_RECONNECT_BACKOFF_MILLIS = listOf(1_000L, 2_000L, 5_000L, 10_000L)

    private val requestSlots = Semaphore(permits = 4)
    private val client by lazy {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000L
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
        }
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    suspend fun createDeviceToken(): String = apiRequest(
        request = { client.post("${ImageApi.apiBaseUrl}/api/device") },
        decode = { json.decodeFromString(DeviceTokenResponse.serializer(), it).deviceToken },
    )

    suspend fun listSaved(): SavedChatMessagesResponse {
        val token = DeviceTokenStore.ensureToken()
        return apiRequest(
            request = {
                client.get(chatApiUrl("/saved")) {
                    header(DEVICE_TOKEN_HEADER, token)
                }
            },
            decode = { json.decodeFromString(SavedChatMessagesResponse.serializer(), it) },
        )
    }

    suspend fun saveMessage(request: SaveChatMessageRequest): SavedChatMessage {
        val token = DeviceTokenStore.ensureToken()
        return apiRequest(
            request = {
                client.post(chatApiUrl("/saved")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(SaveChatMessageRequest.serializer(), request))
                }
            },
            decode = { json.decodeFromString(SavedChatMessage.serializer(), it) },
        )
    }

    suspend fun deleteSaved(id: String) {
        val token = DeviceTokenStore.ensureToken()
        apiRequest(
            request = {
                client.delete(chatApiUrl("/saved/$id")) {
                    header(DEVICE_TOKEN_HEADER, token)
                }
            },
            decode = {},
        )
    }

    /**
     * Send a chat turn.
     *
     * Requests an NDJSON stream so a fast short reply can paint via [onPartial]
     * before the merged final response is returned.
     */
    suspend fun send(
        request: ChatRequest,
        onPartial: (suspend (ChatResponse) -> Unit)? = null,
    ): ChatResponse {
        val ndjson = ContentType("application", "x-ndjson")
        return withRequestSlot {
            client.preparePost(chatApiUrl()) {
                contentType(ContentType.Application.Json)
                accept(ndjson)
                header(HttpHeaders.Accept, "application/x-ndjson")
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
                setBody(json.encodeToString(ChatRequest.serializer(), request))
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val text = response.bodyAsText()
                    throw ChatApiException(errorMessage(text, response.status.value), response.status.value)
                }
                val channel = response.bodyAsChannel()
                var finalResponse: ChatResponse? = null
                while (!channel.isClosedForRead) {
                    val line = withTimeoutOrNull(STREAM_IDLE_TIMEOUT_MILLIS.milliseconds) {
                        channel.readLine()
                    } ?: break
                    if (line.isBlank()) continue
                    val event = json.decodeFromString(ChatResponse.serializer(), line)
                    if (event.partial) {
                        onPartial?.invoke(event)
                    } else {
                        finalResponse = event
                        break
                    }
                }
                finalResponse
                    ?: throw ChatApiException("Chat stream ended without a final response")
            }
        }
    }

    suspend fun history(conversationId: String): ChatHistoryResponse = apiRequest(
        request = { client.get(chatApiUrl("/$conversationId")) },
        decode = { json.decodeFromString(ChatHistoryResponse.serializer(), it) },
    )

    suspend fun speech(request: SpeechRequest): SpeechResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/speech")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SpeechRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(SpeechResponse.serializer(), it) },
    )

    suspend fun speechDataUrl(request: SpeechRequest): String {
        val speech = speech(request)
        return "data:${speech.contentType};base64,${speech.audioBase64}"
    }

    /** Fast study skeleton: language + sentences + tokens (meanings still pending). */
    suspend fun study(request: StudySessionRequest): StudySessionResponse =
        withContext(NonCancellable) {
            val token = DeviceTokenStore.ensureToken()
            executeApiRequest(
                request = {
                    client.post(chatApiUrl("/study")) {
                        header(DEVICE_TOKEN_HEADER, token)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(StudySessionRequest.serializer(), request))
                    }
                },
                decode = { json.decodeFromString(StudySessionResponse.serializer(), it) },
                timeoutMillis = REQUEST_TIMEOUT_MILLIS,
            )
        }

    /** Parse-mode prep: generate the study text the learner asked for. */
    suspend fun studyIntent(request: StudySessionRequest): StudyIntentResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/intent")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudySessionRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyIntentResponse.serializer(), it) },
    )

    /** Translate existing lesson text into the requested study language. */
    suspend fun studyTranslate(request: StudyTranslateRequest): StudyIntentResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/translate")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyTranslateRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyIntentResponse.serializer(), it) },
    )

    /** Full dissection for one skeleton sentence. */
    suspend fun studyDissect(request: StudyDissectSentenceRequest): StudyDissectSentenceResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/dissect")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyDissectSentenceRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyDissectSentenceResponse.serializer(), it) },
    )

    /** Judge a learner sentence using a dissected word and start comic image generation. */
    suspend fun studyPlayground(request: StudyWordPlaygroundRequest): StudyWordPlaygroundResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/playground")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyWordPlaygroundRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyWordPlaygroundResponse.serializer(), it) },
    )

    /** Poll async playground comic image status. */
    suspend fun studyPlaygroundImage(imageId: String): StudyPlaygroundImageResponse = apiRequest(
        request = { client.get(chatApiUrl("/study/playground/image/$imageId")) },
        decode = { json.decodeFromString(StudyPlaygroundImageResponse.serializer(), it) },
    )

    /**
     * Design main/secondary story characters and start reference-portrait jobs.
     * Persist the returned cast on the study session; poll each imageId via [studyImage].
     */
    suspend fun studyStoryCharacters(request: StudyStoryCharactersRequest): StudyStoryCharactersResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/characters")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyStoryCharactersRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyStoryCharactersResponse.serializer(), it) },
    )

    /** Start async expressive visual for one Study-tab sentence. */
    suspend fun studySentenceImage(request: StudySentenceImageRequest): StudySentenceImageResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/sentence-image")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudySentenceImageRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudySentenceImageResponse.serializer(), it) },
    )

    /** Start async expressive visual for one dissected Study-tab word/phrase. */
    suspend fun studyWordImage(request: StudyWordImageRequest): StudyWordImageResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/word-image")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyWordImageRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyWordImageResponse.serializer(), it) },
    )

    /** Generate four related Play-tab scenario starters (covers are async image jobs). */
    suspend fun studyPlayScenarios(request: StudyPlayScenariosRequest): StudyPlayScenariosResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/play/scenarios")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyPlayScenariosRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyPlayScenariosResponse.serializer(), it) },
    )

    /** Continue a Play-tab conversation after the learner sends a message. */
    suspend fun studyPlayTurn(request: StudyPlayTurnRequest): StudyPlayTurnResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/play/turn")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyPlayTurnRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyPlayTurnResponse.serializer(), it) },
    )

    suspend fun studyExamScene(request: StudyExamSceneRequest): StudyExamScene = apiRequest(
        request = {
            client.post(chatApiUrl("/study/exam/scene")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyExamSceneRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyExamScene.serializer(), it) },
    )

    suspend fun studyExamScore(request: StudyExamScoreRequest): StudyExamScoreResponse {
        val token = DeviceTokenStore.ensureToken()
        return apiRequest(
            request = {
                client.post(chatApiUrl("/study/exam/score")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(StudyExamScoreRequest.serializer(), request))
                }
            },
            decode = { json.decodeFromString(StudyExamScoreResponse.serializer(), it) },
        )
    }

    suspend fun studyWriteCheck(request: StudyWriteCheckRequest): StudyWriteCheckResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/write/check")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyWriteCheckRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyWriteCheckResponse.serializer(), it) },
    )

    /** Judge an Express-tab production attempt (meaning match, not exact wording). */
    suspend fun studyExpressCheck(request: StudyExpressCheckRequest): StudyExpressCheckResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/express/check")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyExpressCheckRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyExpressCheckResponse.serializer(), it) },
    )

    /** Judge a Listen-tab translation in the learner's language. */
    suspend fun studyListenCheck(request: StudyListenCheckRequest): StudyListenCheckResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/listen/check")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyListenCheckRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyListenCheckResponse.serializer(), it) },
    )

    /** Generate a fresh batch of Express practice sentences from related vocabulary. */
    suspend fun studyExpressGenerate(request: StudyExpressGenerateRequest): StudyExpressGenerateResponse = apiRequest(
        request = {
            client.post(chatApiUrl("/study/express/generate")) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyExpressGenerateRequest.serializer(), request))
            }
        },
        decode = { json.decodeFromString(StudyExpressGenerateResponse.serializer(), it) },
    )

    /** Poll async study image status (playground comics, portraits, and sentence visuals). */
    suspend fun studyImage(imageId: String): StudyPlaygroundImageResponse = studyPlaygroundImage(imageId)

    suspend fun listStudySessions(): SavedStudySessionsResponse {
        val token = DeviceTokenStore.ensureToken()
        return apiReadRequest(
            request = {
                client.get(chatApiUrl("/study/sessions")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    timeout { requestTimeoutMillis = STUDY_SESSION_READ_TIMEOUT_MILLIS }
                }
            },
            decode = { json.decodeFromString(SavedStudySessionsResponse.serializer(), it) },
        )
    }

    suspend fun getStudySession(id: String): SavedStudySession {
        val token = DeviceTokenStore.ensureToken()
        return apiReadRequest(
            request = {
                client.get(chatApiUrl("/study/sessions/$id")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    timeout { requestTimeoutMillis = STUDY_SESSION_READ_TIMEOUT_MILLIS }
                }
            },
            decode = { json.decodeFromString(SavedStudySession.serializer(), it) },
        )
    }

    suspend fun createStudySession(request: StudyBuildRequest): SavedStudySession {
        val token = DeviceTokenStore.ensureToken()
        return apiRequest(
            request = {
                client.post(chatApiUrl("/study/sessions/build")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(StudyBuildRequest.serializer(), request))
                }
            },
            decode = { json.decodeFromString(SavedStudySession.serializer(), it) },
        )
    }

    suspend fun retryStudyBuild(id: String, elements: List<StudyBuildElement> = emptyList()): StudyBuildState {
        val token = DeviceTokenStore.ensureToken()
        val request = StudyBuildRetryRequest(elements)
        return apiRequest(
            request = {
                client.post(chatApiUrl("/study/sessions/$id/build/retry")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(StudyBuildRetryRequest.serializer(), request))
                }
            },
            decode = { json.decodeFromString(StudyBuildState.serializer(), it) },
        )
    }

    suspend fun studySessionEvents(
        id: String,
        onEvent: suspend (StudyBuildEvent) -> Unit,
    ) {
        val token = DeviceTokenStore.ensureToken()
        var reconnectAttempt = 0
        while (true) {
            var done = false
            try {
                client.prepareGet(chatApiUrl("/study/sessions/$id/events")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        val body = response.bodyAsText()
                        throw ChatApiException(errorMessage(body, response.status.value), response.status.value)
                    }
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = withTimeoutOrNull(STREAM_IDLE_TIMEOUT_MILLIS.milliseconds) {
                            channel.readLine()
                        } ?: throw ChatApiException("Study event stream idle timeout")
                        if (line.isBlank()) continue
                        val event = decodeStudyBuildEventLine(line)
                        onEvent(event)
                        reconnectAttempt = 0
                        if (event == StudyBuildEvent.Done) {
                            done = true
                            break
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ChatApiException) {
                if (error.statusCode != 0) throw error
            } catch (_: Exception) {
                // Reconnect below; every connection begins with a fresh snapshot.
            }
            if (done) return
            val backoff = STUDY_EVENT_RECONNECT_BACKOFF_MILLIS[
                reconnectAttempt.coerceAtMost(STUDY_EVENT_RECONNECT_BACKOFF_MILLIS.lastIndex)
            ]
            delay(backoff.milliseconds)
            reconnectAttempt += 1
        }
    }

    internal fun decodeStudyBuildEventLine(line: String): StudyBuildEvent =
        json.decodeFromString(StudyBuildEvent.serializer(), line)

    suspend fun listCommunityStudySessions(
        cursor: String? = null,
        limit: Int = 20,
    ): CommunityStudySessionsResponse {
        val token = DeviceTokenStore.ensureToken()
        return apiReadRequest(
            request = {
                client.get(chatApiUrl("/study/sessions/community")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    parameter("limit", limit)
                    if (!cursor.isNullOrBlank()) parameter("cursor", cursor)
                    timeout { requestTimeoutMillis = STUDY_SESSION_READ_TIMEOUT_MILLIS }
                }
            },
            decode = { json.decodeFromString(CommunityStudySessionsResponse.serializer(), it) },
        )
    }

    suspend fun saveStudySession(request: SaveStudySessionRequest): SavedStudySessionSummary {
        return withContext(NonCancellable) {
            val token = DeviceTokenStore.ensureToken()
            executeApiRequest(
                request = {
                    client.post(chatApiUrl("/study/sessions")) {
                        header(DEVICE_TOKEN_HEADER, token)
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(SaveStudySessionRequest.serializer(), request))
                    }
                },
                decode = { json.decodeFromString(SavedStudySessionSummary.serializer(), it) },
                timeoutMillis = REQUEST_TIMEOUT_MILLIS,
            )
        }
    }

    suspend fun renameStudySession(id: String, name: String): SavedStudySessionSummary {
        val token = DeviceTokenStore.ensureToken()
        val request = RenameStudySessionRequest(name = name)
        return apiRequest(
            request = {
                client.put(chatApiUrl("/study/sessions/$id")) {
                    header(DEVICE_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(RenameStudySessionRequest.serializer(), request))
                }
            },
            decode = { json.decodeFromString(SavedStudySessionSummary.serializer(), it) },
        )
    }

    suspend fun deleteStudySession(id: String) {
        val token = DeviceTokenStore.ensureToken()
        apiRequest(
            request = {
                client.delete(chatApiUrl("/study/sessions/$id")) {
                    header(DEVICE_TOKEN_HEADER, token)
                }
            },
            decode = {},
        )
    }

    private suspend fun <T> apiRequest(
        request: suspend () -> io.ktor.client.statement.HttpResponse,
        decode: (String) -> T,
    ): T = withRequestSlot {
        executeApiRequest(request, decode, REQUEST_TIMEOUT_MILLIS)
    }

    private suspend fun <T> apiReadRequest(
        request: suspend () -> io.ktor.client.statement.HttpResponse,
        decode: (String) -> T,
    ): T {
        repeat(2) { attempt ->
            try {
                return executeApiRequest(request, decode, STUDY_SESSION_READ_TIMEOUT_MILLIS)
            } catch (error: ChatApiException) {
                if (error.message != REQUEST_TIMEOUT_MESSAGE || attempt == 1) throw error
            }
        }
        throw ChatApiException(REQUEST_TIMEOUT_MESSAGE)
    }

    private suspend fun <T> withRequestSlot(block: suspend () -> T): T {
        withTimeoutOrNull(REQUEST_SLOT_WAIT_TIMEOUT_MILLIS.milliseconds) {
            requestSlots.acquire()
        } ?: throw ChatApiException("Chat request queue timed out")
        try {
            return block()
        } finally {
            requestSlots.release()
        }
    }

    private suspend fun <T> executeApiRequest(
        request: suspend () -> io.ktor.client.statement.HttpResponse,
        decode: (String) -> T,
        timeoutMillis: Long,
    ): T {
        return try {
            withTimeoutOrNull(timeoutMillis.milliseconds) {
                val response = request()
                val text = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    throw ChatApiException(errorMessage(text, response.status.value), response.status.value)
                }
                decode(text)
            } ?: throw ChatApiException(REQUEST_TIMEOUT_MESSAGE)
        } catch (_: HttpRequestTimeoutException) {
            throw ChatApiException(REQUEST_TIMEOUT_MESSAGE)
        }
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
