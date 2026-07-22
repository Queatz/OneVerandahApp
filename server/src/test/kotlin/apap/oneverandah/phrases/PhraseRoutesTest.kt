package apap.oneverandah.phrases

import apap.oneverandah.admin.AdminJwt
import apap.oneverandah.config.AppConfig
import apap.oneverandah.data.PhrasesCatalog
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.module
import apap.oneverandah.chat.ChatSystem
import apap.oneverandah.images.ImageSystem
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
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhraseRoutesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun publicPhrasesReturnsSeededDeck() = testApplication {
        val config = AppConfig.load()
        val imageSystem = runBlocking { ImageSystem.create(config) }
        val chatSystem = runBlocking { ChatSystem.create(config) }
        val phraseSystem = runBlocking {
            PhraseSystem.create(config, FakeStorage(), null)
        }
        application {
            module(config, imageSystem, chatSystem, phraseSystem)
        }

        val response = client.get("/api/v1/phrases")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString(PhrasesResponse.serializer(), response.bodyAsText())
        assertEquals(PhrasesCatalog.all.size, body.phrases.size)
        assertTrue(body.phrases.all { it.approved })
    }

    @Test
    fun adminSignInIssuesNeverExpiringJwt() = testApplication {
        val config = AppConfig.load()
        val imageSystem = runBlocking { ImageSystem.create(config) }
        val chatSystem = runBlocking { ChatSystem.create(config) }
        val phraseSystem = runBlocking {
            PhraseSystem.create(config, FakeStorage(), null)
        }
        application {
            module(config, imageSystem, chatSystem, phraseSystem)
        }

        val bad = client.post("/api/v1/admin/signin") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AdminSignInRequest.serializer(), AdminSignInRequest("nope")))
        }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)

        val ok = client.post("/api/v1/admin/signin") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    AdminSignInRequest.serializer(),
                    AdminSignInRequest(config.adminPassword),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        val token = json.decodeFromString(AdminSignInResponse.serializer(), ok.bodyAsText()).token
        assertTrue(AdminJwt.verify(token, config.adminJwtSecret))
        assertTrue(AdminJwt.verify("Bearer $token", config.adminJwtSecret))
        // No exp claim is embedded in our tokens.
        assertTrue(!token.split('.')[1].contains("exp"))
    }

    @Test
    fun adminCanCreateAndDeletePhrase() = testApplication {
        val config = AppConfig.load()
        val imageSystem = runBlocking { ImageSystem.create(config) }
        val chatSystem = runBlocking { ChatSystem.create(config) }
        val phraseSystem = runBlocking {
            PhraseSystem.create(config, FakeStorage(), null)
        }
        application {
            module(config, imageSystem, chatSystem, phraseSystem)
        }

        val signIn = client.post("/api/v1/admin/signin") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    AdminSignInRequest.serializer(),
                    AdminSignInRequest(config.adminPassword),
                ),
            )
        }
        val token = json.decodeFromString(AdminSignInResponse.serializer(), signIn.bodyAsText()).token

        val create = client.post("/api/v1/admin/phrases") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    PhraseUpsertRequest.serializer(),
                    PhraseUpsertRequest(
                        id = "emp-test-phrase",
                        locationKey = "lobby",
                        location = apap.oneverandah.model.LocalizedText("Lobby", "Sảnh"),
                        spoken = apap.oneverandah.model.LocalizedText(
                            "This child greets the morning light.",
                            "Dạ, con chào ánh sáng ban mai.",
                        ),
                        verse = apap.oneverandah.model.LocalizedText("Verse en", "Verse vi"),
                        rawAsk = apap.oneverandah.model.LocalizedText("Say hello.", "Chào hỏi."),
                        approved = true,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val listed = client.get("/api/v1/phrases")
        val body = json.decodeFromString(PhrasesResponse.serializer(), listed.bodyAsText())
        assertTrue(body.phrases.any { it.id == "emp-test-phrase" })

        val deleted = client.delete("/api/v1/admin/phrases/emp-test-phrase") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
    }

    @Test
    fun reactIncrementsEmojiCount() = testApplication {
        val config = AppConfig.load()
        val imageSystem = runBlocking { ImageSystem.create(config) }
        val chatSystem = runBlocking { ChatSystem.create(config) }
        val phraseSystem = runBlocking {
            PhraseSystem.create(config, FakeStorage(), null)
        }
        application {
            module(config, imageSystem, chatSystem, phraseSystem)
        }

        // Ensure seed exists.
        client.get("/api/v1/phrases")
        val id = PhrasesCatalog.all.first().id
        val response = client.post("/api/v1/phrases/$id/react") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(PhraseReactRequest.serializer(), PhraseReactRequest("🌿")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val phrase = json.decodeFromString(Phrase.serializer(), response.bodyAsText())
        assertEquals(1, phrase.reactions["🌿"])
    }

    @Test
    fun adminCanGeneratePhraseImageAndCover() = testApplication {
        val config = AppConfig.load()
        val imageSystem = runBlocking { ImageSystem.create(config) }
        val chatSystem = runBlocking { ChatSystem.create(config) }
        val phraseSystem = runBlocking {
            PhraseSystem.create(config, FakeStorage(), null)
        }
        application {
            module(config, imageSystem, chatSystem, phraseSystem)
        }

        val signIn = client.post("/api/v1/admin/signin") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AdminSignInRequest.serializer(), AdminSignInRequest(config.adminPassword)))
        }
        assertEquals(HttpStatusCode.OK, signIn.status)
        val token = json.decodeFromString(AdminSignInResponse.serializer(), signIn.bodyAsText()).token

        val phraseId = PhrasesCatalog.all.first().id
        val response = client.post("/api/v1/admin/phrases/generate-image") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    GeneratePhraseImageRequest.serializer(),
                    GeneratePhraseImageRequest(
                        prompt = "Soft golden hour at the infinity pool",
                        phraseId = phraseId,
                        asCover = true,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString(GeneratePhraseImageResponse.serializer(), response.bodyAsText())
        assertTrue(body.imageUrl.isNotBlank())
        assertEquals(body.imageUrl, body.coverImageUrl)
        assertEquals(body.imageUrl, body.phrase?.imageUrl)

        val cover = client.get("/api/v1/phrases/cover")
        assertEquals(HttpStatusCode.OK, cover.status)
        val coverBody = json.decodeFromString(PhrasesCoverResponse.serializer(), cover.bodyAsText())
        assertEquals(body.imageUrl, coverBody.imageUrl)
    }
}

private class FakeStorage : StorageService {
    override val kind: String = "fake"
    override suspend fun upload(objectKey: String, image: GeneratedImage): String =
        "https://example.test/$objectKey"
}
