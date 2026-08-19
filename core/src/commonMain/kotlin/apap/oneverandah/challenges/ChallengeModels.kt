package apap.oneverandah.challenges

import kotlinx.serialization.Serializable

/** A neighborhood activity neighbors can enter and vote on. */
@Serializable
data class Challenge(
    val id: String,
    val name: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastActivityAt: Long = 0,
)

/** One timed caption word from long-form transcription. */
@Serializable
data class ChallengeWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/**
 * A video entry in a [Challenge].
 *
 * [deviceTokenHash] is persisted for ownership and voting identity and is
 * stripped from public API views.
 */
@Serializable
data class ChallengeSubmission(
    val id: String,
    val challengeId: String,
    val challengerName: String,
    val deviceTokenHash: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String? = null,
    val transcript: String = "",
    val words: List<ChallengeWord> = emptyList(),
    val status: String = ChallengeLogic.STATUS_PROCESSING,
    val voteCount: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val publishedAt: Long? = null,
)

@Serializable
data class ChallengeVote(
    val id: String,
    val challengeId: String,
    val submissionId: String,
    val deviceTokenHash: String,
    val emoji: String = ChallengeLogic.EMOJI_HEART,
    val createdAt: Long = 0,
)

/** Public grid card. [coverImageUrl] is the first frame of the current leader. */
@Serializable
data class ChallengeCard(
    val id: String,
    val name: String = "",
    val coverImageUrl: String? = null,
    val coverVideoUrl: String? = null,
    val topVoteCount: Int = 0,
    val submissionCount: Int = 0,
    val lastActivityAt: Long = 0,
)

@Serializable
data class ChallengeEmojiBurst(
    val emoji: String,
    val count: Int = 1,
)

/** Public or owner-facing submission. Never includes the device token hash. */
@Serializable
data class ChallengeSubmissionView(
    val id: String,
    val challengeId: String,
    val challengerName: String,
    val videoUrl: String = "",
    val thumbnailUrl: String? = null,
    val transcript: String = "",
    val words: List<ChallengeWord> = emptyList(),
    val voteCount: Int = 0,
    val medal: String? = null,
    val createdAt: Long = 0,
    val votedByMe: Boolean = false,
    val myEmoji: String? = null,
    val emojis: List<ChallengeEmojiBurst> = emptyList(),
    val status: String = ChallengeLogic.STATUS_PUBLISHED,
    val challengeName: String = "",
    val mine: Boolean = false,
    val durationMs: Long = 0,
    val error: String? = null,
)

@Serializable
data class ChallengesFeedResponse(
    val inReview: List<ChallengeSubmissionView> = emptyList(),
    val challenges: List<ChallengeCard> = emptyList(),
)

@Serializable
data class ChallengeDetailResponse(
    val challenge: ChallengeCard,
    val submissions: List<ChallengeSubmissionView> = emptyList(),
)

@Serializable
data class ChallengeVoteRequest(
    val emoji: String = ChallengeLogic.EMOJI_HEART,
)

@Serializable
data class ChallengeUploadResponse(
    val jobId: String,
    val status: String = ChallengeLogic.JOB_PENDING,
    val submission: ChallengeSubmissionView? = null,
    val challenge: ChallengeCard? = null,
    val error: String? = null,
    val progress: Int = 0,
)

@Serializable
data class ChallengeRenameRequest(
    val name: String,
)

@Serializable
data class AdminChallengeView(
    val id: String,
    val name: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastActivityAt: Long = 0,
    val submissions: List<ChallengeSubmissionView> = emptyList(),
)

@Serializable
data class AdminChallengesResponse(
    val challenges: List<AdminChallengeView> = emptyList(),
    val pending: List<ChallengeSubmissionView> = emptyList(),
)

@Serializable
data class ChallengeApiError(
    val error: String,
)

/** Parsed long-form ASR result. */
data class ChallengeTranscript(
    val text: String = "",
    val words: List<ChallengeWord> = emptyList(),
)
