package apap.oneverandah.routes

import apap.oneverandah.api.ChatErrorResponse
import apap.oneverandah.api.ChatHistoryResponse
import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.SaveChatMessageRequest
import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.RenameStudySessionRequest
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildRequest
import apap.oneverandah.api.StudyBuildRetryRequest
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyExpressCheckRequest
import apap.oneverandah.api.StudyExpressGenerateRequest
import apap.oneverandah.api.StudyExamSceneRequest
import apap.oneverandah.api.StudyExamScoreRequest
import apap.oneverandah.api.StudyListenCheckRequest
import apap.oneverandah.api.StudyPlayScenariosRequest
import apap.oneverandah.api.StudyPlayTurnRequest
import apap.oneverandah.api.StudyWriteCheckRequest
import apap.oneverandah.api.StudySentenceImageRequest
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudyTranslateRequest
import apap.oneverandah.api.StudyStoryCharactersRequest
import apap.oneverandah.api.StudyWordImageRequest
import apap.oneverandah.api.StudyWordPlaygroundRequest
import apap.oneverandah.api.ChatResponse
import apap.oneverandah.chat.ChatService
import apap.oneverandah.chat.QwenAsrRelay
import apap.oneverandah.chat.SavedMessagesService
import apap.oneverandah.chat.SpeechService
import apap.oneverandah.chat.StudyService
import apap.oneverandah.chat.StudyBuildService
import apap.oneverandah.chat.StudySessionsService
import apap.oneverandah.chat.UnauthorizedDeviceException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.writeStringUtf8
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

const val DEVICE_TOKEN_HEADER = "X-Device-Token"

private val chatStreamJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

private val studyPayloadJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

private val ndjsonContentType = ContentType("application", "x-ndjson")

