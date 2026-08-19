package apap.oneverandah.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpTimeout

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
        requestTimeout = 0
        endpoint.connectTimeout = 30_000
        endpoint.connectAttempts = 3
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 300_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 300_000
    }
    expectSuccess = false
    configure()
}
