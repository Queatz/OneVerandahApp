package apap.oneverandah.stories

import apap.oneverandah.api.StudySessionResponse
import kotlinx.serialization.Serializable

/**
 * A Mini Story book. Drafts stay off the public shelf until [published].
 *
 * [category] is free-text from the author (Mystery, Bedtime, Social, …).
 * [releaseDate] is set the first time the book is published.
 * [openCount] counts how many times anyone opened the book.
 */
@Serializable
data class Story(
    val id: String,
    val name: String,
    val category: String,
    val coverImageUrl: String? = null,
    val releaseDate: Long? = null,
    val published: Boolean = false,
    val publishing: Boolean = false,
    val languageTag: String = "en",
    val openCount: Int = 0,
    val pages: List<StoryPage> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** One illustrated page. [session] holds the shared study skeleton after publish. */
@Serializable
data class StoryPage(
    val id: String,
    val photoUrl: String? = null,
    val text: String = "",
    val session: StudySessionResponse? = null,
)

@Serializable
data class StoriesResponse(
    val stories: List<Story> = emptyList(),
)

@Serializable
data class StoryUpsertRequest(
    val id: String? = null,
    val name: String,
    val category: String,
    val coverImageUrl: String? = null,
    val pages: List<StoryPageUpsert> = emptyList(),
)

@Serializable
data class StoryPageUpsert(
    val id: String? = null,
    val photoUrl: String? = null,
    val text: String = "",
)

@Serializable
data class StoryPublishRequest(
    val languageTag: String,
)

@Serializable
data class StoryPublishResponse(
    val story: Story? = null,
    val jobId: String? = null,
    val status: String = StoryLogic.PUBLISH_JOB_READY,
    val error: String? = null,
)

@Serializable
data class StoryLanguageResponse(
    val languageTag: String,
)

/**
 * Admin request to craft a cover or page photo.
 *
 * [scenePhotoIds] are phrasebook apartment reference photos.
 * [characterIds] are uploaded story characters or harvested study-session portraits.
 */
@Serializable
data class GenerateStoryImageRequest(
    val prompt: String,
    val scenePhotoIds: List<String> = emptyList(),
    val characterIds: List<String> = emptyList(),
    val storyId: String? = null,
    val pageId: String? = null,
    val asCover: Boolean = false,
)

@Serializable
data class GenerateStoryImageResponse(
    val imageUrl: String = "",
    val story: Story? = null,
    val jobId: String? = null,
    val status: String = StoryLogic.IMAGE_JOB_READY,
    val error: String? = null,
)

/** Named character portrait reused as an image-generation reference. */
@Serializable
data class StoryCharacter(
    val id: String,
    val name: String,
    val imageUrl: String,
    /** `"upload"` for admin uploads, `"study"` for harvested study-session portraits. */
    val source: String = SOURCE_UPLOAD,
    val createdAt: Long = 0,
) {
    companion object {
        const val SOURCE_UPLOAD = "upload"
        const val SOURCE_STUDY = "study"
    }
}

@Serializable
data class StoryCharactersResponse(
    val characters: List<StoryCharacter> = emptyList(),
)

@Serializable
data class StoryCharacterRenameRequest(
    val name: String,
)

@Serializable
data class StoryImageUploadResponse(
    val imageUrl: String,
)

@Serializable
data class StoryApiError(
    val error: String,
)
