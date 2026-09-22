package apap.oneverandah.meets

import kotlinx.serialization.Serializable

/**
 * A neighbor's open invitation to meet — casual hangout mixed with
 * professional networking.
 *
 * [zaloNumber] is shown on the public meet so neighbors can contact the host
 * directly while the meet is active. [remainingPitches] counts how many
 * completed meet-ups are still available; [maxPitches] is the original cap
 * so admin can reset it.
 */
@Serializable
data class Meet(
    val id: String,
    val name: String = "",
    val photoUrl: String = "",
    val pitch: String = "",
    val zaloNumber: String = "",
    val remainingPitches: Int = 0,
    val maxPitches: Int = 0,
    val status: String = MeetLogic.STATUS_PENDING,
    val deviceTokenHash: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Someone reaching out to a published [Meet]. [zaloNumber] is admin-only. */
@Serializable
data class MeetPitch(
    val id: String,
    val meetId: String,
    val message: String = "",
    val zaloNumber: String = "",
    val deviceTokenHash: String = "",
    val status: String = MeetLogic.PITCH_PENDING,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Public grid / detail card. Includes the host Zalo so people can reach out. */
@Serializable
data class MeetCard(
    val id: String,
    val name: String = "",
    val photoUrl: String = "",
    val pitch: String = "",
    val zaloNumber: String = "",
    val remainingPitches: Int = 0,
    val status: String = MeetLogic.STATUS_PUBLISHED,
    val mine: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
data class MeetsFeedResponse(
    val inReview: List<MeetCard> = emptyList(),
    val meets: List<MeetCard> = emptyList(),
)

@Serializable
data class MeetDetailResponse(
    val meet: MeetCard,
    val canPitch: Boolean = false,
)

@Serializable
data class SendMeetPitchRequest(
    val message: String,
    val zaloNumber: String,
)

@Serializable
data class MeetPitchView(
    val id: String,
    val meetId: String,
    val meetName: String = "",
    val message: String = "",
    val zaloNumber: String = "",
    val meetZaloNumber: String = "",
    val status: String = MeetLogic.PITCH_PENDING,
    val createdAt: Long = 0,
)

@Serializable
data class ResetMeetRemainingRequest(
    val remainingPitches: Int? = null,
)

@Serializable
data class AdminMeetView(
    val id: String,
    val name: String = "",
    val photoUrl: String = "",
    val pitch: String = "",
    val zaloNumber: String = "",
    val remainingPitches: Int = 0,
    val maxPitches: Int = 0,
    val status: String = MeetLogic.STATUS_PENDING,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val pitches: List<MeetPitchView> = emptyList(),
)

@Serializable
data class AdminMeetsResponse(
    val pendingMeets: List<AdminMeetView> = emptyList(),
    val pendingPitches: List<MeetPitchView> = emptyList(),
    val meets: List<AdminMeetView> = emptyList(),
)

@Serializable
data class MeetApiError(
    val error: String,
)
