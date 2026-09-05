package apap.oneverandah.chat

import apap.oneverandah.api.ChatResponse
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyDissectSentenceResponse
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyPlaygroundImageResponse
import apap.oneverandah.api.StudyPlaygroundImageStatus
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySentenceImageRequest
import apap.oneverandah.api.StudyWordImageResponse
import apap.oneverandah.api.StudyWordImageRequest
import apap.oneverandah.chat.StudyCharactersGenerationRequest
import apap.oneverandah.chat.StudyCharacterDesign
import apap.oneverandah.api.StudyStoryCharactersResponse
import apap.oneverandah.api.StudyStoryCharactersRequest
import apap.oneverandah.api.StudySentenceImageResponse
import apap.oneverandah.api.StudyIntentResponse
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.StudyWordPlaygroundRequest
import apap.oneverandah.api.StudyWordPlaygroundResponse
import apap.oneverandah.routes.chatRoutes
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
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class ChatRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `chat endpoint validates sends and exposes history`() = testApplication {
        val repository = InMemoryChatRepository()
        val service = ChatService(
            repository = repository,
            aiClient = object : ChatAiClient {
                override suspend fun generate(
                    request: ChatGenerationRequest,
                    onPartial: suspend (GeneratedChatReply) -> Unit,
                ) = GeneratedChatReply("Hello!")
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
    fun `chat endpoint streams partial then final ndjson when accepted`() = testApplication {
        val service = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(
                    request: ChatGenerationRequest,
                    onPartial: suspend (GeneratedChatReply) -> Unit,
                ): GeneratedChatReply {
                    onPartial(GeneratedChatReply(text = "Quick."))
                    return GeneratedChatReply(
                        text = "Quick.\n\nFuller.",
                        followUpQuestions = listOf("One", "Two", "Three"),
                    )
                }
            },
            residentDirectory = ResidentDirectory(emptyList()),
        )
        application {
            install(ContentNegotiation) { json() }
            routing { chatRoutes(service, SpeechService(RouteSpeechClient())) }
        }

        val sent = client.post("/api/chat") {
            contentType(ContentType.Application.Json)
            header(io.ktor.http.HttpHeaders.Accept, "application/x-ndjson")
            setBody("{\"message\":\"Hi\",\"languageTag\":\"en\"}")
        }
        assertEquals(HttpStatusCode.OK, sent.status)
        val lines = sent.bodyAsText().lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        val partial = json.decodeFromString<ChatResponse>(lines[0])
        val final = json.decodeFromString<ChatResponse>(lines[1])
        assertEquals(true, partial.partial)
        assertEquals("Quick.", partial.message.text)
        assertEquals(false, final.partial)
        assertEquals("Quick.\n\nFuller.", final.message.text)
        assertEquals(listOf("One", "Two", "Three"), final.message.followUpQuestions)
        assertEquals(partial.message.id, final.message.id)
    }

    @Test
    fun `speech endpoint returns encoded audio`() = testApplication {
        val service = ChatService(
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

    @Test
    fun `study endpoint validates and returns skeleton session`() = testApplication {
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
        val studyService = StudyService(
            object : StudyAiClient {
                override suspend fun generateSkeleton(request: StudyGenerationRequest) = StudySessionResponse(
                    text = request.text,
                    textLanguageTag = "en",
                    sentences = listOf(
                        StudySentence(
                            text = request.text,
                            tokens = listOf(
                                StudyToken(text = request.text, status = StudyTokenStatus.PENDING),
                                StudyToken(text = ".", status = StudyTokenStatus.PLAIN),
                            ),
                        ),
                    ),
                )

                override suspend fun dissectSentence(request: StudyDissectGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) =
                    error("not used")

                override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) =
                    error("not used")

                override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) =
                    error("not used")

                override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) =
                    error("not used")
            },
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                chatRoutes(
                    chatService = chatService,
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                )
            }
        }

        val invalid = client.post("/api/chat/study") {
            contentType(ContentType.Application.Json)
            setBody("{\"text\":\"   \",\"languageTag\":\"en\"}")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val response = client.post("/api/chat/study") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudySessionRequest("Hello", "en")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val session = json.decodeFromString<StudySessionResponse>(response.bodyAsText())
        assertEquals("Hello", session.text)
        assertEquals(StudyTokenStatus.PENDING, session.sentences.single().tokens.first().status)
        assertEquals("", session.sentences.single().tokens.first().literalMeaning)
    }

    @Test
    fun `study intent endpoint validates and returns composed text`() = testApplication {
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
        val studyService = StudyService(
            FakeStudyAiClient(composedText = "Anh thích em.\nChúng ta nói chuyện được không?"),
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                chatRoutes(
                    chatService = chatService,
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                )
            }
        }

        val invalid = client.post("/api/chat/study/intent") {
            contentType(ContentType.Application.Json)
            setBody("{\"text\":\"   \",\"languageTag\":\"en\"}")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val response = client.post("/api/chat/study/intent") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    StudySessionRequest(
                        "Give me Vietnamese sentences about talking about relationships.",
                        "en",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val intent = json.decodeFromString<StudyIntentResponse>(response.bodyAsText())
        assertEquals("Anh thích em.\nChúng ta nói chuyện được không?", intent.text)
    }

    @Test
    fun `study dissect endpoint validates and returns filled sentence`() = testApplication {
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
        val studyService = StudyService(
            object : StudyAiClient {
                override suspend fun generateSkeleton(request: StudyGenerationRequest) =
                    error("not used")

                override suspend fun dissectSentence(request: StudyDissectGenerationRequest) =
                    StudySentence(
                        text = request.sentenceText,
                        tokens = request.tokens.map { tokenText ->
                            if (tokenText == ".") {
                                StudyToken(text = tokenText, status = StudyTokenStatus.PLAIN)
                            } else {
                                StudyToken(
                                    text = tokenText,
                                    status = StudyTokenStatus.READY,
                                    literalMeaning = "greeting",
                                    register = "neutral",
                                    contextualMeaning = "hello",
                                    nearSynonyms = listOf("hi"),
                                    exampleSentences = listOf(
                                        StudyExampleSentence(
                                            text = "Hello there.",
                                            translation = "Xin chào đó.",
                                        ),
                                        StudyExampleSentence(
                                            text = "Hello friend.",
                                            translation = "Xin chào bạn.",
                                        ),
                                        StudyExampleSentence(
                                            text = "Hello world.",
                                            translation = "Xin chào thế giới.",
                                        ),
                                    ),
                                )
                            }
                        },
                    )

                override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) =
                    error("not used")

                override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) =
                    error("not used")

                override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) =
                    error("not used")

                override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) =
                    error("not used")
            },
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                chatRoutes(
                    chatService = chatService,
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                )
            }
        }

        val invalid = client.post("/api/chat/study/dissect") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"text":"Hello.","sentenceText":"Hello.","tokens":[],"textLanguageTag":"en","languageTag":"en"}
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val response = client.post("/api/chat/study/dissect") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    StudyDissectSentenceRequest(
                        text = "Hello.",
                        sentenceText = "Hello.",
                        tokens = listOf("Hello", "."),
                        textLanguageTag = "en",
                        languageTag = "en",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val dissected = json.decodeFromString<StudyDissectSentenceResponse>(response.bodyAsText())
        assertEquals("Hello.", dissected.sentenceText)
        assertEquals("greeting", dissected.tokens.first().literalMeaning)
        assertEquals(StudyTokenStatus.READY, dissected.tokens.first().status)
        assertEquals(StudyTokenStatus.PLAIN, dissected.tokens.last().status)
    }

    @Test
    fun `study playground endpoint validates judges and exposes image status`() = testApplication {
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
        val studyService = StudyService(
            aiClient = object : StudyAiClient {
                override suspend fun generateSkeleton(request: StudyGenerationRequest) =
                    error("not used")

                override suspend fun dissectSentence(request: StudyDissectGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) =
                    StudyWordPlaygroundJudgment(
                        natural = true,
                        summary = "Natural greeting.",
                    )

                override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) =
                    error("not used")

                override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) =
                    error("not used")

                override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) =
                    error("not used")
            },
            imageGenerator = StudyPlaygroundImageGenerator { _, _ -> "https://cdn.example/play.png" },
            idGenerator = { "play-image-1" },
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                chatRoutes(
                    chatService = chatService,
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                )
            }
        }

        val invalid = client.post("/api/chat/study/playground") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"word":"","sentence":"Hello","textLanguageTag":"en","languageTag":"en"}
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val response = client.post("/api/chat/study/playground") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    StudyWordPlaygroundRequest(
                        word = "Hello",
                        sentence = "Hello friend.",
                        textLanguageTag = "en",
                        languageTag = "en",
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val playground = json.decodeFromString<StudyWordPlaygroundResponse>(response.bodyAsText())
        assertEquals(true, playground.natural)
        assertEquals("Natural greeting.", playground.summary)
        assertEquals("play-image-1", playground.imageId)

        var imageStatus = StudyPlaygroundImageStatus.PENDING
        var imageUrl: String? = null
        repeat(40) {
            val imageResponse = client.get("/api/chat/study/playground/image/play-image-1")
            assertEquals(HttpStatusCode.OK, imageResponse.status)
            val image = json.decodeFromString<StudyPlaygroundImageResponse>(imageResponse.bodyAsText())
            imageStatus = image.status
            imageUrl = image.imageUrl
            if (
                imageStatus == StudyPlaygroundImageStatus.COMPLETED ||
                imageStatus == StudyPlaygroundImageStatus.FAILED
            ) {
                return@repeat
            }
            delay(25)
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, imageStatus)
        assertEquals("https://cdn.example/play.png", imageUrl)

        val missing = client.get("/api/chat/study/playground/image/missing")
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }

    @Test
    fun `study sentence image endpoint validates and exposes shared image status`() = testApplication {
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
        val studyService = StudyService(
            aiClient = object : StudyAiClient {
                override suspend fun generateSkeleton(request: StudyGenerationRequest) =
                    error("not used")

                override suspend fun dissectSentence(request: StudyDissectGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) =
                    error("not used")

                override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) =
                    error("not used")

                override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) =
                    error("not used")

                override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) =
                    error("not used")
            },
            imageGenerator = StudyPlaygroundImageGenerator { _, _ -> "https://cdn.example/sentence.png" },
            idGenerator = { "sentence-image-1" },
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                chatRoutes(
                    chatService = chatService,
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                )
            }
        }

        val invalid = client.post("/api/chat/study/sentence-image") {
            contentType(ContentType.Application.Json)
            setBody("""{"sentenceText":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val response = client.post("/api/chat/study/sentence-image") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudySentenceImageRequest(sentenceText = "Xin chào.")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val started = json.decodeFromString<StudySentenceImageResponse>(response.bodyAsText())
        assertEquals("sentence-image-1", started.imageId)

        var imageStatus = StudyPlaygroundImageStatus.PENDING
        var imageUrl: String? = null
        repeat(40) {
            val imageResponse = client.get("/api/chat/study/playground/image/sentence-image-1")
            assertEquals(HttpStatusCode.OK, imageResponse.status)
            val image = json.decodeFromString<StudyPlaygroundImageResponse>(imageResponse.bodyAsText())
            imageStatus = image.status
            imageUrl = image.imageUrl
            if (
                imageStatus == StudyPlaygroundImageStatus.COMPLETED ||
                imageStatus == StudyPlaygroundImageStatus.FAILED
            ) {
                return@repeat
            }
            delay(25)
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, imageStatus)
        assertEquals("https://cdn.example/sentence.png", imageUrl)
    }


    @Test
    fun `study word image endpoint validates and exposes shared image status`() = testApplication {
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
        val studyService = StudyService(
            aiClient = object : StudyAiClient {
                override suspend fun generateSkeleton(request: StudyGenerationRequest) =
                    error("not used")

                override suspend fun dissectSentence(request: StudyDissectGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) =
                    error("not used")

                override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) =
                    error("not used")

                override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) =
                    error("not used")

                override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) =
                    error("not used")
            },
            imageGenerator = StudyPlaygroundImageGenerator { _, _ -> "https://cdn.example/word.png" },
            idGenerator = { "word-image-1" },
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                chatRoutes(
                    chatService = chatService,
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                )
            }
        }

        val invalid = client.post("/api/chat/study/word-image") {
            contentType(ContentType.Application.Json)
            setBody("""{"word":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val response = client.post("/api/chat/study/word-image") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyWordImageRequest(word = "chào", contextualMeaning = "greeting")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val started = json.decodeFromString<StudyWordImageResponse>(response.bodyAsText())
        assertEquals("word-image-1", started.imageId)

        var imageStatus = StudyPlaygroundImageStatus.PENDING
        var imageUrl: String? = null
        repeat(40) {
            val imageResponse = client.get("/api/chat/study/playground/image/word-image-1")
            assertEquals(HttpStatusCode.OK, imageResponse.status)
            val image = json.decodeFromString<StudyPlaygroundImageResponse>(imageResponse.bodyAsText())
            imageStatus = image.status
            imageUrl = image.imageUrl
            if (
                imageStatus == StudyPlaygroundImageStatus.COMPLETED ||
                imageStatus == StudyPlaygroundImageStatus.FAILED
            ) {
                return@repeat
            }
            delay(25)
        }
        assertEquals(StudyPlaygroundImageStatus.COMPLETED, imageStatus)
        assertEquals("https://cdn.example/word.png", imageUrl)
    }

    @Test
    fun `study characters endpoint validates and starts portrait jobs`() = testApplication {
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
        val studyService = StudyService(
            aiClient = object : StudyAiClient {
                override suspend fun generateSkeleton(request: StudyGenerationRequest) =
                    error("not used")

                override suspend fun dissectSentence(request: StudyDissectGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWordUsage(request: StudyWordPlaygroundGenerationRequest) =
                    error("not used")

                override suspend fun designStoryCharacters(request: StudyCharactersGenerationRequest) =
                    listOf(
                        StudyCharacterDesign(
                            id = "main",
                            name = "Lan",
                            role = "main",
                            description = "Young woman with dark hair",
                        ),
                    )

                override suspend fun generatePlayScenarios(request: StudyPlayScenariosGenerationRequest) =
                    error("not used")

                override suspend fun generatePlayTurn(request: StudyPlayTurnGenerationRequest) =
                    error("not used")

                override suspend fun evaluateWriteAttempt(request: StudyWriteCheckGenerationRequest) =
                    error("not used")

                override suspend fun evaluateExpressAttempt(request: StudyExpressCheckGenerationRequest) =
                    error("not used")

                override suspend fun generateExpressSentences(request: StudyExpressGenerateGenerationRequest) =
                    error("not used")
            },
            imageGenerator = StudyPlaygroundImageGenerator { _, _ -> "https://cdn.example/portrait.png" },
            idGenerator = { "portrait-1" },
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                chatRoutes(
                    chatService = chatService,
                    speechService = SpeechService(RouteSpeechClient()),
                    studyService = studyService,
                )
            }
        }

        val invalid = client.post("/api/chat/study/characters") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val response = client.post("/api/chat/study/characters") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StudyStoryCharactersRequest(text = "Xin chào.", textLanguageTag = "vi")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val started = json.decodeFromString<StudyStoryCharactersResponse>(response.bodyAsText())
        assertEquals(1, started.characters.size)
        assertEquals("Lan", started.characters.single().name)
        assertEquals("portrait-1", started.characters.single().imageId)
    }
}

internal class RouteSpeechClient : SpeechClient {
    override suspend fun designVoice() = "route-test-voice"

    override suspend fun synthesize(request: SpeechRequest, voiceId: String) =
        GeneratedSpeech(byteArrayOf(1, 2, 3), "audio/mpeg")
}