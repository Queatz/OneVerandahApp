package apap.oneverandah.routes

import apap.oneverandah.api.ChatErrorResponse
import apap.oneverandah.api.ChatHistoryResponse
import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import apap.oneverandah.chat.ChatService
import apap.oneverandah.chat.SpeechService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.Base64

fun Route.chatRoutes(chatService: ChatService, speechService: SpeechService) {
    post("/api/chat") {
        val request = call.receive<ChatRequest>()
        if (request.normalizedOrNull() == null) {
            call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Message must not be blank"))
            return@post
        }
        call.respond(chatService.send(request))
    }

    get("/api/chat/{conversationId}") {
        val conversationId = call.parameters["conversationId"]?.trim().orEmpty()
        if (conversationId.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, ChatErrorResponse("Conversation id is required"))
            return@get
        }
        call.respond(ChatHistoryResponse(conversationId, chatService.history(conversationId)))
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
            ),
        )
    }
}