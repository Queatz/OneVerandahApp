package apap.oneverandah.chat

import apap.oneverandah.api.SavedChatMessage
import apap.oneverandah.api.SavedChatMessagesResponse
import apap.oneverandah.routes.DEVICE_TOKEN_HEADER
import apap.oneverandah.routes.chatRoutes
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class SavedMessagesRoutesTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    @Test
    fun `saved endpoints require device token and isolate devices`() = testApplication {
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val savedService = SavedMessagesService(
            repository = InMemorySavedMessagesRepository(),
            deviceService = deviceService,
        )
        val chatService = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(request: ChatGenerationRequest) = GeneratedChatReply("unused")
            },
            residentDirectory = ResidentDirectory(emptyList()),
        )
        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                chatRoutes(chatService, SpeechService(RouteSpeechClient()), savedService)
            }
        }

        val unauthorized = client.get("/api/chat/saved")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

        val tokenA = deviceService.issueToken().deviceToken
        val tokenB = deviceService.issueToken().deviceToken

        val created = client.post("/api/chat/saved") {
            header(DEVICE_TOKEN_HEADER, tokenA)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceMessageId":"m1",
                  "sender":"AI",
                  "text":"Saved hello",
                  "createdAt":123,
                  "miniApp":{"type":"fact","factId":"f1","title":"T","body":"B","icon":"info"},
                  "followUpQuestions":["Next?"]
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val saved = json.decodeFromString(SavedChatMessage.serializer(), created.bodyAsText())
        assertEquals("Saved hello", saved.text)
        assertEquals("m1", saved.sourceMessageId)

        val listA = client.get("/api/chat/saved") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.OK, listA.status)
        val itemsA = json.decodeFromString(SavedChatMessagesResponse.serializer(), listA.bodyAsText())
        assertEquals(1, itemsA.items.size)
        assertEquals(saved.id, itemsA.items.single().id)

        val listB = client.get("/api/chat/saved") {
            header(DEVICE_TOKEN_HEADER, tokenB)
        }
        assertEquals(HttpStatusCode.OK, listB.status)
        assertTrue(json.decodeFromString(SavedChatMessagesResponse.serializer(), listB.bodyAsText()).items.isEmpty())

        val foreignDelete = client.delete("/api/chat/saved/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenB)
        }
        assertEquals(HttpStatusCode.NotFound, foreignDelete.status)

        val ownedDelete = client.delete("/api/chat/saved/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.NoContent, ownedDelete.status)
    }

    @Test
    fun `blank save payload is rejected`() = testApplication {
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val savedService = SavedMessagesService(
            repository = InMemorySavedMessagesRepository(),
            deviceService = deviceService,
        )
        val chatService = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(request: ChatGenerationRequest) = GeneratedChatReply("unused")
            },
            residentDirectory = ResidentDirectory(emptyList()),
        )
        application {
            install(ContentNegotiation) { json(json) }
            routing { chatRoutes(chatService, SpeechService(RouteSpeechClient()), savedService) }
        }

        val token = deviceService.issueToken().deviceToken
        val response = client.post("/api/chat/saved") {
            header(DEVICE_TOKEN_HEADER, token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"sender":"YOU","text":"   ","createdAt":1}""",
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
