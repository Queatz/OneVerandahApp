package apap.oneverandah.search

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Persistence abstraction for [SearchRecord] analytics.
 *
 * Two implementations exist: [InMemorySearchRepository] (always available, the
 * default) and [apap.oneverandah.search.arango.ArangoSearchRepository] (used
 * when ArangoDB is configured and reachable). Both are safe to call from
 * coroutines.
 */
interface SearchRepository {
    /** Persists a single search query. */
    suspend fun record(record: SearchRecord)

    /** Returns the most recent searches, newest first, capped at [limit]. */
    suspend fun recent(limit: Int): List<SearchRecord>
}

/**
 * Thread-safe in-memory repository. Perfect for local development, tests and as
 * a graceful fallback when ArangoDB is not configured. Data does not survive a
 * restart, which is fine for best-effort search analytics.
 */
class InMemorySearchRepository : SearchRepository {
    private val records = CopyOnWriteArrayList<SearchRecord>()

    override suspend fun record(record: SearchRecord) {
        records.add(record)
    }

    override suspend fun recent(limit: Int): List<SearchRecord> =
        records.sortedByDescending { it.createdAt }.take(limit)
}
