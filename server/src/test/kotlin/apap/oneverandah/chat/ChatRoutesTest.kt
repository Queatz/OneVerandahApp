package apap.oneverandah.chat

import apap.oneverandah.api.ChatResponse
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import apap.oneverandah.routes.chatRoutes
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ChatRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `chat endpoint validates sends and exposes history`() = testApplication {
        val repository = InMemoryChatRepository()
        val service = ChatService(
            repository = repository,
            aiClient = object : ChatAiClient {
                override suspend fun generate(request: ChatGenerationRequest) = GeneratedChatReply("Hello!")
            },
            residentDirectory = ResidentDirectory(emptyList()),
        )
        application {
            install(ContentNegotiation) { json() }
            routing { chatRoutes(service, SpeechService(RouteSpeechClient())) }
        }

        val invalid = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("{\"message\":\"   \",\"languageTag\":\"en\"}")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val sent = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            setBody("{\"message\":\"Hi\",\"languageTag\":\"en\"}")
        }
        assertEquals(HttpStatusCode.OK, sent.status)
        val response = json.decodeFromString<ChatResponse>(sent.bodyAsText())

        val history = client.get("/api/chat/${response.conversationId}")
        assertEquals(HttpStatusCode.OK, history.status)
        assertEquals(2, Regex("\\\"sender\\\"").findAll(history.bodyAsText()).count())
    }

    @Test
    fun `speech endpoint returns encoded audio`() = testApplication {
        val service = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(request: ChatGenerationRequest) = GeneratedChatReply("unused")
            },
            residentDirectory = ResidentDirectory(emptyList()),
        )
        application {
            install(ContentNegotiation) { json() }
            routing { chatRoutes(service, SpeechService(RouteSpeechClient())) }
        }

        val response = client.post("/api/chat/speech") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SpeechRequest("hello", "en")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val speech = json.decodeFromString<SpeechResponse>(response.bodyAsText())
        assertEquals("AQID", speech.audioBase64)
        assertEquals("audio/mpeg", speech.contentType)
    }
}

private class RouteSpeechClient : SpeechClient {
    override suspend fun designVoice() = "route-test-voice"

    override suspend fun synthesize(request: SpeechRequest, voiceId: String) =
        GeneratedSpeech(byteArrayOf(1, 2, 3), "audio/mpeg")
}