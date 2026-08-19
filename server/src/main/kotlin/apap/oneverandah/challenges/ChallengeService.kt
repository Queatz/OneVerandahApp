package apap.oneverandah.challenges

import java.util.UUID

/**
 * Challenge persistence, voting, and review rules.
 *
 * Video processing lives on [ChallengeSystem] so this class stays easy to test
 * with an in-memory store.
 */
class ChallengeService(
    private val repository: ChallengeRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun feed(deviceTokenHash: String?): ChallengesFeedResponse {
        val challenges = repository.findAllChallenges()
        val submissions = repository.findAllSubmissions()
        val cards = ChallengeLogic.publicCards(challenges, submissions)
        val inReview = ChallengeLogic.ownerInReview(submissions, deviceTokenHash).map { submission ->
            val name = challenges.firstOrNull { it.id == submission.challengeId }?.name.orEmpty()
            ChallengeLogic.toView(submission, challengeName = name, viewerHash = deviceTokenHash)
        }
        return ChallengesFeedResponse(inReview = inReview, challenges = cards)
    }

    suspend fun getPublic(id: String, deviceTokenHash: String?): ChallengeDetailResponse? {
        val challenge = repository.findChallenge(id) ?: return null
        val published = ChallengeLogic.publishedOf(repository.findSubmissionsByChallenge(id))
        if (published.isEmpty()) return null
        val votes = repository.findVotesByChallenge(id)
        val myVote = deviceTokenHash?.let { repository.findVote(id, it) }
        val medals = ChallengeLogic.medals(published)
        val ordered = ChallengeLogic.playbackOrder(published)
        return ChallengeDetailResponse(
            challenge = ChallengeLogic.toCard(challenge, published),
            submissions = ordered.map { submission ->
                ChallengeLogic.toView(
                    submission = submission,
                    challengeName = challenge.name,
                    medal = medals[submission.id],
                    myVote = myVote,
                    votes = votes,
                    viewerHash = deviceTokenHash,
                )
            },
        )
    }

    suspend fun beginUpload(
        challengeId: String?,
        challengerName: String,
        deviceTokenHash: String,
    ): Pair<Challenge, ChallengeSubmission> {
        val name = challengerName.trim()
        require(name.isNotEmpty()) { "A challenger name is required" }
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val now = clock()
        val existingId = challengeId?.trim()?.takeIf { it.isNotEmpty() }
        val challenge = if (existingId == null) {
            Challenge(
                id = "chal-${idGenerator()}",
                name = "",
                createdAt = now,
                updatedAt = now,
                lastActivityAt = now,
            ).also { repository.saveChallenge(it) }
        } else {
            repository.findChallenge(existingId) ?: error("Challenge '$existingId' not found")
        }
        val submission = ChallengeSubmission(
            id = "sub-${idGenerator()}",
            challengeId = challenge.id,
            challengerName = name,
            deviceTokenHash = deviceTokenHash,
            status = ChallengeLogic.STATUS_PROCESSING,
            createdAt = now,
            updatedAt = now,
        )
        repository.saveSubmission(submission)
        touchChallenge(challenge.id, now)
        return challenge to submission
    }

    suspend fun attachMedia(
        submissionId: String,
        videoUrl: String,
        thumbnailUrl: String?,
        durationMs: Long,
    ): ChallengeSubmission {
        val existing = repository.findSubmission(submissionId) ?: error("Submission '$submissionId' not found")
        val updated = existing.copy(
            videoUrl = videoUrl,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationMs.coerceAtLeast(0),
            updatedAt = clock(),
        )
        repository.saveSubmission(updated)
        return updated
    }

    suspend fun completeProcessing(
        submissionId: String,
        transcript: ChallengeTranscript,
        generatedName: String?,
    ): ChallengeSubmission {
        val existing = repository.findSubmission(submissionId) ?: error("Submission '$submissionId' not found")
        val now = clock()
        val updated = existing.copy(
            transcript = transcript.text,
            words = transcript.words,
            status = ChallengeLogic.STATUS_IN_REVIEW,
            error = null,
            updatedAt = now,
        )
        repository.saveSubmission(updated)
        val challenge = repository.findChallenge(existing.challengeId)
        val spoken = transcript.text.isNotBlank() || transcript.words.isNotEmpty()
        if (challenge != null && challenge.name.isBlank() && spoken) {
            val named = ChallengeLogic.sanitizeChallengeName(generatedName)
            if (named.isNotBlank()) {
                repository.saveChallenge(challenge.copy(name = named, updatedAt = now, lastActivityAt = now))
            } else {
                touchChallenge(challenge.id, now)
            }
        } else {
            touchChallenge(existing.challengeId, now)
        }
        return updated
    }

    suspend fun failProcessing(submissionId: String, error: String): ChallengeSubmission {
        val existing = repository.findSubmission(submissionId) ?: error("Submission '$submissionId' not found")
        val updated = existing.copy(
            status = ChallengeLogic.STATUS_FAILED,
            error = error.trim().ifBlank { "Processing failed" },
            updatedAt = clock(),
        )
        repository.saveSubmission(updated)
        return updated
    }

    suspend fun vote(
        challengeId: String,
        submissionId: String,
        deviceTokenHash: String,
        emoji: String?,
    ): ChallengeDetailResponse {
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val submission = repository.findSubmission(submissionId)
            ?: error("Submission '$submissionId' not found")
        require(submission.challengeId == challengeId) { "Submission does not belong to this challenge" }
        require(ChallengeLogic.isPublished(submission)) { "Only published submissions can be voted on" }
        require(submission.deviceTokenHash != deviceTokenHash) { "You cannot vote for your own submission" }

        val previous = repository.findVote(challengeId, deviceTokenHash)
        val now = clock()
        val normalized = ChallengeLogic.normalizeEmoji(emoji)
        if (previous?.submissionId == submissionId) {
            if (previous.emoji != normalized) {
                repository.saveVote(previous.copy(emoji = normalized, createdAt = now))
            }
        } else {
            val siblings = repository.findSubmissionsByChallenge(challengeId)
            ChallengeLogic.applyVoteCounts(siblings, previous, submissionId).forEach { repository.saveSubmission(it) }
            val vote = (previous ?: ChallengeVote(
                id = "vote-${idGenerator()}",
                challengeId = challengeId,
                submissionId = submissionId,
                deviceTokenHash = deviceTokenHash,
                emoji = normalized,
                createdAt = now,
            )).copy(submissionId = submissionId, emoji = normalized, createdAt = now)
            repository.saveVote(vote)
        }
        touchChallenge(challengeId, now)
        return getPublic(challengeId, deviceTokenHash)
            ?: error("Challenge '$challengeId' not found")
    }

    suspend fun listAdmin(): AdminChallengesResponse {
        val challenges = repository.findAllChallenges()
            .sortedWith(compareByDescending<Challenge> { it.updatedAt }.thenBy { it.id })
        val submissions = repository.findAllSubmissions()
        val pending = submissions
            .filter { it.status == ChallengeLogic.STATUS_IN_REVIEW }
            .sortedByDescending { it.createdAt }
            .map { submission ->
                val name = challenges.firstOrNull { it.id == submission.challengeId }?.name.orEmpty()
                ChallengeLogic.toView(submission, challengeName = name)
            }
        val views = challenges.map { challenge ->
            val entries = submissions.filter { it.challengeId == challenge.id }
                .sortedWith(compareByDescending<ChallengeSubmission> { it.createdAt }.thenBy { it.id })
            AdminChallengeView(
                id = challenge.id,
                name = challenge.name,
                createdAt = challenge.createdAt,
                updatedAt = challenge.updatedAt,
                lastActivityAt = challenge.lastActivityAt,
                submissions = entries.map { ChallengeLogic.toView(it, challengeName = challenge.name) },
            )
        }
        return AdminChallengesResponse(challenges = views, pending = pending)
    }

    suspend fun publish(submissionId: String): ChallengeSubmission {
        val existing = repository.findSubmission(submissionId) ?: error("Submission '$submissionId' not found")
        require(existing.status == ChallengeLogic.STATUS_IN_REVIEW || existing.status == ChallengeLogic.STATUS_PUBLISHED) {
            "Only reviewed submissions can be published"
        }
        val now = clock()
        val updated = existing.copy(
            status = ChallengeLogic.STATUS_PUBLISHED,
            publishedAt = existing.publishedAt ?: now,
            error = null,
            updatedAt = now,
        )
        repository.saveSubmission(updated)
        touchChallenge(existing.challengeId, now)
        return updated
    }

    suspend fun rename(challengeId: String, name: String): Challenge {
        val existing = repository.findChallenge(challengeId) ?: error("Challenge '$challengeId' not found")
        val trimmed = ChallengeLogic.sanitizeChallengeName(name)
        require(trimmed.isNotEmpty()) { "A challenge name is required" }
        val updated = existing.copy(name = trimmed, updatedAt = clock())
        repository.saveChallenge(updated)
        return updated
    }

    suspend fun deleteChallenge(id: String): Boolean = repository.deleteChallenge(id)

    suspend fun deleteSubmission(id: String): Boolean {
        val existing = repository.findSubmission(id) ?: return false
        repository.deleteVotesForSubmission(id)
        val deleted = repository.deleteSubmission(id)
        val remaining = repository.findSubmissionsByChallenge(existing.challengeId)
        if (remaining.isEmpty()) {
            repository.deleteChallenge(existing.challengeId)
        } else {
            touchChallenge(existing.challengeId, clock())
        }
        return deleted
    }

    suspend fun findChallenge(id: String): Challenge? = repository.findChallenge(id)

    private suspend fun touchChallenge(id: String, now: Long) {
        val existing = repository.findChallenge(id) ?: return
        repository.saveChallenge(existing.copy(updatedAt = now, lastActivityAt = now))
    }
}
