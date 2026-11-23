package apap.oneverandah.chat

import apap.oneverandah.api.ChatErrorResponse
import apap.oneverandah.api.ChatRequest
import apap.oneverandah.api.ChatResponse
import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildMode
import apap.oneverandah.api.StudyBuildRequest
import apap.oneverandah.api.StudyBuildRetryRequest
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyExamBeat
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamGrade
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyPlayScenario
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWordRecallLine
import apap.oneverandah.api.SavedStudySession
import apap.oneverandah.api.isTenRoundExam
import apap.oneverandah.routes.DEVICE_TOKEN_HEADER
import apap.oneverandah.routes.chatRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyBuildRoutesTest {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `build route acknowledges create and streams snapshot before done`() = testApplication {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val token = deviceService.issueToken().deviceToken
        val otherToken = deviceService.issueToken().deviceToken
        val ai = RouteStudyAi()
        val sessions = StudySessionsService(
            repository = repository,
            deviceService = deviceService,
            studyAiClient = ai,
            sessionStore = StudySessionStore(repository, scope = scope),
        )
        val studyService = StudyService(
            aiClient = ai,
            imageGenerator = StudyPlaygroundImageGenerator { _, _ -> "https://img/generated" },
            scope = scope,
        )
        val builds = StudyBuildService(
            repository = repository,
            deviceService = deviceService,
            sessionsService = sessions,
            studyService = studyService,
            scope = scope,
            textWorkers = 1,
            imageWorkers = 1,
            maxAttempts = 3,
            retryBackoffMillis = listOf(1L),
            talkAudioGenerator = StudyTalkAudioGenerator { text, _ -> "https://audio/${text.hashCode()}" },
        )
        val ownerGrade = StudyExamGrade(4, 5, 3, "Owner grade.", 100L)
        sessions.sessionStore.create(
            record(
                id = "complete-session",
                deviceTokenHash = DeviceService.hashToken(token),
                build = StudyBuildState(status = StudyBuildStatus.COMPLETE),
                name = "Complete",
            ).copy(examGrade = ownerGrade),
        )
        sessions.sessionStore.create(
            record(
                id = "retry-session",
                deviceTokenHash = DeviceService.hashToken(token),
                build = StudyBuildState(
                    status = StudyBuildStatus.PARTIAL,
                    failed = listOf(StudyBuildElement(StudyBuildElementKind.NAME)),
                ),
                name = "",
            ),
        )
        val chatService = unusedChatService()
        application {
            install(ContentNegotiation) { json(json) }
            routing {
                chatRoutes(
                    chatService = chatService,
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                    studySessionsService = sessions,
                    studyBuildService = builds,
                )
            }
        }

        try {
            val unauthorized = client.post("/api/chat/study/sessions/build") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyBuildRequest("Hello.", "en")))
            }
            assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

            val invalid = client.post("/api/chat/study/sessions/build") {
                header(DEVICE_TOKEN_HEADER, token)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyBuildRequest("  ", "en")))
            }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)

            val unauthorizedStream = client.get("/api/chat/study/sessions/complete-session/events")
            assertEquals(HttpStatusCode.Unauthorized, unauthorizedStream.status)

            val created = client.post("/api/chat/study/sessions/build") {
                header(DEVICE_TOKEN_HEADER, token)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        StudyBuildRequest(
                            text = "Hello.",
                            languageTag = "en",
                            sourceMessageId = "route-build",
                            mode = StudyBuildMode.EXACT,
                        ),
                    ),
                )
            }
            assertEquals(HttpStatusCode.Accepted, created.status)
            val createdSession = json.decodeFromString<SavedStudySession>(created.bodyAsText())
            assertTrue(createdSession.id.isNotBlank())
            assertTrue(createdSession.build != null)
            assertTrue(createdSession.session.sentences.isEmpty())
            val createdComplete = awaitTerminal(builds, createdSession.id)
            assertEquals(
                StudyBuildStatus.COMPLETE,
                createdComplete.build?.status,
                "Failed elements: ${createdComplete.build?.failed}",
            )

            val stream = client.get("/api/chat/study/sessions/complete-session/events") {
                header(DEVICE_TOKEN_HEADER, token)
            }
            assertEquals(HttpStatusCode.OK, stream.status)
            assertEquals("no-store, no-cache", stream.headers[HttpHeaders.CacheControl])
            assertTrue(stream.headers.getAll(HttpHeaders.Vary).orEmpty().any { it.contains(DEVICE_TOKEN_HEADER) })
            assertEquals("no", stream.headers["X-Accel-Buffering"])
            val events = stream.bodyAsText()
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { json.decodeFromString<StudyBuildEvent>(it) }
                .toList()
            assertIs<StudyBuildEvent.Snapshot>(events.first())
            assertEquals("complete-session", (events.first() as StudyBuildEvent.Snapshot).session.id)
            assertEquals(ownerGrade, (events.first() as StudyBuildEvent.Snapshot).session.examGrade)
            assertEquals(StudyBuildEvent.Done, events.last())
            assertTrue(events.any { it is StudyBuildEvent.TalkWorld })
            val backfilledWorld = requireNotNull(awaitTerminal(builds, "complete-session").session.talkWorld)
            assertEquals(6, backfilledWorld.scenes.size)
            assertTrue(backfilledWorld.scenes.all { scene ->
                !scene.backgroundImageUrl.isNullOrBlank() && scene.turns.all { !it.characterAudioUrl.isNullOrBlank() }
            })

            val foreignStream = client.get("/api/chat/study/sessions/complete-session/events") {
                header(DEVICE_TOKEN_HEADER, otherToken)
            }
            assertEquals(HttpStatusCode.OK, foreignStream.status)
            val foreignEvents = foreignStream.bodyAsText()
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { json.decodeFromString<StudyBuildEvent>(it) }
                .toList()
            val foreignSnapshot = assertIs<StudyBuildEvent.Snapshot>(foreignEvents.first())
            assertNull(foreignSnapshot.session.examGrade)

            val retry = client.post("/api/chat/study/sessions/retry-session/build/retry") {
                header(DEVICE_TOKEN_HEADER, token)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        StudyBuildRetryRequest(elements = listOf(StudyBuildElement(StudyBuildElementKind.NAME))),
                    ),
                )
            }
            assertEquals(HttpStatusCode.OK, retry.status)
            val state = json.decodeFromString<StudyBuildState>(retry.bodyAsText())
            assertTrue(state.failed.isEmpty())
            assertEquals(StudyBuildStatus.COMPLETE, awaitTerminal(builds, "retry-session").build?.status)

            val foreignRetry = client.post("/api/chat/study/sessions/retry-session/build/retry") {
                header(DEVICE_TOKEN_HEADER, otherToken)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudyBuildRetryRequest()))
            }
            assertEquals(HttpStatusCode.NotFound, foreignRetry.status)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `legacy study route waits for server generated skeleton`() = testApplication {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = InMemoryStudySessionsRepository()
        val deviceService = DeviceService(InMemoryDeviceRepository())
        val token = deviceService.issueToken().deviceToken
        val ai = RouteStudyAi()
        val sessions = StudySessionsService(
            repository = repository,
            deviceService = deviceService,
            studyAiClient = ai,
            sessionStore = StudySessionStore(repository, scope = scope),
        )
        val studyService = StudyService(
            aiClient = ai,
            imageGenerator = StudyPlaygroundImageGenerator { _, _ -> "https://img/generated" },
            scope = scope,
        )
        val builds = StudyBuildService(
            repository = repository,
            deviceService = deviceService,
            sessionsService = sessions,
            studyService = studyService,
            scope = scope,
            textWorkers = 1,
            imageWorkers = 1,
            retryBackoffMillis = listOf(1L),
            talkAudioGenerator = StudyTalkAudioGenerator { text, _ -> "https://audio/${text.hashCode()}" },
        )
        application {
            install(ContentNegotiation) { json(json) }
            routing {
                chatRoutes(
                    chatService = unusedChatService(),
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                    studySessionsService = sessions,
                    studyBuildService = builds,
                )
            }
        }

        try {
            val response = client.post("/api/chat/study") {
                header(DEVICE_TOKEN_HEADER, token)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(StudySessionRequest("Xin chào.", "en", "legacy-source")))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val skeleton = json.decodeFromString<StudySessionResponse>(response.bodyAsText())
            assertTrue(skeleton.sentences.isNotEmpty())
            assertEquals("Xin chào.", skeleton.text)
            val saved =
                requireNotNull(repository.findByDeviceAndSource(DeviceService.hashToken(token), "legacy-source"))
            assertTrue(awaitTerminal(builds, saved.id).session.sentences.isNotEmpty())
        } finally {
            scope.cancel()
        }
    }

    private fun record(
        id: String,
        deviceTokenHash: String,
        build: StudyBuildState,
        name: String,
    ) = SavedStudySessionRecord(
        id = id,
        deviceTokenHash = deviceTokenHash,
        languageTag = "en",
        text = "Hello.",
        textLanguageTag = "en",
        session = completeSession(),
        createdAt = 1L,
        updatedAt = 1L,
        name = name,
        build = build,
    )

    private suspend fun awaitTerminal(builds: StudyBuildService, id: String): SavedStudySession = withTimeout(5_000L) {
        var session = builds.getSession(id) ?: error("Study session disappeared")
        while (session.build?.status != StudyBuildStatus.COMPLETE && session.build?.status != StudyBuildStatus.PARTIAL) {
            delay(5L)
            session = builds.getSession(id) ?: error("Study session disappeared")
        }
        session
    }

    private fun completeSession() = StudySessionResponse(
        text = "Hello.",
        textLanguageTag = "en",
        sentences = listOf(
            StudySentence(
                text = "Hello.",
                tokens = listOf(
                    StudyToken(
                        text = "Hello",
                        status = StudyTokenStatus.READY,
                        literalMeaning = "greeting",
                        imageUrl = "https://img/word",
                    ),
                ),
                imageUrl = "https://img/sentence",
            ),
        ),
        characters = listOf(StudyStoryCharacter("main", "Lan", "main", "Neighbor", imageUrl = "https://img/portrait")),
        playScenarios = (1..4).map {
            StudyPlayScenario(
                "play-$it",
                "At a market.",
                imageUrl = "https://img/cover-$it"
            )
        },
        examScene = testExamScene(),
    )

    private fun testExamScene() = StudyExamScene(
        id = "exam-1",
        setting = "At a market.",
        outline = (1..10).map { "Round $it" },
        beats = (1..10).flatMap { round ->
            listOf(
                StudyExamBeat(
                    id = "listen-$round",
                    kind = StudyExamBeatKind.LISTEN,
                    speakerId = "main",
                    speakerName = "Lan",
                    line = "Hello.",
                    prompt = "What did Lan say?",
                    expectedMeaning = "A greeting.",
                    sourceSentence = "Hello.",
                ),
                StudyExamBeat(
                    id = "speak-$round",
                    kind = StudyExamBeatKind.SPEAK,
                    speakerId = "main",
                    speakerName = "Lan",
                    prompt = "Reply to Lan.",
                    expectedMeaning = "A greeting.",
                    sourceSentence = "Hello.",
                ),
            )
        },
    )

    private fun unusedChatService() = ChatService(
        repository = InMemoryChatRepository(),
        aiClient = object : ChatAiClient {
            override suspend fun generate(
                request: ChatGenerationRequest,
                onPartial: suspend (GeneratedChatReply) -> Unit,
            ) = GeneratedChatReply("unused")
        },
        residentDirectory = ResidentDirectory(emptyList()),
    )

    private inner class RouteStudyAi : StudyAiClient {
        override suspend fun generateSkeleton(request: StudyGenerationRequest) = StudySessionResponse(
            text = request.text,
            textLanguageTag = "vi",
            sentences = listOf(
                StudySentence("Xin chào.", listOf(StudyToken("Xin", StudyTokenStatus.PENDING))),
            ),
        )

        override suspend fun dissectSentence(request: StudyDissectGenerationRequest) = StudySentence(
            text = request.sentenceText,
            tokens = request.tokens.map { StudyToken(it, StudyTokenStatus.READY, literalMeaning = "hello") },
        )

        override suspend fun generateWordRecallScenarios(request: StudyNeedScenarioGenerationRequest) =
            request.tokens.associate { token ->
                token.tokenIndex to listOf(
                    StudyWordRecallLine("Mina", "___!", "___!"),
                    StudyWordRecallLine("Kai", "___!", "___!"),
                )
            }

        override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) =
            StudyWordPlaygroundJudgment(true, "Natural.")

        override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest) =
            listOf(StudyCharacterDesign("main", "Lan", "main", "Neighbor"))

        override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest) =
            (1..4).map { StudyPlayScenarioDesign("At a market $it.") }

        override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) =
            StudyPlayTurnDesign("Conversation.", "Xin chào.")

        override suspend fun generateExamScene(request: StudyExamSceneGenerationRequest): StudyExamScene =
            testExamScene()

        override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) =
            StudyWriteFeedback("Clear.")

        override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) =
            StudyExpressJudgment(true, "Clear.")

        override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) =
            listOf(StudyExampleSentence("Xin chào.", "Hello."))
    }
}
