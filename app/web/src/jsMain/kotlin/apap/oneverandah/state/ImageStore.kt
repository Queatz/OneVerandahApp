package apap.oneverandah.state

import androidx.compose.runtime.mutableStateMapOf
import apap.oneverandah.api.ImageApi
import apap.oneverandah.images.ImageDto
import apap.oneverandah.images.ImageStatus

/**
 * Global, Compose-observable store of generated cover images keyed by their
 * target id (a category or service id).
 *
 * Composables that call [coverFor] automatically recompose when [refresh]
 * updates the underlying state map, so covers "pop in" as the backend finishes
 * generating them (see the polling loop in `Main.kt`).
 */
object ImageStore {
    private val byTargetId = mutableStateMapOf<String, ImageDto>()

    /** Fetches the latest images from the backend and merges them into the store. */
    suspend fun refresh() {
        val response = ImageApi.fetchImages()
        response.images.forEach { image -> byTargetId[image.targetId] = image }
    }

    /**
     * Returns the ready-to-display cover URL for [targetId], or `null` when the
     * image is missing, not finished, or has no URL yet (callers then fall back
     * to the Material icon / default cover).
     */
    fun coverFor(targetId: String): String? {
        val image = byTargetId[targetId] ?: return null
        val url = image.imageUrl
        return if (image.status == ImageStatus.COMPLETED && !url.isNullOrBlank()) url else null
    }
}
