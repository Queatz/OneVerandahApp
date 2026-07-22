package apap.oneverandah.phrases

import apap.oneverandah.config.AppConfig
import apap.oneverandah.config.AppEnv
import apap.oneverandah.data.PhrasesCatalog
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhraseRepositoryTest {

    @Test
    fun seedIfEmptyDoesNotOverwriteReactionsOrEdits() = runBlocking {
        val repo = InMemoryPhraseRepository()
        repo.seedIfEmpty()
        val first = repo.findById(PhrasesCatalog.all.first().id)
        assertNotNull(first)

        val edited = first.copy(
            spoken = first.spoken.copy(en = "Custom spoken line"),
            reactions = mapOf("🌿" to 7, "✨" to 2),
            imageUrl = "https://example.test/custom.png",
            updatedAt = 42L,
        )
        repo.save(edited)

        // Simulate startup seed on every boot.
        repeat(3) { repo.seedIfEmpty() }

        val reloaded = repo.findById(first.id)
        assertNotNull(reloaded)
        assertEquals("Custom spoken line", reloaded.spoken.en)
        assertEquals(7, reloaded.reactions["🌿"])
        assertEquals(2, reloaded.reactions["✨"])
        assertEquals("https://example.test/custom.png", reloaded.imageUrl)
        assertEquals(42L, reloaded.updatedAt)
        assertEquals(PhrasesCatalog.all.size, repo.findAll().size)
    }

    @Test
    fun seedIfEmptyStillRunsWhenOnlyCoverDocumentExists() = runBlocking {
        val repo = InMemoryPhraseRepository()
        val now = System.currentTimeMillis()
        repo.save(
            Phrase(
                id = InMemoryPhraseRepository.COVER_PHRASE_ID,
                locationKey = "cover",
                location = apap.oneverandah.model.LocalizedText(en = "Cover", vi = "Bìa"),
                spoken = apap.oneverandah.model.LocalizedText(en = "cover", vi = "bia"),
                verse = apap.oneverandah.model.LocalizedText(en = "", vi = ""),
                rawAsk = apap.oneverandah.model.LocalizedText(en = "", vi = ""),
                approved = false,
                imageUrl = "https://example.test/cover.png",
                createdAt = now,
                updatedAt = now,
            ),
        )

        repo.seedIfEmpty()

        val all = repo.findAll()
        assertTrue(all.any { it.id == InMemoryPhraseRepository.COVER_PHRASE_ID })
        assertEquals(PhrasesCatalog.all.size + 1, all.size)
        assertEquals(
            "https://example.test/cover.png",
            repo.findById(InMemoryPhraseRepository.COVER_PHRASE_ID)?.imageUrl,
        )
    }

    @Test
    fun productionPhraseSystemRequiresArango() = runBlocking {
        val base = AppConfig.load(File("this-file-does-not-exist.env"))
        val config = base.copy(
            env = AppEnv.PRODUCTION,
            arango = base.arango.copy(host = "127.0.0.1", port = 1, password = "nope"),
            autoStart = false,
        )
        val error = assertFailsWith<IllegalStateException> {
            PhraseSystem.create(config, object : StorageService {
                override val kind: String = "fake"
                override suspend fun upload(objectKey: String, image: GeneratedImage): String =
                    "https://example.test/$objectKey"
            })
        }
        assertTrue(error.message.orEmpty().contains("Production phrases require ArangoDB"))
    }
}
