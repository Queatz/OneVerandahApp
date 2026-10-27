package apap.oneverandah.chat

import apap.oneverandah.api.CommunityStudySessionsResponse
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.SavedStudySessionSummary
import apap.oneverandah.api.SavedStudySessionsResponse
import apap.oneverandah.api.SaveStudySessionRequest
import apap.oneverandah.api.StudyExamGrade
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudyExamSceneRequest
import apap.oneverandah.api.StudyExamScoreAnswer
import apap.oneverandah.api.StudyExamScoreRequest
import apap.oneverandah.api.StudyExamScoreResponse
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.routes.DEVICE_TOKEN_HEADER
import apap.oneverandah.routes.chatRoutes
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
        val studySessionsRepository = InMemoryStudySessionsRepository()
        val studySessionsService = StudySessionsService(
            repository = studySessionsRepository,
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
                  "examGrade":{"overall":5,"listening":5,"speaking":5,"feedback":"forged","scoredAt":1},
                  "session":{
                    "text":"Xin chào.",
                    "textLanguageTag":"vi",
                    "sentences":[
                      {
                        "text":"Xin chào.",
                        "imageUrl":" https://img/sentence ",
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
        assertTrue("\"sentenceImageUrls\":[\"https://img/sentence\"]" in createdBody)
        val saved = json.decodeFromString(SavedStudySessionSummary.serializer(), createdBody)
        assertEquals("Xin chào.", saved.text)
        assertEquals("Xin chào.", saved.name)
        assertEquals("m1", saved.sourceMessageId)
        assertEquals("vi", saved.textLanguageTag)
        assertEquals(null, saved.examGrade)

        val ownerGrade = StudyExamGrade(4, 5, 3, "Stored by the server.", 100L)
        studySessionsService.saveExamGrade(tokenA, saved.id, ownerGrade)

        val progressiveSave = client.post("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, tokenA)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceMessageId":"m1",
                  "languageTag":"en",
                  "examGrade":{"overall":1,"listening":1,"speaking":1,"feedback":"forged","scoredAt":2},
                  "session":{"text":"Xin chào.","textLanguageTag":"vi","sentences":[{"text":"Xin chào.","imageUrl":" https://img/sentence ","tokens":[]}]}
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, progressiveSave.status)
        assertEquals(
            ownerGrade,
            json.decodeFromString(SavedStudySessionSummary.serializer(), progressiveSave.bodyAsText()).examGrade,
        )

        val listA = client.get("/api/chat/study/sessions") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.OK, listA.status)
        val listBody = listA.bodyAsText()
        assertTrue("\"session\"" !in listBody)
        assertTrue("\"sentenceImageUrls\":[\"https://img/sentence\"]" in listBody)
        val itemsA = json.decodeFromString(SavedStudySessionsResponse.serializer(), listBody)
        assertEquals(1, itemsA.items.size)
        assertEquals(saved.id, itemsA.items.single().id)
        assertEquals(saved.name, itemsA.items.single().name)
        assertEquals(ownerGrade, itemsA.items.single().examGrade)

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
        assertEquals(saved.name, fetched.name)
        assertEquals("Xin chào.", fetched.session.text)
        assertEquals(1, fetched.session.sentences.size)
        assertEquals(null, fetched.examGrade)

        val ownerGet = client.get("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(ownerGrade, json.decodeFromString(SavedStudySession.serializer(), ownerGet.bodyAsText()).examGrade)

        val missing = client.get("/api/chat/study/sessions/missing-id") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)

        val foreignDelete = client.delete("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenB)
        }
        assertEquals(HttpStatusCode.NotFound, foreignDelete.status)

        val foreignRename = client.put("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenB)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Foreign name"}""")
        }
        assertEquals(HttpStatusCode.NotFound, foreignRename.status)

        val blankRename = client.put("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blankRename.status)

        val ownedRename = client.put("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Greetings in Vietnamese"}""")
        }
        assertEquals(HttpStatusCode.OK, ownedRename.status)
        val renamed = json.decodeFromString(SavedStudySessionSummary.serializer(), ownedRename.bodyAsText())
        assertEquals("Greetings in Vietnamese", renamed.name)

        val fetchedAfterRename = client.get("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.OK, fetchedAfterRename.status)
        assertEquals(
            "Greetings in Vietnamese",
            json.decodeFromString(SavedStudySession.serializer(), fetchedAfterRename.bodyAsText()).name,
        )

        val ownedDelete = client.delete("/api/chat/study/sessions/${saved.id}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.NoContent, ownedDelete.status)
    }

    @Test
    fun `community study sessions exclude the caller and page`() = testApplication {
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val studySessionsRepository = InMemoryStudySessionsRepository()
        val studySessionsService = StudySessionsService(
            repository = studySessionsRepository,
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
        val communityRecord = studySessionsRepository.listRecent().first { it.text == "community-two" }
        studySessionsRepository.upsert(
            communityRecord.copy(examGrade = StudyExamGrade(5, 5, 5, "Private grade.", 200L)),
        )

        val firstPage = client.get("/api/chat/study/sessions/community?limit=1") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.OK, firstPage.status)
        val firstPageBody = firstPage.bodyAsText()
        assertTrue("\"session\"" !in firstPageBody)
        assertTrue("\"examGrade\"" !in firstPageBody)
        assertTrue("\"sentenceImageUrls\":[\"https://img/community-two\"]" in firstPageBody)
        val page = json.decodeFromString(CommunityStudySessionsResponse.serializer(), firstPageBody)
        assertEquals(1, page.items.size)
        assertEquals("community-two", page.items.single().text)
        assertEquals(null, page.items.single().examGrade)
        assertTrue(!page.nextCursor.isNullOrBlank())

        val secondPage = client.get("/api/chat/study/sessions/community?limit=1&cursor=${page.nextCursor}") {
            header(DEVICE_TOKEN_HEADER, tokenA)
        }
        assertEquals(HttpStatusCode.OK, secondPage.status)
        val next = json.decodeFromString(CommunityStudySessionsResponse.serializer(), secondPage.bodyAsText())
        assertEquals(listOf("community-one"), next.items.map { it.text })
        assertTrue(next.items.none { it.text == "mine-only" })
    }

    @Test
    fun `exam routes let any device score a saved scene and keep grades device scoped`() = testApplication {
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val repository = InMemoryStudySessionsRepository()
        val studySessionsService = StudySessionsService(repository, deviceService)
        val examAi = FakeStudyAiClient()
        val studyService = StudyService(aiClient = examAi, clock = { 1_700L })
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
                    studyService = studyService,
                    studySessionsService = studySessionsService,
                )
            }
        }

        val ownerToken = deviceService.issueToken().deviceToken
        val otherToken = deviceService.issueToken().deviceToken
        val session = StudySessionResponse(
            text = "Xin chào.",
            textLanguageTag = "vi",
            sentences = listOf(StudySentence(text = "Xin chào.", tokens = emptyList())),
            characters = listOf(
                StudyStoryCharacter("main", "Lan", "main", "A neighbor."),
            ),
        )
        val sceneResponse = client.post("/api/chat/study/exam/scene") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    StudyExamSceneRequest.serializer(),
                    StudyExamSceneRequest(session = session, languageTag = "en"),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, sceneResponse.status)
        val scene = json.decodeFromString(StudyExamScene.serializer(), sceneResponse.bodyAsText())
        assertEquals(20, scene.beats.size)
        assertTrue(scene.beats.all { it.speakerName == "Lan" })

        val saved = studySessionsService.save(
            ownerToken,
            SaveStudySessionRequest(
                sourceMessageId = "exam-route-session",
                languageTag = "en",
                session = session.copy(examScene = scene),
            ),
        )
        val scoreRequest = StudyExamScoreRequest(
            sessionId = saved.id,
            answers = listOf(
                StudyExamScoreAnswer("listen-1", "Hello."),
                StudyExamScoreAnswer("speak-1", "Xin chào, Lan."),
                StudyExamScoreAnswer("speak-2", "   "),
                StudyExamScoreAnswer("unknown", "ignored"),
            ),
        )
        val scored = client.post("/api/chat/study/exam/score") {
            header(DEVICE_TOKEN_HEADER, ownerToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyExamScoreRequest.serializer(), scoreRequest))
        }
        assertEquals(HttpStatusCode.OK, scored.status)
        val result = json.decodeFromString(StudyExamScoreResponse.serializer(), scored.bodyAsText())
        assertEquals(20, result.beats.size)
        assertEquals(1, result.grade.listening)
        assertEquals(1, result.grade.speaking)
        assertEquals(1, result.grade.overall)
        assertEquals(result.grade, studySessionsService.get(ownerToken, saved.id)?.examGrade)

        val foreignScore = client.post("/api/chat/study/exam/score") {
            header(DEVICE_TOKEN_HEADER, otherToken)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyExamScoreRequest.serializer(), scoreRequest))
        }
        assertEquals(HttpStatusCode.OK, foreignScore.status)
        val foreignResult = json.decodeFromString(StudyExamScoreResponse.serializer(), foreignScore.bodyAsText())
        assertEquals(foreignResult.grade, studySessionsService.get(otherToken, saved.id)?.examGrade)
        assertEquals(result.grade, studySessionsService.get(ownerToken, saved.id)?.examGrade)
        assertEquals(4, examAi.examBeatRequests.size)
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
                "imageUrl":"https://img/$text",
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
