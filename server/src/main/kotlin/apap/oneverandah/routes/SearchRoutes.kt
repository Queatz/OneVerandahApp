package apap.oneverandah.routes

import apap.oneverandah.images.ApiError
import apap.oneverandah.images.ImageSystem
import apap.oneverandah.search.SearchHistoryResponse
import apap.oneverandah.search.SearchRecord
import apap.oneverandah.search.SearchRequest
import apap.oneverandah.search.SearchResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** How many recent searches `GET /search-history` returns. */
private const val SEARCH_HISTORY_LIMIT = 20

/**
 * Registers the search-analytics API.
 *
 * Searching itself happens entirely on the client against the bundled catalog;
 * these endpoints only record what people are searching for so we can learn
 * from it.
 *
 * Endpoints:
 *  - `POST /search` — records a single (non-blank) search query.
 *  - `GET  /search-history` — returns the latest 20 recorded searches.
 */
fun Route.searchRoutes(system: ImageSystem) {
    val repository = system.searchRepository

    // Record a search query.
    post("/search") {
        val request = runCatching { call.receive<SearchRequest>() }.getOrNull()
        val query = request?.query?.trim()
        if (query.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ApiError("A non-blank 'query' is required"))
            return@post
        }
        val now = System.currentTimeMillis()
        repository.record(
            SearchRecord(
                // Timestamp + random suffix keeps every recorded search distinct
                // even when several arrive within the same millisecond.
                id = "search_${now}_${java.util.UUID.randomUUID()}",
                query = query,
                createdAt = now,
            ),
        )
        call.respond(HttpStatusCode.Accepted, SearchResponse(query = query, recorded = true))
    }

    // Return the most recent searches (newest first).
    get("/search-history") {
        val searches = repository.recent(SEARCH_HISTORY_LIMIT)
        call.respond(SearchHistoryResponse(searches))
    }
}
