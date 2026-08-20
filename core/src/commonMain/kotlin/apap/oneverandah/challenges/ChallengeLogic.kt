package apap.oneverandah.challenges

/**
 * Pure Challenges rules shared by the server and client.
 *
 * Keep this free of I/O so ranking, captions, and vote transfers stay deterministic.
 */
object ChallengeLogic {
    const val STATUS_PROCESSING = "processing"
    const val STATUS_IN_REVIEW = "in_review"
    const val STATUS_PUBLISHED = "published"
    const val STATUS_FAILED = "failed"

    const val JOB_PENDING = "pending"
    const val JOB_READY = "ready"
    const val JOB_FAILED = "failed"

    const val MEDAL_GOLD = "gold"
    const val MEDAL_SILVER = "silver"
    const val MEDAL_BRONZE = "bronze"

    const val EMOJI_HEART = "heart"
    const val EMOJI_LAUGH = "laugh"
    const val EMOJI_WOW = "wow"
    const val EMOJI_STRONG = "strong"
    const val EMOJI_PRETTY = "pretty"

    val EMOJI_IDS: List<String> = listOf(
        EMOJI_HEART,
        EMOJI_LAUGH,
        EMOJI_WOW,
        EMOJI_STRONG,
        EMOJI_PRETTY,
    )

    const val MAX_DURATION_MS = 5 * 60 * 1000L
    const val TARGET_HEIGHT = 720
    const val VIDEO_CODEC = "libx264"
    const val VIDEO_PROFILE = "main"
    const val VIDEO_PIXEL_FORMAT = "yuv420p"
    const val VIDEO_TAG = "avc1"
    const val MAX_NAME_WORDS = 4
    const val CAPTION_HOLD_MS = 800L
    const val MAX_FLYING_EMOJIS = 24
    const val ADVANCE_COOLDOWN_MS = 700L
    const val SWIPE_THRESHOLD_PX = 72.0

    fun isPublished(submission: ChallengeSubmission): Boolean =
        submission.status == STATUS_PUBLISHED

    fun isOwnerVisible(submission: ChallengeSubmission): Boolean =
        submission.status == STATUS_PROCESSING ||
            submission.status == STATUS_IN_REVIEW ||
            submission.status == STATUS_FAILED

    fun normalizeEmoji(raw: String?): String {
        val key = raw?.trim()?.lowercase().orEmpty()
        return if (key in EMOJI_IDS) key else EMOJI_HEART
    }

    fun emojiGlyph(id: String): String = when (normalizeEmoji(id)) {
        EMOJI_LAUGH -> "😂"
        EMOJI_WOW -> "😮"
        EMOJI_STRONG -> "💪"
        EMOJI_PRETTY -> "✨"
        else -> "❤️"
    }

