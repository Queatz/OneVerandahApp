package apap.oneverandah.chat

import apap.oneverandah.api.ChatResponse
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudyDissectSentenceResponse
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
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
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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

    @Test
    fun `study endpoint validates and returns skeleton session`() = testApplication {
        val chatService = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(request: ChatGenerationRequest) = GeneratedChatReply("unused")
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
    fun `study dissect endpoint validates and returns filled sentence`() = testApplication {
        val chatService = ChatService(
            repository = InMemoryChatRepository(),
            aiClient = object : ChatAiClient {
                override suspend fun generate(request: ChatGenerationRequest) = GeneratedChatReply("unused")
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
}

internal class RouteSpeechClient : SpeechClient {
    override suspend fun designVoice() = "route-test-voice"

    override suspend fun synthesize(request: SpeechRequest, voiceId: String) =
        GeneratedSpeech(byteArrayOf(1, 2, 3), "audio/mpeg")
}