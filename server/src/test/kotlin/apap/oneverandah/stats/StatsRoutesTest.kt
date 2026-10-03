package apap.oneverandah.stats

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.config.AppConfig
import apap.oneverandah.routes.DEVICE_TOKEN_HEADER
import apap.oneverandah.routes.statsRoutes
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun visitPingRequiresDeviceTokenAndAdminStatsRequiresAuth() = testApplication {
        val config = AppConfig.load()
        val system = StatsSystem(StatsService(InMemoryVisitRepository()))
        application {
            install(ContentNegotiation) { json(json) }
            routing { statsRoutes(system, config) }
        }

        val missingToken = client.post("/api/v1/visits") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(VisitPingRequest.serializer(), VisitPingRequest(path = "/chat", languageTag = "en")))
        }
        assertEquals(HttpStatusCode.Unauthorized, missingToken.status)

        val ping = client.post("/api/v1/visits") {
            header(DEVICE_TOKEN_HEADER, "visitor-token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(VisitPingRequest.serializer(), VisitPingRequest(path = "/chat", languageTag = "en")))
        }
        assertEquals(HttpStatusCode.OK, ping.status)
        assertTrue(json.decodeFromString(VisitPingResponse.serializer(), ping.bodyAsText()).ok)

        val unauth = client.get("/api/v1/admin/stats")
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)

        val token = AdminJwt.issue(config.adminJwtSecret)
        val stats = client.get("/api/v1/admin/stats") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, stats.status)
        val body = json.decodeFromString(AdminStatsResponse.serializer(), stats.bodyAsText())
        assertEquals("UTC+7", body.timezone)
        assertEquals(30, body.daily.size)
        assertEquals(1, body.totals.visitsAllTime)
        assertEquals(1, body.totals.visitorsAllTime)
        assertEquals("/chat", body.topPaths.single().key)
    }
}
