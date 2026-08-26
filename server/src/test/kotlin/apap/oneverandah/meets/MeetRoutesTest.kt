package apap.oneverandah.meets

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.chat.DeviceService
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.routes.DEVICE_TOKEN_HEADER
import apap.oneverandah.routes.meetRoutes
import io.ktor.client.request.delete
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

class MeetRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun publicFeedHidesMeetUntilAdminApproves() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { meetRoutes(system, config) }
        }

        val created = system.service.create(
            "Lan",
            "Coffee in the lobby",
            "090111",
            3,
            "https://example.test/lan.jpg",
            DeviceService.hashToken("lan-token"),
        )

        val hidden = json.decodeFromString(
            MeetsFeedResponse.serializer(),
            client.get("/api/v1/meets").bodyAsText(),
        )
        assertTrue(hidden.meets.isEmpty())

        val unauth = client.post("/api/v1/admin/meets/${created.id}/approve")
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)

        val token = AdminJwt.issue(config.adminJwtSecret)
        val approved = client.post("/api/v1/admin/meets/${created.id}/approve") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, approved.status)

        val feed = json.decodeFromString(
            MeetsFeedResponse.serializer(),
            client.get("/api/v1/meets").bodyAsText(),
        )
        assertEquals(1, feed.meets.size)
        assertEquals("Lan", feed.meets.single().name)
        assertEquals("Coffee in the lobby", feed.meets.single().pitch)
    }

    @Test
    fun pitchRequiresDeviceTokenAndNotifiesRemainingUnchanged() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { meetRoutes(system, config) }
        }
        val created = system.service.create("Lan", "Coffee", "090111", 2, "lan.jpg", "hash-lan")
        system.service.approve(created.id)

        val missing = client.post("/api/v1/meets/${created.id}/pitches") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SendMeetPitchRequest.serializer(), SendMeetPitchRequest("Hi", "090222")))
        }
        assertEquals(HttpStatusCode.Unauthorized, missing.status)

        val pitched = client.post("/api/v1/meets/${created.id}/pitches") {
            header(DEVICE_TOKEN_HEADER, "visitor-token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SendMeetPitchRequest.serializer(), SendMeetPitchRequest("Hi Lan", "090222")))
        }
        assertEquals(HttpStatusCode.OK, pitched.status)
        val detail = json.decodeFromString(MeetDetailResponse.serializer(), pitched.bodyAsText())
        assertEquals(2, detail.meet.remainingPitches)
        assertTrue(!detail.canPitch)
    }

    @Test
    fun adminCanCompletePitchAndResetRemaining() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { meetRoutes(system, config) }
        }
        val created = system.service.create("Lan", "Coffee", "090111", 3, "lan.jpg", "hash-lan")
        system.service.approve(created.id)
        val pitch = system.service.sendPitch(created.id, "Let's walk", "090222", DeviceService.hashToken("visitor"))
        val token = AdminJwt.issue(config.adminJwtSecret)

        val completed = client.post("/api/v1/admin/meet-pitches/${pitch.id}/complete") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, completed.status)

        val afterComplete = json.decodeFromString(
            MeetDetailResponse.serializer(),
            client.get("/api/v1/meets/${created.id}").bodyAsText(),
        )
        assertEquals(2, afterComplete.meet.remainingPitches)

        val reset = client.post("/api/v1/admin/meets/${created.id}/reset") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, reset.status)
        val afterReset = json.decodeFromString(Meet.serializer(), reset.bodyAsText())
        assertEquals(3, afterReset.remainingPitches)

        val deleted = client.delete("/api/v1/admin/meets/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/meets/${created.id}").status)
    }

    @Test
    fun ownerSeesPendingOnFeed() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { meetRoutes(system, config) }
        }
        val raw = "lan-token"
        system.service.create("Lan", "Coffee", "090111", 3, "lan.jpg", DeviceService.hashToken(raw))

        val mine = json.decodeFromString(
            MeetsFeedResponse.serializer(),
            client.get("/api/v1/meets") { header(DEVICE_TOKEN_HEADER, raw) }.bodyAsText(),
        )
        assertEquals(1, mine.inReview.size)
        assertTrue(mine.inReview.single().mine)
        assertEquals(MeetLogic.STATUS_PENDING, mine.inReview.single().status)
    }

    private fun testSystem(): MeetSystem {
        val repo = InMemoryMeetRepository()
        return MeetSystem(
            service = MeetService(repo),
            storage = object : StorageService {
                override val kind: String = "fake"
                override suspend fun upload(objectKey: String, image: GeneratedImage): String =
                    "https://example.test/$objectKey"
            },
        )
    }
}
