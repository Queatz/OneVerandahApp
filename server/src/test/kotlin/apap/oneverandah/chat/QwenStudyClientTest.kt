package apap.oneverandah.chat

import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class QwenStudyClientTest {
    @Test
    fun `study skeleton and dissect use the heavy chat model`() = runBlocking {
        val models = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            models += Json.parseToJsonElement(body).jsonObject["model"]?.jsonPrimitive?.content.orEmpty()
            val content = when (models.size) {
                1 -> """{"textLanguageTag":"vi","sentences":[{"text":"Xin chào.","tokens":[{"text":"Xin chào","status":"PENDING"},{"text":".","status":"PLAIN"}]}]}"""
                else -> """{"sentenceText":"Xin chào.","tokens":[{"text":"Xin chào","literalMeaning":"Hello","register":"neutral","contextualMeaning":"Greeting","nearSynonyms":["chào"],"exampleSentences":[{"text":"Xin chào bạn.","translation":"Hello friend."},{"text":"Chào buổi sáng.","translation":"Good morning."},{"text":"Xin chào mọi người.","translation":"Hello everyone."}]},{"text":".","literalMeaning":"","register":"","contextualMeaning":"","nearSynonyms":[],"exampleSentences":[]}]}"""
            }
            respond(
                content = qwenChatResponse(content),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeStudyAiClient(qwenConfig(), client)

        ai.generateSkeleton(StudyGenerationRequest(text = "Xin chào.", languageTag = "en"))
        ai.dissectSentence(
            StudyDissectGenerationRequest(
                text = "Xin chào.",
                sentenceText = "Xin chào.",
                tokens = listOf("Xin chào", "."),
                textLanguageTag = "vi",
                languageTag = "en",
            ),
        )

        assertEquals(listOf("qwen3.8-max", "qwen3.8-max"), models)
        client.close()
    }

    @Test
    fun `study intent generation uses the flash chat model`() = runBlocking {
        val models = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            models += Json.parseToJsonElement(body).jsonObject["model"]?.jsonPrimitive?.content.orEmpty()
            respond(
                content = qwenChatResponse("""{"text":"Anh thích em."}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeStudyAiClient(qwenConfig(), client)

        val composed = ai.generateStudyIntent(
            StudyGenerationRequest(
                text = "sentences about talking about relationships in Vietnamese",
                languageTag = "en",
            ),
        )

        assertEquals("Anh thích em.", composed)
        assertEquals(listOf("qwen3.8-flash"), models)
        client.close()
    }

    @Test
    fun `study intent retries when the model connection closes before any data`() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            if (calls == 1) {
                throw EOFException("Not enough data available")
            }
            respond(
                content = qwenChatResponse("""{"text":"Anh thích em."}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeStudyAiClient(qwenConfig(), client)

        val composed = ai.generateStudyIntent(
            StudyGenerationRequest(
                text = "sentences about talking about relationships in Vietnamese",
                languageTag = "en",
            ),
        )

        assertEquals("Anh thích em.", composed)
        assertEquals(2, calls)
        client.close()
    }

    @Test
    fun `study intent does not retry a model error response`() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            respond(
                content = """{"error":"bad request"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeStudyAiClient(qwenConfig(), client)

        assertFailsWith<QwenChatException> {
            ai.generateStudyIntent(
                StudyGenerationRequest(text = "Xin chào.", languageTag = "en"),
            )
        }
        assertEquals(1, calls)
        client.close()
    }

    @Test
    fun `study text extraction uses the flash chat model`() = runBlocking {
        val models = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            models += Json.parseToJsonElement(body).jsonObject["model"]?.jsonPrimitive?.content.orEmpty()
            respond(
                content = qwenChatResponse("""{"text":"¿Cómo estás?"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeStudyAiClient(qwenConfig(), client)

        val extracted = ai.extractStudyText(
            StudyGenerationRequest(
                text = "A natural greeting is ¿Cómo estás? when you meet someone.",
                languageTag = "en",
            ),
        )

        assertEquals("¿Cómo estás?", extracted)
        assertEquals(listOf("qwen3.8-flash"), models)
        client.close()
    }

    @Test
    fun `lighter study tasks use the default flash chat model`() = runBlocking {
        val models = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            models += Json.parseToJsonElement(body).jsonObject["model"]?.jsonPrimitive?.content.orEmpty()
            val content = when (models.size) {
                1 -> """{"natural":true,"summary":"Natural usage."}"""
                2 -> """{"characters":[{"id":"main","name":"Lan","role":"main","description":"Young woman in linen"}]}"""
                3 -> """{"scenarios":[{"description":"A neighbor greets you.","openingLine":"Xin chào!"},{"description":"A vendor offers fruit.","openingLine":null},{"description":"A classmate asks a question.","openingLine":"Bạn rảnh không?"},{"description":"Someone waves from across the courtyard.","openingLine":null}]}"""
                4 -> """{"description":"They smile.","reply":"Thật sao?"}"""
                5 -> """{"feedback":"Add the missing word."}"""
                6 -> """{"correct":true,"feedback":""}"""
                else -> """{"sentences":[{"text":"Xin chào.","translation":"Hello."},{"text":"Tạm biệt.","translation":"Goodbye."},{"text":"Cảm ơn.","translation":"Thank you."},{"text":"Không sao.","translation":"No problem."},{"text":"Hẹn gặp lại.","translation":"See you later."},{"text":"Bạn khỏe không?","translation":"How are you?"},{"text":"Tôi ổn.","translation":"I am fine."},{"text":"Đi thôi.","translation":"Let's go."},{"text":"Chờ một chút.","translation":"Wait a moment."},{"text":"Được rồi.","translation":"Alright."}]}"""
            }
            respond(
                content = qwenChatResponse(content),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeStudyAiClient(qwenConfig(), client)

        ai.evaluateWordUsage(
            StudyWordPlaygroundGenerationRequest(
                word = "chào",
                sentence = "Xin chào.",
                textLanguageTag = "vi",
                languageTag = "en",
            ),
        )
        ai.designStoryCharacters(
            StudyCharactersGenerationRequest(text = "Xin chào.", textLanguageTag = "vi"),
        )
        ai.generatePlayScenarios(
            StudyPlayScenariosGenerationRequest(
                text = "Xin chào.",
                textLanguageTag = "vi",
                languageTag = "en",
                characters = emptyList(),
            ),
        )
        ai.generatePlayTurn(
            StudyPlayTurnGenerationRequest(
                text = "Xin chào.",
                textLanguageTag = "vi",
                languageTag = "en",
                scenarioDescription = "A neighbor greets you.",
                characters = emptyList(),
                history = emptyList(),
                userMessage = "Chào bạn!",
            ),
        )
        ai.evaluateWriteAttempt(
            StudyWriteCheckGenerationRequest(
                expected = "Xin chào.",
                attempt = "Xin chao.",
                textLanguageTag = "vi",
                languageTag = "en",
            ),
        )
        ai.evaluateExpressAttempt(
            StudyExpressCheckGenerationRequest(
                meaning = "Hello.",
                attempt = "Xin chào.",
                textLanguageTag = "vi",
                languageTag = "en",
                referenceText = "Xin chào.",
            ),
        )
        ai.generateExpressSentences(
            StudyExpressGenerateGenerationRequest(
                text = "Xin chào.",
                textLanguageTag = "vi",
                languageTag = "en",
                vocabulary = listOf("chào"),
            ),
        )

        assertEquals(
            listOf(
                "qwen3.8-flash",
                "qwen3.8-flash",
                "qwen3.8-flash",
                "qwen3.8-flash",
                "qwen3.8-flash",
                "qwen3.8-flash",
                "qwen3.8-flash",
            ),
            models,
        )
        client.close()
    }

    @Test
    fun `intended scene design uses the flash chat model`() = runBlocking {
        val models = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            bodies += body
            models += Json.parseToJsonElement(body).jsonObject["model"]?.jsonPrimitive?.content.orEmpty()
            respond(
                content = qwenChatResponse(
                    """{"scene":"A young woman smiles from a sunlit doorway, cinematic medium shot."}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val ai = DashScopeStudyAiClient(qwenConfig(), client)

        val scene = ai.designIntendedScene(
            StudySceneGenerationRequest(
                sentence = "Lan mỉm cười.",
                fullText = "Lan mỉm cười. Minh vẫy tay.",
                sentenceIndex = 0,
                sentenceCount = 2,
                scenarioDescription = "A neighbor greets you.",
                spokenLine = "Xin chào!",
                natural = false,
            ),
        )

        assertEquals("A young woman smiles from a sunlit doorway, cinematic medium shot.", scene)
        assertEquals(listOf("qwen3.8-flash"), models)
        assertContains(bodies.single(), "Play scenario setup to interpret visually")
        assertContains(bodies.single(), "A neighbor greets you.")
        assertContains(bodies.single(), "Spoken line for emotional context only")
        assertContains(bodies.single(), "The learner's usage was judged unnatural")
        client.close()
    }

    @Test
    fun `intended scene parser accepts aliases and fences`() {
        val scene = QwenStudySceneParser.parse(
            """
            ```json
            {"description":"A neighbor waves from a courtyard in warm evening light."}
            ```
            """.trimIndent(),
        )
        assertEquals("A neighbor waves from a courtyard in warm evening light.", scene)
    }

    private fun qwenConfig() = QwenChatConfig(
        chatEndpoint = "https://example.test/chat",
        chatModel = "qwen3.8-flash",
        chatHeavyModel = "qwen3.8-max",
        ttsEndpoint = "https://example.test/tts",
        ttsModel = "qwen-audio-3.0-tts-flash",
        ttsVoice = "longanhuan_v3.6",
        voiceDesignEndpoint = "https://example.test/customization",
        voiceDesignModel = "voice-enrollment",
        asrEndpoint = "wss://example.test/realtime",
        asrModel = "qwen3-asr-flash-realtime",
        apiKey = "test-key",
    )

    private fun qwenChatResponse(content: String): String {
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """{"choices":[{"message":{"content":"$escaped"}}]}"""
    }
}
