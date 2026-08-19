package apap.oneverandah.challenges

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.chat.DeviceService
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.routes.DEVICE_TOKEN_HEADER
import apap.oneverandah.routes.challengeRoutes
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChallengeRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun publicFeedHidesReviewUntilAdminPublishes() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { challengeRoutes(system, config) }
        }

        val (_, created) = system.service.beginUpload(null, "Lan", DeviceService.hashToken("lan-token"))
        system.service.completeProcessing(created.id, ChallengeTranscript("hello pool"), "Pool laps")

        val hidden = client.get("/api/v1/challenges")
        val empty = json.decodeFromString(ChallengesFeedResponse.serializer(), hidden.bodyAsText())
        assertTrue(empty.challenges.isEmpty())

        val unauth = client.post("/api/v1/admin/submissions/${created.id}/publish")
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)

        val token = AdminJwt.issue(config.adminJwtSecret)
        val published = client.post("/api/v1/admin/submissions/${created.id}/publish") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, published.status)

        val feed = json.decodeFromString(
            ChallengesFeedResponse.serializer(),
            client.get("/api/v1/challenges").bodyAsText(),
        )
        assertEquals(1, feed.challenges.size)
        assertEquals("Pool laps", feed.challenges.single().name)
    }

    @Test
    fun voteRequiresDeviceToken() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { challengeRoutes(system, config) }
        }
        val (_, created) = system.service.beginUpload(null, "Lan", DeviceService.hashToken("lan-token"))
        system.service.completeProcessing(created.id, ChallengeTranscript("hi there"), "Hi there")
        system.service.publish(created.id)

        val missing = client.post("/api/v1/challenges/${created.challengeId}/submissions/${created.id}/vote") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChallengeVoteRequest.serializer(), ChallengeVoteRequest("heart")))
        }
        assertEquals(HttpStatusCode.Unauthorized, missing.status)

        val voted = client.post("/api/v1/challenges/${created.challengeId}/submissions/${created.id}/vote") {
            header(DEVICE_TOKEN_HEADER, "voter-token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChallengeVoteRequest.serializer(), ChallengeVoteRequest("wow")))
        }
        assertEquals(HttpStatusCode.OK, voted.status)
        val detail = json.decodeFromString(ChallengeDetailResponse.serializer(), voted.bodyAsText())
        assertEquals(1, detail.submissions.single().voteCount)
        assertEquals(ChallengeLogic.EMOJI_WOW, detail.submissions.single().myEmoji)
    }

    @Test
    fun ownerSeesInReviewOnFeed() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { challengeRoutes(system, config) }
        }
        val raw = "lan-token"
        val (_, created) = system.service.beginUpload(null, "Lan", DeviceService.hashToken(raw))
        system.service.completeProcessing(created.id, ChallengeTranscript("hello"), "Hello day")

        val mine = json.decodeFromString(
            ChallengesFeedResponse.serializer(),
            client.get("/api/v1/challenges") { header(DEVICE_TOKEN_HEADER, raw) }.bodyAsText(),
        )
        assertEquals(1, mine.inReview.size)
        assertTrue(mine.inReview.single().mine)
        assertEquals(ChallengeLogic.STATUS_IN_REVIEW, mine.inReview.single().status)
    }

    @Test
    fun adminCanRenameAndDelete() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { challengeRoutes(system, config) }
        }
        val (_, created) = system.service.beginUpload(null, "Lan", "hash")
        system.service.completeProcessing(created.id, ChallengeTranscript("hello"), "Old name")
        system.service.publish(created.id)
        val token = AdminJwt.issue(config.adminJwtSecret)

        val renamed = client.post("/api/v1/admin/challenges/${created.challengeId}/rename") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChallengeRenameRequest.serializer(), ChallengeRenameRequest("New name")))
        }
        assertEquals(HttpStatusCode.OK, renamed.status)

        val deleted = client.delete("/api/v1/admin/challenges/${created.challengeId}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/challenges/${created.challengeId}").status)
    }

    private fun testSystem(): ChallengeSystem {
        val repo = InMemoryChallengeRepository()
        return ChallengeSystem(
            service = ChallengeService(repo),
            storage = object : StorageService {
                override val kind: String = "fake"
                override suspend fun upload(objectKey: String, image: GeneratedImage): String =
                    "https://example.test/$objectKey"
            },
            transcriber = ChallengeTranscriber { _, _, _ -> ChallengeTranscript("hello") },
            namer = ChallengeNamer { "Pool laps" },
            media = PassthroughChallengeMedia(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }
}
