package apap.oneverandah.stories

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.qwen.PlaceholderQwenClient
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.phrases.InMemoryReferencePhotoRepository
import apap.oneverandah.routes.storyRoutes
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

class StoryRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun publicListHidesDraftsAndAdminCanPublish() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                storyRoutes(system, config)
            }
        }

        val created = system.service.create(
            StoryUpsertRequest(
                name = "Lobby Ghost",
                category = "Mystery",
                pages = listOf(StoryPageUpsert(text = "The lift chimed once.")),
            ),
        )

        val publicDraft = client.get("/api/v1/stories")
        assertEquals(HttpStatusCode.OK, publicDraft.status)
        val empty = json.decodeFromString(StoriesResponse.serializer(), publicDraft.bodyAsText())
        assertTrue(empty.stories.isEmpty())

        val unauth = client.post("/api/v1/admin/stories/${created.id}/publish") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StoryPublishRequest.serializer(), StoryPublishRequest("en")))
        }
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)

        val token = AdminJwt.issue(config.adminJwtSecret)
        val published = client.post("/api/v1/admin/stories/${created.id}/publish") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(StoryPublishRequest.serializer(), StoryPublishRequest("en")))
        }
        assertEquals(HttpStatusCode.OK, published.status)
        val book = json.decodeFromString(Story.serializer(), published.bodyAsText())
        assertTrue(book.published)
        assertFalse(book.pages.first().session?.sentences.isNullOrEmpty())

        val listed = client.get("/api/v1/stories")
        val body = json.decodeFromString(StoriesResponse.serializer(), listed.bodyAsText())
        assertEquals(1, body.stories.size)
        assertTrue(body.stories.single().pages.isEmpty())

        val opened = client.get("/api/v1/stories/${created.id}")
        assertEquals(HttpStatusCode.OK, opened.status)
        val detail = json.decodeFromString(Story.serializer(), opened.bodyAsText())
        assertEquals(1, detail.openCount)
        assertEquals("The lift chimed once.", detail.pages.single().text)
    }

    @Test
    fun adminCreateRejectsBlankNameAndMissingBookIsNotFound() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { storyRoutes(system, config) }
        }
        val token = AdminJwt.issue(config.adminJwtSecret)

        val bad = client.post("/api/v1/admin/stories") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    StoryUpsertRequest.serializer(),
                    StoryUpsertRequest(name = " ", category = "Bedtime"),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)

        val missing = client.get("/api/v1/admin/stories/nope") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)

        val publicMissing = client.get("/api/v1/stories/nope")
        assertEquals(HttpStatusCode.NotFound, publicMissing.status)
    }

    @Test
    fun adminCanUpdateAndDelete() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) { json(json) }
            routing { storyRoutes(system, config) }
        }
        val token = AdminJwt.issue(config.adminJwtSecret)
        val created = system.service.create(
            StoryUpsertRequest(name = "Old Title", category = "Social", pages = listOf(StoryPageUpsert(text = "Hi"))),
        )
        val updated = client.put("/api/v1/admin/stories/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    StoryUpsertRequest.serializer(),
                    StoryUpsertRequest(
                        name = "New Title",
                        category = "Bedtime",
                        pages = listOf(StoryPageUpsert(id = created.pages.single().id, text = "Hello again")),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertTrue(updated.bodyAsText().contains("New Title"))

        val deleted = client.delete("/api/v1/admin/stories/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/admin/stories/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.status)
    }

    @Test
    fun generateImageStartsInBackgroundAndCanBePolled() = testApplication {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val config = AppConfig.load()
        val system = testSystem(
            qwen = object : apap.oneverandah.images.qwen.QwenClient {
                override val kind: String = "slow"
                override suspend fun generate(
                    prompt: String,
                    referenceImageUrls: List<String>,
                ): GeneratedImage {
                    gate.await()
                    return GeneratedImage(byteArrayOf(1, 2, 3), "image/png", "png")
                }
            },
        )
        application {
            install(ContentNegotiation) { json(json) }
            routing { storyRoutes(system, config) }
        }
        val token = AdminJwt.issue(config.adminJwtSecret)

        val started = client.post("/api/v1/admin/stories/generate-image") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    GenerateStoryImageRequest.serializer(),
                    GenerateStoryImageRequest(prompt = "Lan by the pool at dusk"),
                ),
            )
        }
        assertEquals(HttpStatusCode.Accepted, started.status)
        val pending = json.decodeFromString(GenerateStoryImageResponse.serializer(), started.bodyAsText())
        assertEquals(StoryLogic.IMAGE_JOB_PENDING, pending.status)
        assertFalse(pending.jobId.isNullOrBlank())
        assertTrue(pending.imageUrl.isBlank())

        val stillPending = client.get("/api/v1/admin/stories/generate-image/${pending.jobId}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, stillPending.status)
        assertEquals(
            StoryLogic.IMAGE_JOB_PENDING,
            json.decodeFromString(GenerateStoryImageResponse.serializer(), stillPending.bodyAsText()).status,
        )

        gate.complete(Unit)
        var ready: GenerateStoryImageResponse? = null
        repeat(50) {
            val polled = client.get("/api/v1/admin/stories/generate-image/${pending.jobId}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val body = json.decodeFromString(GenerateStoryImageResponse.serializer(), polled.bodyAsText())
            if (body.status == StoryLogic.IMAGE_JOB_READY) {
                ready = body
                return@repeat
            }
            kotlinx.coroutines.delay(20)
        }
        assertEquals(StoryLogic.IMAGE_JOB_READY, ready?.status)
        assertTrue(ready?.imageUrl?.isNotBlank() == true)
    }

    @Test
    fun adminCanRenameCharacter() = testApplication {
        val config = AppConfig.load()
        val system = testSystem()
        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                storyRoutes(system, config)
            }
        }

        val created = system.service.addCharacter("Minh", "https://example.test/minh.png")
        val token = AdminJwt.issue(config.adminJwtSecret)
        val renamed = client.put("/api/v1/admin/story-characters/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    StoryCharacterRenameRequest.serializer(),
                    StoryCharacterRenameRequest("Minh Anh"),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, renamed.status)
        val body = json.decodeFromString(StoryCharacter.serializer(), renamed.bodyAsText())
        assertEquals("Minh Anh", body.name)

        val listed = client.get("/api/v1/admin/story-characters") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val characters = json.decodeFromString(StoryCharactersResponse.serializer(), listed.bodyAsText())
        assertEquals(listOf("Minh Anh"), characters.characters.map { it.name })
    }

    private fun testSystem(
        qwen: apap.oneverandah.images.qwen.QwenClient = PlaceholderQwenClient(),
    ): StorySystem {
        val stories = InMemoryStoryRepository()
        val characters = InMemoryStoryCharacterRepository()
        val service = StoryService(
            stories = stories,
            characters = characters,
            tokenizer = StoryTokenizer { text, language ->
                StudySessionResponse(
                    text = text,
                    textLanguageTag = language,
                    sentences = listOf(
                        StudySentence(
                            text = text,
                            tokens = listOf(StudyToken(text = text, status = StudyTokenStatus.READY)),
                        ),
                    ),
                )
            },
        )
        return StorySystem(
            service = service,
            stories = stories,
            characters = characters,
            referencePhotos = InMemoryReferencePhotoRepository(),
            storage = object : StorageService {
                override val kind: String = "fake"
                override suspend fun upload(objectKey: String, image: GeneratedImage): String =
                    "https://example.test/$objectKey"
            },
            qwen = qwen,
        )
    }
}
