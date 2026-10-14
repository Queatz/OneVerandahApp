package apap.oneverandah.chat

import apap.oneverandah.config.QwenChatConfig
import apap.oneverandah.routes.asrRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QwenAsrRelayTest {
    @Test
    fun `realtime ASR uses configured endpoint and required Qwen headers`() {
        val config = config()

        assertEquals(
            "wss://workspace.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3-asr-flash-realtime",
            config.asrUrl(),
        )
        assertEquals("Bearer test-key", config.asrHeaders()["Authorization"])
        assertEquals("realtime=v1", config.asrHeaders()["OpenAI-Beta"])
    }

    @Test
    fun `ASR config supports custom language parameter`() {
        val config = config()
        assertEquals(
            "wss://workspace.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3-asr-flash-realtime&language=vi",
            config.asrUrl("vi"),
        )
    }

    @Test
    fun `ASR config uses default url when language is blank`() {
        val config = config()
        assertEquals(
            "wss://workspace.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3-asr-flash-realtime",
            config.asrUrl(null),
        )
        assertEquals(
            "wss://workspace.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3-asr-flash-realtime",
            config.asrUrl(""),
        )
    }

    @Test
    fun `relay forwards text events and converts binary PCM into Qwen append events`() = runBlocking {
        val browserIncoming = Channel<Frame>(Channel.UNLIMITED)
        val browserOutgoing = Channel<Frame>(Channel.UNLIMITED)
        val qwenIncoming = Channel<Frame>(Channel.UNLIMITED)
        val qwenOutgoing = Channel<Frame>(Channel.UNLIMITED)
        val pcm = byteArrayOf(1, 2, 3, 4)
        browserIncoming.send(Frame.Text("{\"type\":\"session.update\"}"))
        browserIncoming.send(Frame.Binary(true, pcm))
        browserIncoming.close()
        qwenIncoming.send(Frame.Text("{\"type\":\"conversation.item.input_audio_transcription.completed\",\"transcript\":\"hello\"}"))
        qwenIncoming.send(Frame.Text("{\"type\":\"error\",\"error\":{\"message\":\"invalid audio format\"}}"))
        qwenIncoming.close()

        relayAsrFrames(browserIncoming, browserOutgoing, qwenIncoming, qwenOutgoing)

        assertEquals(
            "{\"type\":\"session.update\"}",
            (qwenOutgoing.receive() as Frame.Text).readText(),
        )
        assertEquals(
            """{"type":"input_audio_buffer.append","audio":"${Base64.getEncoder().encodeToString(pcm)}"}""",
            (qwenOutgoing.receive() as Frame.Text).readText(),
        )
        assertTrue(qwenOutgoing.isClosedForReceive.not())
        assertEquals(
            "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"transcript\":\"hello\"}",
            (browserOutgoing.receive() as Frame.Text).readText(),
        )
        assertEquals(
            "{\"type\":\"error\",\"error\":{\"message\":\"invalid audio format\"}}",
            (browserOutgoing.receive() as Frame.Text).readText(),
        )
        assertTrue(browserOutgoing.isClosedForReceive)
    }

    @Test
    fun `ASR config is disabled without an API key`() {
        assertFalse(config(apiKey = "").asrEnabled)
        assertTrue(config().asrEnabled)
    }

    @Test
    fun `ASR config identifies missing deployment settings`() {
        assertEquals("QWEN_API_KEY", config(apiKey = "").asrUnavailableReason)
        assertEquals("QWEN_ASR_WORKSPACE_ID or QWEN_ASR_ENDPOINT", config(asrEndpoint = "").asrUnavailableReason)
        assertEquals("QWEN_ASR_MODEL", config(asrModel = "").asrUnavailableReason)
        assertEquals(null, config().asrUnavailableReason)
    }

    @Test
    fun `relay route closes with policy violation when ASR is unavailable`() = testApplication {
        application {
            install(ServerWebSockets)
            routing {
                asrRoutes(QwenAsrRelay(config(apiKey = ""), HttpClient(MockEngine { error("unused") })))
            }
        }
        val websocketClient = createClient { install(ClientWebSockets) }

        val session = websocketClient.webSocketSession("/api/chat/asr")
        val reason = session.closeReason.await()

        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        assertEquals("Voice input is unavailable", reason?.message)
    }

    private fun config(
        apiKey: String = "test-key",
        asrEndpoint: String = "wss://workspace.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime",
        asrModel: String = "qwen3-asr-flash-realtime",
    ) = QwenChatConfig(
        chatEndpoint = "https://example.test/chat",
        chatModel = "qwen3.8-flash",
        chatHeavyModel = "qwen3.8-max",
        ttsEndpoint = "wss://example.test/tts",
        ttsModel = "qwen-audio-3.0-tts-flash",
        ttsVoice = "longanhuan_v3.6",
        voiceDesignEndpoint = "https://example.test/voice-design",
        voiceDesignModel = "voice-enrollment",
        asrEndpoint = asrEndpoint,
        asrModel = asrModel,
        apiKey = apiKey,
    )
}
