package apap.oneverandah.api

import apap.oneverandah.images.ImagesResponse
import apap.oneverandah.search.SearchHistoryResponse
import apap.oneverandah.search.SearchRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.browser.window
import kotlinx.serialization.json.Json

/**
 * Small client for the backend image API.
 *
 * The base URL is resolved from the runtime environment so the same web build
 * works in development (talking to the local Ktor server on port 7071) and in
 * production (same-origin, with nginx proxying `/api` and `/images` to the
 * backend). It can be overridden by setting `window.__API_BASE__`.
 */
object ImageApi {
    private val client by lazy { HttpClient() }
    private val json = Json { ignoreUnknownKeys = true }

    val baseUrl: String by lazy { resolveBaseUrl() }

    val apiBaseUrl = "https://api.oneverandah.app"

    private fun resolveBaseUrl(): String {
        val override = window.asDynamic().__API_BASE__
        if (override != null && override is String && override.isNotBlank()) {
            return override.trimEnd('/')
        }
        val hostname = window.location.hostname
        return if (hostname == "localhost" || hostname == "127.0.0.1") {
            "http://localhost:7071"
        } else {
            // Same origin; a reverse proxy is expected to route /api and /images.
            ""
        }
    }

    /** Fetches the current set of images, or an empty response on any error. */
    suspend fun fetchImages(): ImagesResponse = try {
        val text = client.get("$apiBaseUrl/api/v1/images").bodyAsText()
        json.decodeFromString(ImagesResponse.serializer(), text)
    } catch (e: Throwable) {
        console.error("Failed to load images: ${e.message}")
        ImagesResponse(emptyList())
    }

    /**
     * Records a search query for analytics. Best-effort: any failure is logged
     * and swallowed so it never disrupts the (client-side) search experience.
     */
    suspend fun logSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        try {
            client.post("$apiBaseUrl/search") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SearchRequest.serializer(), SearchRequest(trimmed)))
            }
        } catch (e: Throwable) {
            console.error("Failed to log search: ${e.message}")
        }
    }

    /** Fetches the latest recorded searches, or an empty response on any error. */
    suspend fun fetchSearchHistory(): SearchHistoryResponse = try {
        val text = client.get("$apiBaseUrl/search-history").bodyAsText()
        json.decodeFromString(SearchHistoryResponse.serializer(), text)
    } catch (e: Throwable) {
        console.error("Failed to load search history: ${e.message}")
        SearchHistoryResponse(emptyList())
    }
}
