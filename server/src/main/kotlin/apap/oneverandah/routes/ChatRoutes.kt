package apap.oneverandah.routes

import apap.oneverandah.api.ChatErrorResponse
import apap.oneverandah.api.ChatHistoryResponse
import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.SaveChatMessageRequest
import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudySentenceImageRequest
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudyWordPlaygroundRequest
import apap.oneverandah.chat.ChatService
import apap.oneverandah.chat.QwenAsrRelay
import apap.oneverandah.chat.SavedMessagesService
import apap.oneverandah.chat.SpeechService
import apap.oneverandah.chat.StudyService
import apap.oneverandah.chat.StudySessionsService
import apap.oneverandah.chat.UnauthorizedDeviceException
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import java.util.Base64

const val DEVICE_TOKEN_HEADER = "X-Device-Token"

fun Route.chatRoutes(
    chatService: ChatService,
    speechService: SpeechService,
    savedMessagesService: SavedMessagesService? = null,
    studyService: StudyService? = null,
    studySessionsService: StudySessionsService? = null,
) {
    post("/api/chat") {
        val request = call.receive<ChatRequest>()
        if (request.normalizedOrNull() == null) {
            call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Message must not be blank"))
            return@post
        }
        call.respond(chatService.send(request))
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
            val request = call.receive<StudySessionRequest>()
            if (request.normalizedOrNull() == null) {
                call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Study text must not be blank"))
                return@post
            }
            call.respond(studyService.skeleton(request))
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

    // Static study-session paths before /{conversationId}.
    if (studySessionsService != null) {
        get("/api/chat/study/sessions") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            try {
                call.respond(studySessionsService.list(token))
            } catch (_: UnauthorizedDeviceException) {
                call.respond(HttpStatusCode.Unauthorized, ChatErrorResponse("Valid device token required"))
            }
        }

        post("/api/chat/study/sessions") {
            val token = call.request.header(DEVICE_TOKEN_HEADER)
            val request = call.receive<SaveStudySessionRequest>()
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

fun Route.asrRoutes(asrRelay: QwenAsrRelay) {
    webSocket("/api/chat/asr") {
        asrRelay.relay(this)
    }
}
