package apap.oneverandah.clubs

import kotlinx.serialization.Serializable

/**
 * A resident club people can discover and ask to join.
 *
 * [hostZalo] is admin-only and stripped from public views. [joinQuestions]
 * are shown in the join dialog (up to three). [content] is the same
 * markdown/photo story shape used by locations.
 */
@Serializable
data class Club(
    val id: String,
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val category: String = "",
    val activities: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val content: List<ClubContentBlock> = emptyList(),
    val joinQuestions: List<String> = emptyList(),
    val hostName: String = "",
    val hostZalo: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Vertical story block on the club page: markdown text or a photo. */
@Serializable
data class ClubContentBlock(
    val type: String = ClubLogic.BLOCK_TEXT,
    val text: String = "",
    val imageUrl: String = "",
)

/** A public request to start a new club. [hostZalo] is admin-only. */
@Serializable
data class ClubCreateRequest(
    val id: String,
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val hostName: String = "",
    val hostZalo: String = "",
    val deviceTokenHash: String = "",
    val createdAt: Long = 0,
)

/** One answer to a club join question. */
@Serializable
data class ClubJoinAnswer(
    val question: String = "",
    val answer: String = "",
)

/** A request to join an existing club. [zaloNumber] is admin-only. */
@Serializable
data class ClubJoin(
    val id: String,
    val clubId: String,
    val name: String = "",
    val zaloNumber: String = "",
    val answers: List<ClubJoinAnswer> = emptyList(),
    val message: String = "",
    val deviceTokenHash: String = "",
    val createdAt: Long = 0,
)

/** Public list/detail card. Never includes a Zalo number. */
@Serializable
data class ClubCard(
    val id: String,
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val category: String = "",
    val activities: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val hostName: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class ClubsFeedResponse(
    val clubs: List<ClubCard> = emptyList(),
    val categories: List<String> = emptyList(),
)

@Serializable
data class ClubDetailResponse(
    val club: ClubCard,
    val content: List<ClubContentBlock> = emptyList(),
    val joinQuestions: List<String> = emptyList(),
)

@Serializable
data class CreateClubRequestPayload(
    val name: String,
    val tagline: String = "",
    val description: String = "",
    val hostName: String = "",
    val hostZalo: String = "",
)

@Serializable
data class JoinClubRequest(
    val name: String,
    val zaloNumber: String,
    val answers: List<String> = emptyList(),
    val message: String = "",
)

@Serializable
data class ClubUpsertRequest(
    val id: String? = null,
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val category: String = "",
    val activities: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val content: List<ClubContentBlock> = emptyList(),
    val joinQuestions: List<String> = emptyList(),
    val hostName: String = "",
    val hostZalo: String = "",
)

@Serializable
data class ClubPhotoUploadResponse(
    val url: String,
)

@Serializable
data class ClubCreateRequestView(
    val id: String,
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val hostName: String = "",
    val hostZalo: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class ClubJoinView(
    val id: String,
    val clubId: String,
    val clubName: String = "",
    val name: String = "",
    val zaloNumber: String = "",
    val hostZalo: String = "",
    val answers: List<ClubJoinAnswer> = emptyList(),
    val message: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class AdminClubView(
    val id: String,
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val category: String = "",
    val activities: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val content: List<ClubContentBlock> = emptyList(),
    val joinQuestions: List<String> = emptyList(),
    val hostName: String = "",
    val hostZalo: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val joins: List<ClubJoinView> = emptyList(),
)

@Serializable
data class AdminClubsResponse(
    val clubs: List<AdminClubView> = emptyList(),
    val createRequests: List<ClubCreateRequestView> = emptyList(),
    val joins: List<ClubJoinView> = emptyList(),
)

@Serializable
data class ClubApiError(
    val error: String,
)

@Serializable
data class ClubActionResponse(
    val ok: Boolean = true,
)