    /**
     * AI names should be 2–4 words. Extra words are dropped; punctuation is
     * stripped. A single leftover word is kept so a sparse transcript still
     * yields a label. Empty input stays empty.
     */
    fun sanitizeChallengeName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = buildString {
            for (ch in raw.trim()) {
                if (ch.isLetterOrDigit() || ch == '\'' || ch == '-' || ch.isWhitespace()) append(ch)
                else append(' ')
            }
        }
        val words = cleaned.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_NAME_WORDS)
        return words.joinToString(" ")
    }

    fun publishedOf(submissions: List<ChallengeSubmission>): List<ChallengeSubmission> =
        submissions.filter(::isPublished)

    fun topSubmission(published: List<ChallengeSubmission>): ChallengeSubmission? =
        published.sortedWith(bestFirst()).firstOrNull()

    /** Gold / silver / bronze for the current top three published entries. */
    fun medals(published: List<ChallengeSubmission>): Map<String, String> {
        val ranked = published.sortedWith(bestFirst())
        return buildMap {
            ranked.getOrNull(0)?.let { put(it.id, MEDAL_GOLD) }
            ranked.getOrNull(1)?.let { put(it.id, MEDAL_SILVER) }
            ranked.getOrNull(2)?.let { put(it.id, MEDAL_BRONZE) }
        }
    }

    /**
     * Player order: the current leader first, then everyone else newest first.
     */
    fun playbackOrder(published: List<ChallengeSubmission>): List<ChallengeSubmission> {
        val top = topSubmission(published) ?: return emptyList()
        val rest = published
            .filter { it.id != top.id }
            .sortedWith(newestFirst())
        return listOf(top) + rest
    }

    /**
     * Public shelf: challenges that have at least one published submission,
     * newest activity first. Cover is the first frame of the current leader.
     */
    fun publicCards(
        challenges: List<Challenge>,
        submissions: List<ChallengeSubmission>,
    ): List<ChallengeCard> {
        val publishedByChallenge = publishedOf(submissions).groupBy { it.challengeId }
        return challenges.mapNotNull { challenge ->
            val published = publishedByChallenge[challenge.id].orEmpty()
            if (published.isEmpty()) return@mapNotNull null
            val top = topSubmission(published) ?: return@mapNotNull null
            val lastActivity = published.maxOf { it.publishedAt ?: it.updatedAt.takeIf { ts -> ts > 0 } ?: it.createdAt }
            toCard(challenge, published, top).copy(lastActivityAt = lastActivity)
        }.sortedWith(
            compareByDescending<ChallengeCard> { it.lastActivityAt }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id },
        )
    }

    fun toCard(
        challenge: Challenge,
        published: List<ChallengeSubmission>,
        top: ChallengeSubmission? = topSubmission(published),
    ): ChallengeCard = ChallengeCard(
        id = challenge.id,
        name = challenge.name,
        coverImageUrl = top?.thumbnailUrl,
        coverVideoUrl = top?.videoUrl?.takeIf { it.isNotBlank() },
        topVoteCount = top?.voteCount ?: 0,
        submissionCount = published.size,
        lastActivityAt = challenge.lastActivityAt,
    )

    fun ownerInReview(
        submissions: List<ChallengeSubmission>,
        tokenHash: String?,
    ): List<ChallengeSubmission> {
        val hash = tokenHash?.trim().orEmpty()
        if (hash.isEmpty()) return emptyList()
        return submissions
            .filter { it.deviceTokenHash == hash && isOwnerVisible(it) }
            .sortedWith(newestFirst())
    }

    fun captionAt(words: List<ChallengeWord>, timeMs: Long): String {
        if (words.isEmpty()) return ""
        val current = words.filter { timeMs >= it.startMs && timeMs < it.endMs }
        if (current.isNotEmpty()) return current.joinToString(" ") { it.text }
        val upcoming = words.firstOrNull { it.startMs > timeMs }
        if (upcoming != null && upcoming.startMs - timeMs <= CAPTION_HOLD_MS && timeMs < words.first().startMs) {
            return ""
        }
        val last = words.lastOrNull { it.startMs <= timeMs } ?: return ""
        return if (timeMs < last.endMs + CAPTION_HOLD_MS) last.text else ""
    }

    /** Prefer timed ASR words; otherwise spread the transcript across the clip. */
    fun captionWords(
        words: List<ChallengeWord>,
        transcript: String,
        durationMs: Long,
    ): List<ChallengeWord> = if (words.isNotEmpty()) words else timedWordsFromText(transcript, durationMs)

    fun timedWordsFromText(text: String, durationMs: Long): List<ChallengeWord> {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val span = durationMs.coerceAtLeast(tokens.size * 250L)
        val each = span / tokens.size
        return tokens.mapIndexed { index, token ->
            ChallengeWord(text = token, startMs = index * each, endMs = (index + 1) * each)
        }
    }

    fun flyingEmojis(emojis: List<ChallengeEmojiBurst>): List<String> {
        val glyphs = mutableListOf<String>()
        for (burst in emojis) {
            val glyph = emojiGlyph(burst.emoji)
            repeat(burst.count.coerceAtLeast(0)) { glyphs += glyph }
            if (glyphs.size >= MAX_FLYING_EMOJIS) break
        }
        return glyphs.take(MAX_FLYING_EMOJIS)
    }

    fun emojiBursts(votes: List<ChallengeVote>): List<ChallengeEmojiBurst> =
        votes.groupingBy { normalizeEmoji(it.emoji) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { ChallengeEmojiBurst(emoji = it.key, count = it.value) }

    /**
     * One vote per device per challenge. Changing the pick moves the vote.
     */
    fun applyVoteCounts(
        submissions: List<ChallengeSubmission>,
        previous: ChallengeVote?,
        nextSubmissionId: String,
    ): List<ChallengeSubmission> {
        if (previous?.submissionId == nextSubmissionId) return submissions
        return submissions.map { submission ->
            when {
                previous != null && submission.id == previous.submissionId ->
                    submission.copy(voteCount = (submission.voteCount - 1).coerceAtLeast(0))
                submission.id == nextSubmissionId ->
                    submission.copy(voteCount = submission.voteCount + 1)
                else -> submission
            }
        }
    }

    fun toView(
        submission: ChallengeSubmission,
        challengeName: String = "",
        medal: String? = null,
        myVote: ChallengeVote? = null,
        votes: List<ChallengeVote> = emptyList(),
        viewerHash: String? = null,
    ): ChallengeSubmissionView {
        val mine = !viewerHash.isNullOrBlank() && submission.deviceTokenHash == viewerHash
        val votedHere = myVote?.submissionId == submission.id
        return ChallengeSubmissionView(
            id = submission.id,
            challengeId = submission.challengeId,
            challengerName = submission.challengerName,
            videoUrl = submission.videoUrl,
            thumbnailUrl = submission.thumbnailUrl,
            transcript = submission.transcript,
            words = submission.words,
            voteCount = submission.voteCount,
            medal = medal,
            createdAt = submission.createdAt,
            votedByMe = votedHere,
            myEmoji = if (votedHere) myVote.emoji else null,
            emojis = emojiBursts(votes.filter { it.submissionId == submission.id }),
            status = submission.status,
            challengeName = challengeName,
            mine = mine,
            durationMs = submission.durationMs,
            error = submission.error,
        )
    }

    fun nextIndexOrDone(currentIndex: Int, total: Int): Int? {
        if (total <= 0) return null
        val next = currentIndex + 1
        return if (next >= total) null else next
    }

    /** Leave the player only after the last real clip, never while the list is empty. */
    fun shouldExitAfterLast(currentIndex: Int, total: Int): Boolean =
        total > 0 && currentIndex + 1 >= total

    fun previousIndex(currentIndex: Int): Int? =
        if (currentIndex <= 0) null else currentIndex - 1

    /**
     * Keep watching the same clip after a vote reshuffles playback order.
     * Unknown ids stay on [fallback], clamped to the list.
     */
    fun indexOfSubmission(ids: List<String>, submissionId: String, fallback: Int = 0): Int {
        val found = ids.indexOf(submissionId)
        if (found >= 0) return found
        if (ids.isEmpty()) return 0
        return fallback.coerceIn(0, ids.lastIndex)
    }

    /**
     * TikTok-style vertical swipe: finger moving up (negative delta) goes to
     * the next clip; finger moving down goes to the previous one.
     *
     * @return `true` for next, `false` for previous, `null` if the gesture is too small.
     */
    fun swipeGoesNext(deltaY: Double, threshold: Double = SWIPE_THRESHOLD_PX): Boolean? = when {
        deltaY <= -threshold -> true
        deltaY >= threshold -> false
        else -> null
    }

    /**
     * HTML `loop` seeks from near the end back to 0. That jump is not a
     * manual scrub and must be allowed or the last frames will repeat.
     */
    fun isNaturalLoopSeek(currentTimeSec: Double, lastTimeSec: Double): Boolean =
        currentTimeSec < 0.5 && lastTimeSec > currentTimeSec + 0.35

    /** Blocks a second swipe/wheel from skipping past the next clip. */
    fun canAdvance(nowMs: Long, lastAdvanceMs: Long, cooldownMs: Long = ADVANCE_COOLDOWN_MS): Boolean =
        nowMs - lastAdvanceMs >= cooldownMs

    fun statusLabel(status: String): String = when (status) {
        STATUS_PROCESSING -> "Processing"
        STATUS_IN_REVIEW -> "In Review"
        STATUS_PUBLISHED -> "Published"
        STATUS_FAILED -> "Failed"
        else -> status.replace('_', ' ').split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    private fun bestFirst(): Comparator<ChallengeSubmission> =
        compareByDescending<ChallengeSubmission> { it.voteCount }
            .thenBy { it.createdAt }
            .thenBy { it.id }

    private fun newestFirst(): Comparator<ChallengeSubmission> =
        compareByDescending<ChallengeSubmission> { it.createdAt }
            .thenBy { it.id }
}
