package apap.oneverandah.clubs

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.routes.DEVICE_TOKEN_HEADER
import apap.oneverandah.routes.clubRoutes
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClubRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun publicFeedAndAdminCrud() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { clubRoutes(system, config) }
        }

        val created = system.service.upsert(
            ClubUpsertRequest(name = "Sunrise Yoga", category = "Wellness", hostZalo = "090000"),
        )

        val feed = json.decodeFromString(
            ClubsFeedResponse.serializer(),
            client.get("/api/v1/clubs").bodyAsText(),
        )
        assertEquals(1, feed.clubs.size)
        assertEquals("Sunrise Yoga", feed.clubs.single().name)
        assertEquals("090000", feed.clubs.single().hostZalo)

        val unauth = client.post("/api/v1/admin/clubs") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ClubUpsertRequest.serializer(), ClubUpsertRequest(name = "X", category = "Y")))
        }
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)

        val token = AdminJwt.issue(config.adminJwtSecret)
        val updated = client.put("/api/v1/admin/clubs/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ClubUpsertRequest.serializer(),
                    ClubUpsertRequest(
                        name = "Sunrise Yoga",
                        category = "Wellness",
                        tagline = "Breathe",
                        joinQuestions = listOf("Why join?"),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, updated.status)

        val deleted = client.delete("/api/v1/admin/clubs/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/clubs/${created.id}").status)
    }

    @Test
    fun joinAndCreateRequestRequireDeviceToken() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { clubRoutes(system, config) }
        }
        val created = system.service.upsert(
            ClubUpsertRequest(name = "Run Club", category = "Sport", joinQuestions = listOf("Pace?")),
        )

        val missingToken = client.post("/api/v1/clubs/${created.id}/joins") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JoinClubRequest.serializer(),
                    JoinClubRequest(name = "Lan", zaloNumber = "090111", answers = listOf("Easy"), message = "Hi"),
                ),
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, missingToken.status)

        val ok = client.post("/api/v1/clubs/${created.id}/joins") {
            header(DEVICE_TOKEN_HEADER, "lan-token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JoinClubRequest.serializer(),
                    JoinClubRequest(name = "Lan", zaloNumber = "090111", answers = listOf("Easy"), message = "Hi"),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("090111", system.service.listAdmin().joins.single().zaloNumber)
        assertFalse(ok.bodyAsText().contains("090111"))

        val createMissing = client.post("/api/v1/clubs/requests") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateClubRequestPayload.serializer(),
                    CreateClubRequestPayload(name = "Chess", hostName = "Minh", hostZalo = "090222"),
                ),
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, createMissing.status)

        val createdRequest = client.post("/api/v1/clubs/requests") {
            header(DEVICE_TOKEN_HEADER, "minh-token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateClubRequestPayload.serializer(),
                    CreateClubRequestPayload(name = "Chess", hostName = "Minh", hostZalo = "090222"),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, createdRequest.status)
        assertEquals(1, system.service.listAdmin().createRequests.size)

        val token = AdminJwt.issue(config.adminJwtSecret)
        val requestId = system.service.listAdmin().createRequests.single().id
        val dismissed = client.delete("/api/v1/admin/clubs/requests/$requestId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, dismissed.status)
        assertTrue(system.service.listAdmin().createRequests.isEmpty())
    }

    private fun testSystem(): ClubSystem {
        val repo = InMemoryClubRepository()
        return ClubSystem(
            service = ClubService(repo),
            storage = object : StorageService {
                override val kind: String = "fake"
                override suspend fun upload(objectKey: String, image: GeneratedImage): String =
                    "https://example.test/$objectKey"
            },
        )
    }
}
