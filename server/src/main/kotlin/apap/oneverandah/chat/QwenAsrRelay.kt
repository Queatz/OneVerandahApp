package apap.oneverandah.chat

import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class QwenAsrRelay(
    private val config: QwenChatConfig,
    private val client: HttpClient,
) {
    private val log = LoggerFactory.getLogger(QwenAsrRelay::class.java)

    suspend fun relay(browser: DefaultWebSocketServerSession) {
        config.asrUnavailableReason?.let { missingSetting ->
            log.warn("Voice input is unavailable: configure {}", missingSetting)
            browser.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Voice input is unavailable"))
            return
        }

        try {
            val language = browser.call.parameters["language"]
            client.webSocket(
                request = {
                    url(config.asrUrl(language))
                    config.asrHeaders().forEach { (name, value) -> header(name, value) }
                },
            ) {
                relayAsrFrames(browser.incoming, browser.outgoing, incoming, outgoing)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.warn("Qwen realtime ASR relay failed: {}", cause.message)
            runCatching {
                browser.send("""{"type":"error","error":{"message":"Voice input is unavailable"}}""")
                browser.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "ASR relay failed"))
            }
        }
    }
}

internal suspend fun relayAsrFrames(
    browserIncoming: ReceiveChannel<Frame>,
    browserOutgoing: SendChannel<Frame>,
    qwenIncoming: ReceiveChannel<Frame>,
    qwenOutgoing: SendChannel<Frame>,
) = coroutineScope {
    val toQwen = launch {
        try {
            for (frame in browserIncoming) {
                if (frame is Frame.Text) qwenOutgoing.send(Frame.Text(frame.readText()))
            }
        } finally {
            qwenOutgoing.close()
        }
    }
    val toBrowser = launch {
        try {
            for (frame in qwenIncoming) {
                if (frame is Frame.Text) browserOutgoing.send(Frame.Text(frame.readText()))
            }
        } finally {
            browserOutgoing.close()
        }
    }
    joinAll(toQwen, toBrowser)
}