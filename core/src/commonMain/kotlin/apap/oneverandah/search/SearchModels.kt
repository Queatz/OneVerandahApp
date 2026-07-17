package apap.oneverandah.search

import kotlinx.serialization.Serializable

/**
 * Shared search-analytics domain and API models.
 *
 * Like the image models these live in `:core` `commonMain` so the exact same
 * shapes are used by the Ktor **server** (persistence + API responses) and by
 * the Compose HTML **web** client (posting queries + decoding history).
 *
 * Searching itself still happens entirely on the client against the bundled
 * catalog; these records exist purely so we can understand what people are
 * looking for.
 */

/**
 * A persisted search query.
 *
 * [id] is the storage key (e.g. ArangoDB `_key`). [query] is the raw text the
 * user typed and [createdAt] is the epoch-millis timestamp the query was
 * recorded at.
 */
@Serializable
data class SearchRecord(
    val id: String,
    val query: String,
    val createdAt: Long = 0,
)

/** Request body for `POST /search`. */
@Serializable
data class SearchRequest(
    val query: String,
)

/** Response body for `POST /search`. */
@Serializable
data class SearchResponse(
    val query: String,
    val recorded: Boolean = true,
)

/** Response body for `GET /search-history`. */
@Serializable
data class SearchHistoryResponse(
    val searches: List<SearchRecord> = emptyList(),
)
