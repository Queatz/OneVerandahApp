package apap.oneverandah.chat

import apap.oneverandah.api.CommunityStudySessionsResponse
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.SavedStudySessionSummary
import apap.oneverandah.api.SavedStudySessionsResponse
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

class StudySessionsRoutesTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    @Test
    fun `study session endpoints require device token and isolate devices`() = testApplication {
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val studySessionsService = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
        )
        val chatService = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(
                    request: ChatGenerationRequest,
                    onPartial: suspend (GeneratedChatReply) -> Unit,
                ) = GeneratedChatReply("unused")
            },
            residentDirectory = ResidentDirectory(emptyList()),
        )
        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                chatRoutes(
                    chatService,
                    SpeechService(RouteSpeechClient()),
                    studySessionsService = studySessionsService,
                )
            }
        }

        val unauthorized = client.get("/api/chat/study/sessions")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

        val tokenA = deviceService.issueToken().deviceToken
        val tokenB = deviceService.issueToken().deviceToken

        val created = client.post("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, tokenA)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceMessageId":"m1",
                  "languageTag":"en",
                  "session":{
                    "text":"Xin chào.",
                    "textLanguageTag":"vi",
                    "sentences":[
                      {
                        "text":"Xin chào.",
                        "tokens":[{"text":"Xin","status":"PENDING"},{"text":"chào","status":"PENDING"}]
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val createdBody = created.bodyAsText()
        assertTrue("\"session\"" !in createdBody)
        val saved = json.decodeFromString(SavedStudySessionSummary.serializer(), createdBody)
        assertEquals("Xin chào.", saved.text)
        assertEquals("m1", saved.sourceMessageId)
        assertEquals("vi", saved.textLanguageTag)

        val listA = client.get("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.OK, listA.status)
        val listBody = listA.bodyAsText()
        assertTrue("\"session\"" !in listBody)
        val itemsA = json.decodeFromString(SavedStudySessionsResponse.serializer(), listBody)
        assertEquals(1, itemsA.items.size)
        assertEquals(saved.id, itemsA.items.single().id)

        val listB = client.get("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, tokenB)
        }
        assertEquals(HttpStatusCode.OK, listB.status)
        assertTrue(json.decodeFromString(SavedStudySessionsResponse.serializer(), listB.bodyAsText()).items.isEmpty())

        val unauthorizedGet = client.get("/api/chat/study/sessions/${saved.id}")
        assertEquals(HttpStatusCode.Unauthorized, unauthorizedGet.status)

        val shared = client.get("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenB)
        }
        assertEquals(HttpStatusCode.OK, shared.status)
        val fetched = json.decodeFromString(SavedStudySession.serializer(), shared.bodyAsText())
        assertEquals(saved.id, fetched.id)
        assertEquals("Xin chào.", fetched.text)
        assertEquals("Xin chào.", fetched.session.text)
        assertEquals(1, fetched.session.sentences.size)

        val missing = client.get("/api/chat/study/sessions/missing-id") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)

        val foreignDelete = client.delete("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenB)
        }
        assertEquals(HttpStatusCode.NotFound, foreignDelete.status)

        val ownedDelete = client.delete("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.NoContent, ownedDelete.status)
    }

    @Test
    fun `community study sessions exclude the caller and page`() = testApplication {
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val studySessionsService = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
            clock = incrementalClock(5_000L),
        )
        val chatService = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(
                    request: ChatGenerationRequest,
                    onPartial: suspend (GeneratedChatReply) -> Unit,
                ) = GeneratedChatReply("unused")
            },
            residentDirectory = ResidentDirectory(emptyList()),
        )
        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                chatRoutes(
                    chatService,
                    SpeechService(RouteSpeechClient()),
                    studySessionsService = studySessionsService,
                )
            }
        }

        val unauthorized = client.get("/api/chat/study/sessions/community")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

        val tokenA = deviceService.issueToken().deviceToken
        val tokenB = deviceService.issueToken().deviceToken

        client.post("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, tokenA)
            contentType(ContentType.Application.Json)
            setBody(sessionBody("mine-only"))
        }
        client.post("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, tokenB)
            contentType(ContentType.Application.Json)
            setBody(sessionBody("community-one"))
        }
        client.post("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, tokenB)
            contentType(ContentType.Application.Json)
            setBody(sessionBody("community-two"))
        }

        val firstPage = client.get("/api/chat/study/sessions/community?limit=1") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.OK, firstPage.status)
        val firstPageBody = firstPage.bodyAsText()
        assertTrue("\"session\"" !in firstPageBody)
        val page = json.decodeFromString(CommunityStudySessionsResponse.serializer(), firstPageBody)
        assertEquals(1, page.items.size)
        assertEquals("community-two", page.items.single().text)
        assertTrue(!page.nextCursor.isNullOrBlank())

        val secondPage = client.get("/api/chat/study/sessions/community?limit=1&cursor=${page.nextCursor}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.OK, secondPage.status)
        val next = json.decodeFromString(CommunityStudySessionsResponse.serializer(), secondPage.bodyAsText())
        assertEquals(listOf("community-one"), next.items.map { it.text })
        assertTrue(next.items.none { it.text == "mine-only" })
    }

    private fun sessionBody(text: String) = """
        {
          "languageTag":"en",
          "session":{
            "text":"$text",
            "textLanguageTag":"en",
            "sentences":[
              {
                "text":"$text",
                "tokens":[{"text":"${text.split(" ").first()}","status":"PENDING"}]
              }
            ]
          }
        }
    """.trimIndent()

    private fun incrementalClock(start: Long): () -> Long {
        var value = start
        return { value++ }
    }

    @Test
    fun `blank study session payload is rejected`() = testApplication {
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val studySessionsService = StudySessionsService(
            repository = InMemoryStudySessionsRepository(),
            deviceService = deviceService,
        )
        val chatService = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(
                    request: ChatGenerationRequest,
                    onPartial: suspend (GeneratedChatReply) -> Unit,
                ) = GeneratedChatReply("unused")
            },
            residentDirectory = ResidentDirectory(emptyList()),
        )
        application {
            install(ContentNegotiation) { json(json) }
            routing {
                chatRoutes(
                    chatService,
                    SpeechService(RouteSpeechClient()),
                    studySessionsService = studySessionsService,
                )
            }
        }

        val token = deviceService.issueToken().deviceToken
        val response = client.post("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, token)
            contentType(ContentType.Application.Json)
            setBody(
                """{"languageTag":"en","session":{"text":"   ","textLanguageTag":"en","sentences":[]}}""",
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
