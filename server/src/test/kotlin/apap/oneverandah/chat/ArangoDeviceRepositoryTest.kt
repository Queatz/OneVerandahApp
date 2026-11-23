package apap.oneverandah.chat

import apap.oneverandah.config.ArangoConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArangoDeviceRepositoryTest {
    @Test
    fun `touch retries write conflict and succeeds`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine {
            requestCount++
            if (requestCount == 1) {
                respond(
                    content = WRITE_CONFLICT,
                    status = HttpStatusCode.Conflict,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        })
        val repository = ArangoDeviceRepository(config(), client)

        repository.touch("token-hash", 123L)

        assertEquals(2, requestCount)
        client.close()
    }

    @Test
    fun `touch gives up after repeated write conflicts`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine {
            requestCount++
            respond(
                content = WRITE_CONFLICT,
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        val repository = ArangoDeviceRepository(config(), client)

        assertFailsWith<ArangoDeviceException> {
            repository.touch("token-hash", 123L)
        }

        assertEquals(3, requestCount)
        client.close()
    }

    private fun config() = ArangoConfig(
        host = "localhost",
        port = 8529,
        database = "test",
        user = "test",
        password = "test",
        useSsl = false,
    )

    private companion object {
        const val WRITE_CONFLICT = """{"code":409,"error":true,"errorMessage":"AQL: write-write conflict [node #6: UpdateNode] (while executing)","errorNum":1200}"""
    }
}