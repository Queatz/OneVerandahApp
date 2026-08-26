package apap.oneverandah.challenges

import java.util.concurrent.ConcurrentHashMap

interface ChallengeRepository {
    suspend fun findAllChallenges(): List<Challenge>
    suspend fun findChallenge(id: String): Challenge?
    suspend fun saveChallenge(challenge: Challenge)
    suspend fun deleteChallenge(id: String): Boolean

    suspend fun findAllSubmissions(): List<ChallengeSubmission>
    suspend fun findSubmission(id: String): ChallengeSubmission?
    suspend fun findSubmissionsByChallenge(challengeId: String): List<ChallengeSubmission>
    suspend fun saveSubmission(submission: ChallengeSubmission)
    suspend fun deleteSubmission(id: String): Boolean

    suspend fun findVotesByChallenge(challengeId: String): List<ChallengeVote>
    suspend fun findVote(submissionId: String, tokenHash: String): ChallengeVote?
    suspend fun saveVote(vote: ChallengeVote)
    suspend fun deleteVote(id: String)
    suspend fun deleteVotesForSubmission(submissionId: String)
    suspend fun deleteVotesForChallenge(challengeId: String)
}

/** Development / fallback store kept entirely in memory. */
class InMemoryChallengeRepository : ChallengeRepository {
    private val challenges = ConcurrentHashMap<String, Challenge>()
    private val submissions = ConcurrentHashMap<String, ChallengeSubmission>()
    private val votes = ConcurrentHashMap<String, ChallengeVote>()

    override suspend fun findAllChallenges(): List<Challenge> =
        challenges.values.sortedByDescending { it.lastActivityAt }

    override suspend fun findChallenge(id: String): Challenge? = challenges[id]

    override suspend fun saveChallenge(challenge: Challenge) {
        challenges[challenge.id] = challenge
    }

    override suspend fun deleteChallenge(id: String): Boolean {
        val removed = challenges.remove(id) != null
        submissions.values.filter { it.challengeId == id }.forEach { submissions.remove(it.id) }
        votes.values.filter { it.challengeId == id }.forEach { votes.remove(it.id) }
        return removed
    }

    override suspend fun findAllSubmissions(): List<ChallengeSubmission> =
        submissions.values.sortedByDescending { it.createdAt }

    override suspend fun findSubmission(id: String): ChallengeSubmission? = submissions[id]

    override suspend fun findSubmissionsByChallenge(challengeId: String): List<ChallengeSubmission> =
        submissions.values.filter { it.challengeId == challengeId }.sortedByDescending { it.createdAt }

    override suspend fun saveSubmission(submission: ChallengeSubmission) {
        submissions[submission.id] = submission
    }

    override suspend fun deleteSubmission(id: String): Boolean {
        val removed = submissions.remove(id) != null
        votes.values.filter { it.submissionId == id }.forEach { votes.remove(it.id) }
        return removed
    }

    override suspend fun findVotesByChallenge(challengeId: String): List<ChallengeVote> =
        votes.values.filter { it.challengeId == challengeId }

    override suspend fun findVote(submissionId: String, tokenHash: String): ChallengeVote? =
        votes.values.firstOrNull { it.submissionId == submissionId && it.deviceTokenHash == tokenHash }

    override suspend fun saveVote(vote: ChallengeVote) {
        votes[vote.id] = vote
    }

    override suspend fun deleteVote(id: String) {
        votes.remove(id)
    }

    override suspend fun deleteVotesForSubmission(submissionId: String) {
        votes.values.filter { it.submissionId == submissionId }.forEach { votes.remove(it.id) }
    }

    override suspend fun deleteVotesForChallenge(challengeId: String) {
        votes.values.filter { it.challengeId == challengeId }.forEach { votes.remove(it.id) }
    }
}
