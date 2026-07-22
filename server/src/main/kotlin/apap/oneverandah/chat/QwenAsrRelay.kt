package apap.oneverandah.chat

import apap.oneverandah.config.QwenChatConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

private const val warmIdleTtlMs = 60_000L

/**
 * Relays browser realtime-ASR WebSocket traffic to Qwen while keeping credentials
 * server-side.
 *
 * Browser audio is accepted as raw PCM binary frames and converted to Qwen's
 * required base64 JSON `input_audio_buffer.append` events. Idle upstream sockets
 * are kept warm for [warmIdleTtlMs] so the next hold can skip the cold handshake.
 */
class QwenAsrRelay(
    private val config: QwenChatConfig,
    private val client: HttpClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val log = LoggerFactory.getLogger(QwenAsrRelay::class.java)
    private val pool = QwenAsrWarmPool(config, client, scope)

    suspend fun startup() {
        if (!config.asrEnabled) return
        pool.prewarm()
    }

    fun shutdown() {
        pool.close()
    }

    suspend fun relay(browser: DefaultWebSocketServerSession) {
        config.asrUnavailableReason?.let { missingSetting ->
            log.warn("Voice input is unavailable: configure {}", missingSetting)
            browser.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Voice input is unavailable"))
            return
        }

        // Language is applied by the browser via session.update; keep the upstream URL
        // language-agnostic so warm sockets can be reused across locales.
        val upstreamStartedAt = System.nanoTime()
        val upstream = try {
            pool.borrow()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.warn("Qwen realtime ASR upstream connect failed: {}", cause.message)
            runCatching {
                browser.send("""{"type":"error","error":{"message":"Voice input is unavailable"}}""")
                browser.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "ASR relay failed"))
            }
            return
        }
        log.info(
            "ASR upstream ready in {}ms (warmHit={}) language={}",
            (System.nanoTime() - upstreamStartedAt) / 1_000_000,
            upstream.fromPool,
            browser.call.parameters["language"] ?: "-",
        )

        val returnToPool = AtomicBoolean(true)
        try {
            relayAsrFrames(
                browserIncoming = browser.incoming,
                browserOutgoing = browser.outgoing,
                qwenIncoming = upstream.session.incoming,
                qwenOutgoing = upstream.session.outgoing,
                onBrowserText = { text ->
                    if (text.contains("\"session.finish\"")) {
                        returnToPool.set(false)
                    }
                },
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            returnToPool.set(false)
            log.warn("Qwen realtime ASR relay failed: {}", cause.message)
            runCatching {
                browser.send("""{"type":"error","error":{"message":"Voice input is unavailable"}}""")
                browser.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "ASR relay failed"))
            }
        } finally {
            if (returnToPool.get() && upstream.session.isActive) {
                pool.release(upstream.session)
            } else {
                runCatching {
                    if (upstream.session.isActive) {
                        upstream.session.send("""{"type":"session.finish"}""")
                    }
                    upstream.session.close()
                }
            }
        }
    }
}

internal data class BorrowedAsrSession(
    val session: DefaultClientWebSocketSession,
    val fromPool: Boolean,
)

/**
 * Single-slot warm pool for Qwen realtime ASR upstream sockets.
 *
 * Upstream sockets are opened without a language query param; the browser sets
 * language through `session.update`, so one warm socket can serve any locale.
 */
internal class QwenAsrWarmPool(
    private val config: QwenChatConfig,
    private val client: HttpClient,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(QwenAsrWarmPool::class.java)
    private val mutex = Mutex()
    private var idleSession: DefaultClientWebSocketSession? = null
    private var idleCloseJob: Job? = null
    private val closed = AtomicBoolean(false)

    suspend fun prewarm() {
        if (closed.get() || !config.asrEnabled) return
        try {
            val session = openSession()
            release(session)
            log.info("ASR warm pool pre-warmed")
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.info("ASR warm pool pre-warm skipped: {}", cause.message)
        }
    }

    suspend fun borrow(): BorrowedAsrSession {
        if (closed.get()) error("ASR warm pool is closed")
        mutex.withLock {
            val session = idleSession
            if (session != null && session.isActive) {
                idleSession = null
                idleCloseJob?.cancel()
                idleCloseJob = null
                return BorrowedAsrSession(session, fromPool = true)
            }
            if (session != null) {
                idleSession = null
                idleCloseJob?.cancel()
                idleCloseJob = null
                runCatching { session.close() }
            }
        }
        return BorrowedAsrSession(openSession(), fromPool = false)
    }

    suspend fun release(session: DefaultClientWebSocketSession) {
        if (closed.get() || !session.isActive) {
            runCatching { session.close() }
            return
        }
        mutex.withLock {
            val existing = idleSession
            if (existing != null) {
                runCatching { session.close() }
                return
            }
            idleSession = session
            idleCloseJob?.cancel()
            idleCloseJob = scope.launch {
                delay(warmIdleTtlMs)
                mutex.withLock {
                    if (idleSession === session) {
                        idleSession = null
                        runCatching {
                            if (session.isActive) {
                                session.send("""{"type":"session.finish"}""")
                            }
                            session.close()
                        }
                        log.info("ASR warm pool idle socket closed after {}ms", warmIdleTtlMs)
                    }
                }
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.launch {
            mutex.withLock {
                idleCloseJob?.cancel()
                idleCloseJob = null
                val session = idleSession
                idleSession = null
                if (session != null) {
                    runCatching {
                        if (session.isActive) {
                            session.send("""{"type":"session.finish"}""")
                        }
                        session.close()
                    }
                }
            }
        }
    }

    private suspend fun openSession(): DefaultClientWebSocketSession {
        val startedAt = System.nanoTime()
        val session = client.webSocketSession {
            url(config.asrUrl(null))
            config.asrHeaders().forEach { (name, value) -> header(name, value) }
        }
        log.info(
            "ASR upstream socket opened in {}ms",
            (System.nanoTime() - startedAt) / 1_000_000,
        )
        return session
    }
}

internal suspend fun relayAsrFrames(
    browserIncoming: ReceiveChannel<Frame>,
    browserOutgoing: SendChannel<Frame>,
    qwenIncoming: ReceiveChannel<Frame>,
    qwenOutgoing: SendChannel<Frame>,
    onBrowserText: (String) -> Unit = {},
    closeUpstreamOnExit: Boolean = false,
) = coroutineScope {
    val toQwen = launch {
        try {
            for (frame in browserIncoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        onBrowserText(text)
                        qwenOutgoing.send(Frame.Text(text))
                    }
                    is Frame.Binary -> {
                        // Browser sends raw PCM16 bytes; Qwen still expects base64 JSON.
                        val audio = Base64.getEncoder().encodeToString(frame.readBytes())
                        qwenOutgoing.send(
                            Frame.Text("""{"type":"input_audio_buffer.append","audio":"$audio"}"""),
                        )
                    }
                    else -> Unit
                }
            }
        } finally {
            if (closeUpstreamOnExit) {
                qwenOutgoing.close()
            }
        }
    }
    val toBrowser = launch {
        try {
            for (frame in qwenIncoming) {
                if (!coroutineContext.isActive) break
                if (frame is Frame.Text) {
                    browserOutgoing.send(Frame.Text(frame.readText()))
                }
            }
        } finally {
            browserOutgoing.close()
        }
    }
    try {
        toQwen.join()
    } finally {
        toBrowser.cancel()
        joinAll(toQwen, toBrowser)
    }
}
