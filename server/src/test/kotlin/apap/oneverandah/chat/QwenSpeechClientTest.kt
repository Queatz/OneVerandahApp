package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.config.AppConfig
import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class QwenSpeechClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `default TTS endpoint uses Beijing speech synthesis API`() {
        val emptyEnvFile = Files.createTempFile("one-verandah", ".env").toFile()

        try {
            assertEquals(
                "wss://dashscope.aliyuncs.com/api-ws/v1/inference/",
                AppConfig.load(emptyEnvFile).qwenChat.ttsEndpoint,
            )
        } finally {
            emptyEnvFile.delete()
        }
    }

    @Test
    fun `speech endpoints cannot be overridden into incompatible regions or protocols`() {
        val envFile = Files.createTempFile("one-verandah", ".env").toFile().apply {
            writeText(
                """
                QWEN_TTS_ENDPOINT=https://dashscope-intl.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
                QWEN_VOICE_DESIGN_ENDPOINT=https://dashscope-us.aliyuncs.com/api/v1/services/audio/tts/customization
                """.trimIndent(),
            )
        }

        try {
            val config = AppConfig.load(envFile).qwenChat
            assertEquals("wss://dashscope.aliyuncs.com/api-ws/v1/inference/", config.ttsEndpoint)
            assertEquals(
                "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization",
                config.voiceDesignEndpoint,
            )
        } finally {
            envFile.delete()
        }
    }

    @Test
    fun `synthesis delegates to CosyVoice protocol and returns MP3 audio`() = runBlocking {
        val audio = byteArrayOf(1, 2, 3, 4)
        var synthesizedText = ""
        var synthesizedVoice = ""
        var synthesizedLanguage = ""
        val client = HttpClient(MockEngine { error("HTTP is not used for synthesis") })
        val speechClient = DashScopeSpeechClient(config(), client) { text, voiceId, languageTag ->
            synthesizedText = text
            synthesizedVoice = voiceId
            synthesizedLanguage = languageTag
            audio
        }

        val result = speechClient.synthesize(
            request = SpeechRequest(
                text = "Bonjour le monde",
                languageTag = "fr-FR",
            ),
            voiceId = "cosyvoice-test-id",
        )

        assertContentEquals(audio, result.bytes)
        assertEquals("audio/mpeg", result.contentType)
        assertEquals("Bonjour le monde", synthesizedText)
        assertEquals("cosyvoice-test-id", synthesizedVoice)
        assertEquals("fr-FR", synthesizedLanguage)
        client.close()
    }

    @Test
    fun `synthesis wraps CosyVoice protocol failures`() = runBlocking {
        val client = HttpClient(MockEngine { error("HTTP is not used for synthesis") })
        val speechClient = DashScopeSpeechClient(config(), client) { _, _, _ ->
            error("region rejected voice")
        }

        val exception = assertFailsWith<QwenSpeechException> {
            speechClient.synthesize(
                request = SpeechRequest(text = "Hello", languageTag = "en"),
                voiceId = "cosyvoice-test-id",
            )
        }

        assertEquals("Qwen TTS failed: region rejected voice", exception.message)
        client.close()
    }

    @Test
    fun `synthesis preserves coroutine cancellation`() = runBlocking {
        val cancellation = CancellationException("request cancelled")
        val client = HttpClient(MockEngine { error("HTTP is not used for synthesis") })
        val speechClient = DashScopeSpeechClient(config(), client) { _, _, _ -> throw cancellation }

        val exception = assertFailsWith<CancellationException> {
            speechClient.synthesize(
                request = SpeechRequest(text = "Hello", languageTag = "en"),
                voiceId = "cosyvoice-test-id",
            )
        }

        assertEquals(cancellation, exception)
        client.close()
    }

    @Test
    fun `voice design sends CosyVoice enrollment payload`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"output":{"voice":"cosyvoice-test-id"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val speechClient = DashScopeSpeechClient(config(), client)

        val voiceId = speechClient.designVoice()

        assertEquals("cosyvoice-test-id", voiceId)
        val body = json.parseToJsonElement(requestBody).jsonObject
        assertEquals("voice-enrollment", body["model"]?.jsonPrimitive?.content)
        val input = body["input"]?.jsonObject
        assertEquals("create_voice", input?.get("action")?.jsonPrimitive?.content)
        assertEquals("cosyvoice-v3.5-plus", input?.get("target_model")?.jsonPrimitive?.content)
        assertEquals(
            "A warm, calm and approachable multilingual community assistant voice with clear pronunciation.",
            input?.get("voice_prompt")?.jsonPrimitive?.content,
        )
        assertEquals(
            "Hello! I am your friendly One Verandah community assistant.",
            input?.get("preview_text")?.jsonPrimitive?.content,
        )
        val prefix = input?.get("prefix")?.jsonPrimitive?.content.orEmpty()
        assertEquals("onev", prefix)
        assertEquals(true, prefix.length <= 10)
        val parameters = body["parameters"]?.jsonObject
        assertEquals("24000", parameters?.get("sample_rate")?.jsonPrimitive?.content)
        assertEquals("wav", parameters?.get("response_format")?.jsonPrimitive?.content)
        client.close()
    }

    private fun config() = QwenChatConfig(
        chatEndpoint = "https://example.test/chat",
        chatModel = "qwen-plus",
        ttsEndpoint = "https://example.test/tts",
        ttsModel = "cosyvoice-v3.5-plus",
        voiceDesignEndpoint = "https://example.test/customization",
        voiceDesignModel = "voice-enrollment",
        asrEndpoint = "wss://example.test/realtime",
        asrModel = "qwen3-asr-flash-realtime",
        apiKey = "test-key",
    )
}