package apap.oneverandah.api

import apap.oneverandah.images.ImagesResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
}
