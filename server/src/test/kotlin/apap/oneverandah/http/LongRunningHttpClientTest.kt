package apap.oneverandah.http

import io.ktor.client.engine.cio.CIOEngineConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class LongRunningHttpClientTest {

    @Test
    fun disablesCioEngineRequestTimeout() {
        val client = longRunningHttpClient()
        try {
            assertEquals(0L, (client.engine.config as CIOEngineConfig).requestTimeout)
        } finally {
            client.close()
        }
    }
}
