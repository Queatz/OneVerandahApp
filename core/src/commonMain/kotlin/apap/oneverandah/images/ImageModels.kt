package apap.oneverandah.images

import kotlinx.serialization.Serializable

/**
 * Shared image-generation domain and API models.
 *
 * These types intentionally live in `:core` `commonMain` so the exact same
 * shapes are used by the Ktor **server** (persistence + API responses) and by
 * the Compose HTML **web** client (decoding the API and rendering covers).
 *
 * They are `@Serializable` so they round-trip cleanly through the JSON API on
 * both the JVM and JS targets without a hand-written mapping layer.
 */

/** Whether an image represents a [Category] cover or a [Service] cover. */
@Serializable
enum class ImageTargetType {
    CATEGORY,
    SERVICE,
}

/** Lifecycle of a single generated image. */
@Serializable
enum class ImageStatus {
    /** Queued for generation, not started yet. */
    PENDING,

    /** Currently being generated / uploaded. */
    GENERATING,

    /** Successfully generated, stored and reachable at [ImageEntity.imageUrl]. */
    COMPLETED,

    /** Generation failed after all retries; eligible for later re-queueing. */
    FAILED,
}

/**
 * A persisted image record.
 *
 * [id] is the storage key (e.g. ArangoDB `_key`). [targetId] links back to a
 * catalog [apap.oneverandah.model.Category] or
 * [apap.oneverandah.model.Service] id, so the frontend can look the cover up by
 * the entity it already knows about.
 */
@Serializable
data class ImageEntity(
    val id: String,
    val targetId: String,
    val targetType: ImageTargetType,
    /** Human readable name of the target, kept for prompt building and logging. */
    val name: String = "",
    /** Public URL of the generated image, or null until generation completes. */
    val imageUrl: String? = null,
    /** The prompt used (or to be used) for generation. */
    val prompt: String = "",
    val status: ImageStatus = ImageStatus.PENDING,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    /** Projects this record onto the trimmed shape returned by the public API. */
    fun toDto(): ImageDto = ImageDto(
        id = id,
        targetId = targetId,
        targetType = targetType,
        imageUrl = imageUrl,
        status = status,
    )
}

/** Trimmed image shape returned by `GET /api/v1/images`. */
@Serializable
data class ImageDto(
    val id: String,
    val targetId: String,
    val targetType: ImageTargetType,
    val imageUrl: String? = null,
    val status: ImageStatus = ImageStatus.PENDING,
)

/** Response body for `GET /api/v1/images`. */
@Serializable
data class ImagesResponse(
    val images: List<ImageDto> = emptyList(),
)

/** Response body for `GET /api/v1/images/status/{targetId}`. */
@Serializable
data class ImageStatusResponse(
    val targetId: String,
    val targetType: ImageTargetType,
    val status: ImageStatus,
    val imageUrl: String? = null,
)

/** Request body for `POST /api/v1/images/generate`. */
@Serializable
data class GenerateRequest(
    val imageId: String,
)

/** Response body for `POST /api/v1/images/generate`. */
@Serializable
data class GenerateResponse(
    val imageId: String,
    val status: ImageStatus,
)

/** Generic error envelope used by the API. */
@Serializable
data class ApiError(
    val error: String,
)
