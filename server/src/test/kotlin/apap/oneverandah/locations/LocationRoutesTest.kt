package apap.oneverandah.locations

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.routes.DEVICE_TOKEN_HEADER
import apap.oneverandah.routes.locationRoutes
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

class LocationRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun publicFeedAndAdminCrud() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { locationRoutes(system, config) }
        }

        val created = system.service.upsert(
            LocationUpsertRequest(name = "River Cafe", category = "Food", status = LocationLogic.STATUS_PLANNING),
        )

        val feed = json.decodeFromString(
            LocationsFeedResponse.serializer(),
            client.get("/api/v1/locations").bodyAsText(),
        )
        assertEquals(1, feed.locations.size)
        assertEquals("River Cafe", feed.locations.single().name)
        assertEquals(LocationLogic.DISPLAY_PLANNING, feed.locations.single().displayStatus)

        val unauth = client.post("/api/v1/admin/locations") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LocationUpsertRequest.serializer(), LocationUpsertRequest(name = "X", category = "Y")))
        }
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)

        val token = AdminJwt.issue(config.adminJwtSecret)
        val updated = client.put("/api/v1/admin/locations/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    LocationUpsertRequest.serializer(),
                    LocationUpsertRequest(
                        name = "River Cafe",
                        category = "Food",
                        status = LocationLogic.STATUS_OPERATING,
                        schedule = listOf(LocationHours(dayOfWeek = 1, openMinutes = 540, closeMinutes = 1080)),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, updated.status)

        val raw = "lan-token"
        val like = client.post("/api/v1/locations/${created.id}/like") {
            header(DEVICE_TOKEN_HEADER, raw)
        }
        assertEquals(HttpStatusCode.OK, like.status)
        val liked = json.decodeFromString(LocationCard.serializer(), like.bodyAsText())
        assertTrue(liked.liked)
        assertEquals(1, liked.likeCount)

        val deleted = client.delete("/api/v1/admin/locations/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/locations/${created.id}").status)
    }

    @Test
    fun commitRequiresDeviceTokenAndPlanningStatus() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { locationRoutes(system, config) }
        }
        val created = system.service.upsert(
            LocationUpsertRequest(name = "Garden", category = "Food", status = LocationLogic.STATUS_PLANNING),
        )
        val missingToken = client.post("/api/v1/locations/${created.id}/commitments") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CommitLocationFundsRequest.serializer(),
                    CommitLocationFundsRequest(50_000, "Yes", "090111"),
                ),
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, missingToken.status)

        val ok = client.post("/api/v1/locations/${created.id}/commitments") {
            header(DEVICE_TOKEN_HEADER, "lan-token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CommitLocationFundsRequest.serializer(),
                    CommitLocationFundsRequest(50_000, "Yes", "090111"),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("090111", system.service.listAdmin().locations.single().commitments.single().zaloNumber)
        assertFalse(ok.bodyAsText().contains("090111"))
    }

    private fun testSystem(): LocationSystem {
        val repo = InMemoryLocationRepository()
        return LocationSystem(
            service = LocationService(repo),
            storage = object : StorageService {
                override val kind: String = "fake"
                override suspend fun upload(objectKey: String, image: GeneratedImage): String =
                    "https://example.test/$objectKey"
            },
        )
    }
}
