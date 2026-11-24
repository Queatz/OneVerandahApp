package apap.oneverandah.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.Dispatchers

/**
 * HTTP client for Qwen / DashScope calls that regularly exceed 15 seconds.
 *
 * CIO's engine-level [CIOEngineConfig.requestTimeout] defaults to 15s and is
 * independent of the [HttpTimeout] plugin. When it fires, CIO closes the
 * socket and the next parse fails with "the server prematurely closed the
 * connection". Disable that cutoff (0) and let [HttpTimeout] own the budget.
 */
internal fun longRunningHttpClient(
    configure: HttpClientConfig<CIOEngineConfig>.() -> Unit = {},
): HttpClient = HttpClient(CIO) {
    engine {
        dispatcher = Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors().coerceIn(4, 16))
        maxConnectionsCount = 512
        endpoint.maxConnectionsPerRoute = 32
        requestTimeout = 0
        endpoint.connectTimeout = 30_000
        endpoint.connectAttempts = 3
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 1_200_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 1_200_000
    }
    expectSuccess = false
    configure()
}

/** HTTP client for short API and ArangoDB requests, isolated from provider calls. */
internal fun shortHttpClient(): HttpClient = HttpClient(CIO) {
    engine {
        dispatcher = Dispatchers.IO.limitedParallelism(16)
        maxConnectionsCount = 512
        endpoint.maxConnectionsPerRoute = 32
        requestTimeout = 30_000
        endpoint.connectTimeout = 10_000
        endpoint.connectAttempts = 2
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }
    expectSuccess = false
}

/** Dedicated CIO engine for persistent realtime ASR WebSocket connections. */
internal fun websocketHttpClient(): HttpClient = HttpClient(CIO) {
    engine {
        dispatcher = Dispatchers.IO.limitedParallelism(2)
        maxConnectionsCount = 32
        endpoint.maxConnectionsPerRoute = 16
        requestTimeout = 0
        endpoint.connectTimeout = 30_000
        endpoint.connectAttempts = 3
    }
    install(WebSockets)
    expectSuccess = false
}
