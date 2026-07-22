package apap.oneverandah.phrases

import apap.oneverandah.data.PhrasesCatalog
import apap.oneverandah.phrases.Phrase as PhraseModel
import java.util.concurrent.ConcurrentHashMap

interface PhraseRepository {
    suspend fun findAll(): List<PhraseModel>
    suspend fun findById(id: String): PhraseModel?
    suspend fun save(phrase: PhraseModel)
    suspend fun delete(id: String): Boolean
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
        if (items.isNotEmpty()) return
        val now = System.currentTimeMillis()
        seed.forEachIndexed { index, phrase ->
            items[phrase.id] = phrase.copy(
                createdAt = phrase.createdAt.takeIf { it > 0 } ?: (now + index),
                updatedAt = phrase.updatedAt.takeIf { it > 0 } ?: (now + index),
            )
        }
    }
}
