package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechWordTiming
import apap.oneverandah.config.AppConfig
import apap.oneverandah.config.QwenChatConfig
import com.alibaba.dashscope.audio.tts.timestamp.Word
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class QwenSpeechClientTest {
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
    fun `default TTS model and designed voice use Qwen-Audio flash`() {
        val emptyEnvFile = Files.createTempFile("one-verandah", ".env").toFile()

        try {
            val config = AppConfig.load(emptyEnvFile).qwenChat
            assertEquals("qwen-audio-3.0-tts-flash", config.ttsModel)
            assertEquals("longanhuan_v3.6", config.ttsVoice)
        } finally {
            emptyEnvFile.delete()
        }
    }

    @Test
    fun `default chat models use flash by default and max for heavy study work`() {
        val emptyEnvFile = Files.createTempFile("one-verandah", ".env").toFile()

        try {
            val config = AppConfig.load(emptyEnvFile).qwenChat
            assertEquals("qwen3.8-flash", config.chatModel)
            assertEquals("qwen4-max", config.chatHeavyModel)
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
    fun `synthesis delegates to Qwen-Audio protocol and returns MP3 audio with word timings`() = runBlocking {
        val audio = byteArrayOf(1, 2, 3, 4)
        val words = listOf(
            SpeechWordTiming(text = "Bonjour", beginMs = 0, endMs = 320, beginIndex = 0, endIndex = 7),
            SpeechWordTiming(text = "monde", beginMs = 400, endMs = 780, beginIndex = 11, endIndex = 16),
        )
        var synthesizedText = ""
        var synthesizedVoice = ""
        var synthesizedLanguage = ""
        val client = HttpClient(MockEngine { error("HTTP is not used for synthesis") })
        val speechClient = DashScopeSpeechClient(config(), client) { text, voiceId, languageTag ->
            synthesizedText = text
            synthesizedVoice = voiceId
            synthesizedLanguage = languageTag
            SynthesizedAudio(bytes = audio, words = words)
        }

        val result = speechClient.synthesize(
            request = SpeechRequest(
                text = "Bonjour le monde",
                languageTag = "fr-FR",
            ),
            voiceId = "longanhuan_v3.6",
        )

        assertContentEquals(audio, result.bytes)
        assertEquals("audio/mpeg", result.contentType)
        assertEquals(words, result.words)
        assertEquals("Bonjour le monde", synthesizedText)
        assertEquals("longanhuan_v3.6", synthesizedVoice)
        assertEquals("fr-FR", synthesizedLanguage)
        client.close()
    }

    @Test
    fun `dashscope word maps into speech timing and drops empty ranges`() {
        val valid = Word().apply {
            text = "  Xin  "
            beginTime = 12
            endTime = 90
            beginIndex = 0
            endIndex = 3
        }
        val emptyText = Word().apply {
            text = "   "
            beginTime = 0
            endTime = 10
        }
        val zeroDuration = Word().apply {
            text = "a"
            beginTime = 5
            endTime = 5
        }

        assertEquals(
            SpeechWordTiming(text = "Xin", beginMs = 12, endMs = 90, beginIndex = 0, endIndex = 3),
            valid.toSpeechWordTiming(),
        )
        assertNull(emptyText.toSpeechWordTiming())
        assertNull(zeroDuration.toSpeechWordTiming())
    }

    @Test
    fun `synthesis wraps Qwen-Audio protocol failures`() = runBlocking {
        val client = HttpClient(MockEngine { error("HTTP is not used for synthesis") })
        val speechClient = DashScopeSpeechClient(config(), client) { _, _, _ ->
            error("region rejected voice")
        }

        val exception = assertFailsWith<QwenSpeechException> {
            speechClient.synthesize(
                request = SpeechRequest(text = "Hello", languageTag = "en"),
                voiceId = "longanhuan_v3.6",
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
                voiceId = "longanhuan_v3.6",
            )
        }

        assertEquals(cancellation, exception)
        client.close()
    }

    @Test
    fun `voice design resolves the default Qwen-Audio system voice`() = runBlocking {
        val client = HttpClient(MockEngine { error("HTTP is not used for default voice resolution") })
        val speechClient = DashScopeSpeechClient(config(), client)

        val voiceId = speechClient.designVoice()

        assertEquals("longanhuan_v3.6", voiceId)
        client.close()
    }

    @Test
    fun `voice design rejects a blank default voice`() = runBlocking {
        val client = HttpClient(MockEngine { error("HTTP is not used for default voice resolution") })
        val speechClient = DashScopeSpeechClient(config(ttsVoice = "  "), client)

        val exception = assertFailsWith<IllegalStateException> {
            speechClient.designVoice()
        }

        assertEquals("Qwen TTS requires QWEN_TTS_VOICE", exception.message)
        client.close()
    }

    private fun config(
        ttsVoice: String = "longanhuan_v3.6",
    ) = QwenChatConfig(
        chatEndpoint = "https://example.test/chat",
        chatModel = "qwen3.8-flash",
        chatHeavyModel = "qwen4-max",
        ttsEndpoint = "https://example.test/tts",
        ttsModel = "qwen-audio-3.0-tts-flash",
        ttsVoice = ttsVoice,
        voiceDesignEndpoint = "https://example.test/customization",
        voiceDesignModel = "voice-enrollment",
        asrEndpoint = "wss://example.test/realtime",
        asrModel = "qwen3-asr-flash-realtime",
        apiKey = "test-key",
    )
}
