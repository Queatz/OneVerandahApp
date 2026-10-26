package apap.oneverandah.http

import io.ktor.client.engine.cio.CIOEngineConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LongRunningHttpClientTest {

    @Test
    fun disablesCioEngineRequestTimeout() {
        val client = longRunningHttpClient()
        try {
            val config = client.engine.config as CIOEngineConfig
            assertEquals(0L, config.requestTimeout)
            assertTrue(config.dispatcher != null)
            assertEquals(512, config.maxConnectionsCount)
            assertEquals(32, config.endpoint.maxConnectionsPerRoute)
        } finally {
            client.close()
        }
    }

    @Test
    fun shortClientHasItsOwnBoundedRequestBudget() {
        val client = shortHttpClient()
        try {
            assertEquals(30_000L, (client.engine.config as CIOEngineConfig).requestTimeout)
        } finally {
            client.close()
        }
    }
}
