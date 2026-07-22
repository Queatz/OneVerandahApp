package apap.oneverandah.phrases

import apap.oneverandah.data.PhrasesCatalog
import apap.oneverandah.phrases.Phrase as PhraseModel
import java.util.concurrent.ConcurrentHashMap

interface PhraseRepository {
    suspend fun findAll(): List<PhraseModel>
    suspend fun findById(id: String): PhraseModel?
    suspend fun save(phrase: PhraseModel)
    suspend fun delete(id: String): Boolean

    /**
     * Inserts the founding deck only when no real phrases exist yet.
     *
     * Important: this must never overwrite edits, reactions, or admin-created
     * phrases. The internal cover pseudo-document is ignored when deciding
     * emptiness so a cover-only collection still receives the founding deck.
     */
    suspend fun seedIfEmpty(seed: List<PhraseModel> = PhrasesCatalog.all)
}

/** Development / fallback store kept entirely in memory. */
class InMemoryPhraseRepository : PhraseRepository {
    private val items = ConcurrentHashMap<String, PhraseModel>()

    override suspend fun findAll(): List<PhraseModel> =
        items.values.sortedBy { it.createdAt }

    override suspend fun findById(id: String): PhraseModel? = items[id]

    override suspend fun save(phrase: PhraseModel) {
        items[phrase.id] = phrase
    }

    override suspend fun delete(id: String): Boolean = items.remove(id) != null

    override suspend fun seedIfEmpty(seed: List<PhraseModel>) {
        if (hasCatalogPhrases(items.keys)) return
        val now = System.currentTimeMillis()
        seed.forEachIndexed { index, phrase ->
            // Never clobber a row that appeared between the emptiness check and insert.
            if (items.containsKey(phrase.id)) return@forEachIndexed
            items[phrase.id] = phrase.copy(
                createdAt = phrase.createdAt.takeIf { it > 0 } ?: (now + index),
                updatedAt = phrase.updatedAt.takeIf { it > 0 } ?: (now + index),
            )
        }
    }

    companion object {
        /** Pseudo-id used by [apap.oneverandah.phrases.PhraseSystem] for cover URL storage. */
        const val COVER_PHRASE_ID = "__phrases_cover__"

        fun hasCatalogPhrases(ids: Iterable<String>): Boolean =
            ids.any { it != COVER_PHRASE_ID }
    }
}
