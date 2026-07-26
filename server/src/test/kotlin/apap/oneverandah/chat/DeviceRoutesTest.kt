package apap.oneverandah.chat

import apap.oneverandah.api.DeviceTokenResponse
import apap.oneverandah.routes.deviceRoutes
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class DeviceRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `POST device issues a long token`() = testApplication {
        val service = DeviceService(InMemoryDeviceRepository())
        application {
            install(ContentNegotiation) { json() }
            routing { deviceRoutes(service) }
        }

        val response = client.post("/api/device")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString(DeviceTokenResponse.serializer(), response.bodyAsText())
        assertTrue(body.deviceToken.length >= 32)
        assertTrue(service.findValid(body.deviceToken) != null)
    }

    @Test
    fun `repeat POST device creates distinct tokens`() = testApplication {
        val service = DeviceService(InMemoryDeviceRepository())
        application {
            install(ContentNegotiation) { json() }
            routing { deviceRoutes(service) }
        }

        val first = json.decodeFromString(
            DeviceTokenResponse.serializer(),
            client.post("/api/device").bodyAsText(),
        )
        val second = json.decodeFromString(
            DeviceTokenResponse.serializer(),
            client.post("/api/device").bodyAsText(),
        )
        assertNotEquals(first.deviceToken, second.deviceToken)
    }
}