fun Route.chatRoutes(
    chatService: ChatService,
    speechService: SpeechService,
    savedMessagesService: SavedMessagesService? = null,
    studyService: StudyService? = null,
    studySessionsService: StudySessionsService? = null,
    studyBuildService: StudyBuildService? = null,
) {
    post("/api/chat") {
        val request = call.receive<ChatRequest>()
        if (request.normalizedOrNull() == null) {
            call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Message must not be blank"))
            return@post
        }
        val accept = call.request.header(HttpHeaders.Accept).orEmpty()
        val wantsStream = accept.contains("application/x-ndjson", ignoreCase = true)
        if (!wantsStream) {
            call.respond(chatService.send(request))
            return@post
        }

        // NDJSON stream: optional partial line(s), then a final non-partial ChatResponse.
        call.respondTextWriter(contentType = ndjsonContentType) {
            suspend fun writeEvent(response: ChatResponse) {
                appendLine(chatStreamJson.encodeToString(ChatResponse.serializer(), response))
                flush()
            }

            val final = chatService.send(request) { partial ->
                writeEvent(partial)
            }
            writeEvent(final)
        }
    }

    post("/api/chat/speech") {
        val speech = speechService.synthesize(call.receive<SpeechRequest>())
        if (speech == null) {
            call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Speech text must not be blank"))
            return@post
        }
        call.respond(
            SpeechResponse(
                audioBase64 = Base64.getEncoder().encodeToString(speech.bytes),
                contentType = speech.contentType,
                words = speech.words,
            ),
        )
    }

    if (studyService != null) {
        post("/api/chat/study") {
            val request = call.receive<StudySessionRequest>().normalizedOrNull()
            if (request == null) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study text must not be blank"))
                return@post
            }
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val session = try {
                when {
                    studyBuildService != null -> {
                        val saved = studyBuildService.create(
                            token,
                            StudyBuildRequest(
                                text = request.text,
                                languageTag = request.languageTag,
                                sourceMessageId = request.sourceMessageId,
                            ),
                        )
                        studyBuildService.awaitSkeleton(saved.id) ?: run {
                            call.respond(
                                HttpStatusCode.BadGateway,
                                ChatErrorResponse("Study session skeleton generation failed"),
                            )
                            return@post
                        }
                    }

                    studySessionsService != null -> {
                        studySessionsService.createFromStudyRequest(token, request) { studyService.skeleton(it) }
                    }

                    else -> withContext(NonCancellable) { studyService.skeleton(request) }
                }
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
                return@post
            }
            call.respondStudyPayload(session)
        }

        post("/api/chat/study/intent") {
            val request = call.receive<StudySessionRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study text must not be blank"))
                return@post
            }
            call.respond(studyService.intendedStudyText(request))
        }

        post("/api/chat/study/translate") {
            val request = call.receive<StudyTranslateRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study translation requires text and a target language")
                )
                return@post
            }
            call.respond(studyService.translateStudyText(request))
        }

        post("/api/chat/study/dissect") {
            val request = call.receive<StudyDissectSentenceRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study dissect requires non-blank text, sentence, and tokens"),
                )
                return@post
            }
            call.respond(studyService.dissectSentence(request))
        }

        post("/api/chat/study/playground") {
            val request = call.receive<StudyWordPlaygroundRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study playground requires a non-blank word and sentence"),
                )
                return@post
            }
            call.respond(studyService.playground(request))
        }

        post("/api/chat/study/characters") {
            val request = call.receive<StudyStoryCharactersRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study story characters require non-blank text"),
                )
                return@post
            }
            call.respond(studyService.storyCharacters(request))
        }

        post("/api/chat/study/sentence-image") {
            val request = call.receive<StudySentenceImageRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study sentence image requires non-blank sentence text"),
                )
                return@post
            }
            call.respond(studyService.sentenceImage(request))
        }

        post("/api/chat/study/word-image") {
            val request = call.receive<StudyWordImageRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study word image requires non-blank word text"),
                )
                return@post
            }
            call.respond(studyService.wordImage(request))
        }

        post("/api/chat/study/play/scenarios") {
            val request = call.receive<StudyPlayScenariosRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study play scenarios require non-blank text"),
                )
                return@post
            }
            call.respond(studyService.playScenarios(request))
        }

        post("/api/chat/study/play/turn") {
            val request = call.receive<StudyPlayTurnRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study play turn requires non-blank text, scenario, and user message"),
                )
                return@post
            }
            call.respond(studyService.playTurn(request))
        }

        post("/api/chat/study/exam/scene") {
            val request = call.receiveStudyPayload<StudyExamSceneRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study exam scene requires non-blank text and at least one sentence"),
                )
                return@post
            }
            call.respondStudyPayload(studyService.examScene(request))
        }

        post("/api/chat/study/write/check") {
            val request = call.receive<StudyWriteCheckRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study write check requires non-blank expected and attempt text"),
                )
                return@post
            }
            call.respond(studyService.writeCheck(request))
        }

        post("/api/chat/study/express/check") {
            val request = call.receive<StudyExpressCheckRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study express check requires non-blank meaning and attempt text"),
                )
                return@post
            }
            call.respond(studyService.expressCheck(request))
        }

        post("/api/chat/study/listen/check") {
            val request = call.receive<StudyListenCheckRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study listen check requires non-blank source, expected meaning, and attempt text"),
                )
                return@post
            }
            call.respond(studyService.listenCheck(request))
        }

        post("/api/chat/study/express/generate") {
            val request = call.receive<StudyExpressGenerateRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study express generate requires non-blank source text"),
                )
                return@post
            }
            call.respond(studyService.expressGenerate(request))
        }

        get("/api/chat/study/playground/image/{imageId}") {
            val imageId = call.parameters["imageId"]?.trim().orEmpty()
            if (imageId.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Playground image id is required"))
                return@get
            }
            val image = studyService.playgroundImage(imageId)
            if (image == null) {
                call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Playground image not found"))
                return@get
            }
            call.respond(image)
        }
    }

    if (studyService != null && studySessionsService != null) {
        post("/api/chat/study/exam/score") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val request = call.receiveStudyPayload<StudyExamScoreRequest>()
            val normalized = request.normalizedOrNull()
            if (normalized == null) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study exam session id is required"))
                return@post
            }
            try {
                val saved = studySessionsService.get(token, normalized.sessionId)
                if (saved == null) {
                    call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Study session not found"))
                    return@post
                }
                val scene = saved.session.examScene
                if (scene == null) {
                    call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study exam scene has not been saved"))
                    return@post
                }
                val result = studyService.scoreExam(
                    scene = scene,
                    request = normalized,
                    textLanguageTag = saved.textLanguageTag,
                    languageTag = saved.languageTag,
                )
                val updated = studySessionsService.saveExamGrade(token, saved.id, result.grade)
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Study session not found"))
                    return@post
                }
                call.respondStudyPayload(result)
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }
    }

    // Static study-session paths before /{conversationId}.
    if (studySessionsService != null) {
        if (studyBuildService != null) {
            post("/api/chat/study/sessions/build") {
                val token = call.request.header(DEVICE_TOKEN_HEADER)
                val request = call.receiveStudyPayload<StudyBuildRequest>()
                if (request.normalizedOrNull() == null) {
                    call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study build requires non-blank text"))
                    return@post
                }
                try {
                    val created = studyBuildService.create(token, request)
                    call.respondStudyPayload(created, HttpStatusCode.Accepted)
                } catch (_: UnauthorizedDeviceException) {
                    call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
                }
            }
        }

        get("/api/chat/study/sessions/community") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val cursor = call.request.queryParameters["cursor"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                ?: StudySessionsService.DEFAULT_COMMUNITY_PAGE_SIZE
            try {
                val page = withTimeoutOrNull(STUDY_SESSION_LIST_TIMEOUT_MILLIS) {
                    studySessionsService.listCommunity(token, cursor, limit)
                }
                if (page == null) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ChatErrorResponse("Study sessions request timed out"))
                } else {
                    call.respond(page)
                }
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }

        get("/api/chat/study/sessions") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            try {
                val sessions = withTimeoutOrNull(STUDY_SESSION_LIST_TIMEOUT_MILLIS) {
                    studySessionsService.list(token)
                }
                if (sessions == null) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ChatErrorResponse("Study sessions request timed out"))
                } else {
                    call.respond(sessions)
                }
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }

        if (studyBuildService != null) {
            get("/api/chat/study/sessions/{id}/events") {
                val token = call.request.header(DEVICE_TOKEN_HEADER)
                val id = call.parameters["id"]?.trim().orEmpty()
                if (id.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study session id is required"))
                    return@get
                }
                val accessible = try {
                    studySessionsService.get(token, id)
                } catch (_: UnauthorizedDeviceException) {
                    call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
                    return@get
                }
                if (accessible == null) {
                    call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Study session not found"))
                    return@get
                }

                call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                call.response.headers.append("X-Accel-Buffering", "no")
                val eventFlow = studyBuildService.events(id)
                call.respondBytesWriter(contentType = ndjsonContentType) {
                    coroutineScope {
                        val channel = Channel<StudyBuildEvent>(Channel.BUFFERED)
                        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                            eventFlow.collect { channel.send(it) }
                        }

                        suspend fun writeEvent(event: StudyBuildEvent) {
                            val viewerEvent = when (event) {
                                is StudyBuildEvent.Snapshot -> event.copy(
                                    session = event.session.copy(examGrade = accessible.examGrade),
                                )
                                else -> event
                            }
                            writeStringUtf8(
                                studyPayloadJson.encodeToString(StudyBuildEvent.serializer(), viewerEvent) + "\n",
                            )
                            flush()
                        }
                        try {
                            val snapshot = studyBuildService.ensureBuilding(id)
                            writeEvent(StudyBuildEvent.Snapshot(snapshot ?: accessible))
                            val terminal = snapshot?.build?.status == StudyBuildStatus.COMPLETE ||
                                    snapshot?.build?.status == StudyBuildStatus.PARTIAL
                            if (snapshot == null || terminal) {
                                writeEvent(StudyBuildEvent.Done)
                                return@coroutineScope
                            }
                            while (true) {
                                val event = withTimeoutOrNull(STUDY_BUILD_HEARTBEAT_MILLIS) { channel.receive() }
                                if (event == null) {
                                    writeEvent(StudyBuildEvent.Heartbeat)
                                } else {
                                    writeEvent(event)
                                    if (event == StudyBuildEvent.Done) return@coroutineScope
                                }
                            }
                        } finally {
                            collector.cancelAndJoin()
                            channel.cancel()
                        }
                    }
                }
            }

            post("/api/chat/study/sessions/{id}/build/retry") {
                val token = call.request.header(DEVICE_TOKEN_HEADER)
                val id = call.parameters["id"]?.trim().orEmpty()
                if (id.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study session id is required"))
                    return@post
                }
                val request = call.receiveStudyPayload<StudyBuildRetryRequest>()
                try {
                    val state = studyBuildService.retry(token, id, request)
                    if (state == null) {
                        call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Study session not found"))
                        return@post
                    }
                    call.respondStudyPayload(state)
                } catch (_: UnauthorizedDeviceException) {
                    call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
                }
            }
        }

        get("/api/chat/study/sessions/{id}") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val id = call.parameters["id"]?.trim().orEmpty()
            if (id.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study session id is required"))
                return@get
            }
            try {
                val session = studySessionsService.get(token, id)
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Study session not found"))
                    return@get
                }
                call.respondStudyPayload(session)
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }

        post("/api/chat/study/sessions") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val request = call.receiveStudyPayload<SaveStudySessionRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ChatErrorResponse("Study session requires non-blank text and at least one sentence"),
                )
                return@post
            }
            try {
                call.respond(studySessionsService.save(token, request))
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }

        put("/api/chat/study/sessions/{id}") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val id = call.parameters["id"]?.trim().orEmpty()
            if (id.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study session id is required"))
                return@put
            }
            val request = call.receiveStudyPayload<RenameStudySessionRequest>()
            val normalized = request.normalizedOrNull()
            if (normalized == null) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study session name must not be blank"))
                return@put
            }
            try {
                val renamed = studySessionsService.rename(token, id, normalized.name)
                if (renamed == null) {
                    call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Study session not found"))
                    return@put
                }
                call.respondStudyPayload(renamed)
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }

        delete("/api/chat/study/sessions/{id}") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val id = call.parameters["id"]?.trim().orEmpty()
            if (id.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study session id is required"))
                return@delete
            }
            try {
                val deleted = studySessionsService.delete(token, id)
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Study session not found"))
                    return@delete
                }
                call.respond(HttpStatusCode.NoContent)
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }
    }

    // Register static /saved paths before /{conversationId} so "saved" is not captured as an id.
    if (savedMessagesService != null) {
        get("/api/chat/saved") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            try {
                call.respond(savedMessagesService.list(token))
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }

        post("/api/chat/saved") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val request = call.receive<SaveChatMessageRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Message text must not be blank"))
                return@post
            }
            try {
                call.respond(savedMessagesService.save(token, request))
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }

        delete("/api/chat/saved/{id}") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val id = call.parameters["id"]?.trim().orEmpty()
            if (id.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Saved message id is required"))
                return@delete
            }
            try {
                val deleted = savedMessagesService.delete(token, id)
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, ChatErrorResponse("Saved message not found"))
                    return@delete
                }
                call.respond(HttpStatusCode.NoContent)
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }
    }

    get("/api/chat/{conversationId}") {
        val conversationId = call.parameters["conversationId"]?.trim().orEmpty()
        if (conversationId.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Conversation id is required"))
            return@get
        }
        call.respond(ChatHistoryResponse(conversationId, chatService.history(conversationId)))
    }
}

private suspend inline fun <reified T> ApplicationCall.receiveStudyPayload(): T {
    val text = receiveText()
    return withContext(Dispatchers.Default) { studyPayloadJson.decodeFromString(text) }
}

private suspend inline fun <reified T> ApplicationCall.respondStudyPayload(
    payload: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val text = withContext(Dispatchers.Default) { studyPayloadJson.encodeToString(payload) }
    respondText(text, ContentType.Application.Json, status)
}

private const val STUDY_BUILD_HEARTBEAT_MILLIS = 15_000L
private const val STUDY_SESSION_LIST_TIMEOUT_MILLIS = 15_000L

fun Route.asrRoutes(asrRelay: QwenAsrRelay) {
    webSocket("/api/chat/asr") {
        asrRelay.relay(this)
    }
}
