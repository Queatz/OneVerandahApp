package apap.oneverandah.challenges

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChallengeLogicTest {
    @Test
    fun publicCardsHideUnpublishedAndSortByRecentSubmission() {
        val swimming = Challenge(id = "swim", name = "Pool laps", lastActivityAt = 1)
        val writing = Challenge(id = "write", name = "Story hour", lastActivityAt = 2)
        val empty = Challenge(id = "empty", name = "Silent", lastActivityAt = 99)
        val submissions = listOf(
            submission("old-pub", challengeId = "swim", status = ChallengeLogic.STATUS_PUBLISHED, publishedAt = 10, voteCount = 3, thumbnailUrl = "old.jpg"),
            submission("new-pub", challengeId = "write", status = ChallengeLogic.STATUS_PUBLISHED, publishedAt = 50, voteCount = 1, thumbnailUrl = "new.jpg"),
            submission("review", challengeId = "swim", status = ChallengeLogic.STATUS_IN_REVIEW, createdAt = 80),
            submission("only-review", challengeId = "empty", status = ChallengeLogic.STATUS_IN_REVIEW),
        )

        val cards = ChallengeLogic.publicCards(listOf(swimming, writing, empty), submissions)

        assertEquals(listOf("write", "swim"), cards.map { it.id })
        assertEquals("new.jpg", cards.first().coverImageUrl)
        assertEquals(1, cards.first().submissionCount)
        assertEquals(3, cards.last().topVoteCount)
    }

    @Test
    fun coverUsesFirstFrameOfCurrentLeader() {
        val challenge = Challenge(id = "c", name = "Swim")
        val silver = submission("s", challengeId = "c", voteCount = 2, thumbnailUrl = "silver.jpg", videoUrl = "silver.mp4", createdAt = 1)
        val gold = submission("g", challengeId = "c", voteCount = 5, thumbnailUrl = "gold.jpg", videoUrl = "gold.mp4", createdAt = 2)
        val card = ChallengeLogic.publicCards(listOf(challenge), listOf(silver, gold)).single()
        assertEquals("gold.jpg", card.coverImageUrl)
        assertEquals("gold.mp4", card.coverVideoUrl)
        assertEquals(5, card.topVoteCount)
        assertEquals(2, card.submissionCount)
    }

    @Test
    fun medalsFollowVoteCountThenEarlierEntry() {
        val gold = submission("g", voteCount = 4, createdAt = 30)
        val silver = submission("s", voteCount = 4, createdAt = 10)
        val bronze = submission("b", voteCount = 1, createdAt = 1)
        val leftover = submission("x", voteCount = 0, createdAt = 2)

        val medals = ChallengeLogic.medals(listOf(gold, silver, bronze, leftover))

        assertEquals(ChallengeLogic.MEDAL_GOLD, medals["s"])
        assertEquals(ChallengeLogic.MEDAL_SILVER, medals["g"])
        assertEquals(ChallengeLogic.MEDAL_BRONZE, medals["b"])
        assertNull(medals["x"])
    }

    @Test
    fun playbackOrderPutsLeaderFirstThenNewest() {
        val leader = submission("lead", voteCount = 9, createdAt = 1)
        val older = submission("old", voteCount = 1, createdAt = 2)
        val newer = submission("new", voteCount = 2, createdAt = 8)

        val order = ChallengeLogic.playbackOrder(listOf(older, newer, leader))

        assertEquals(listOf("lead", "new", "old"), order.map { it.id })
    }

    @Test
    fun playbackUsesWidelySupported720pH264() {
        assertEquals(720, ChallengeLogic.TARGET_HEIGHT)
        assertEquals("libx264", ChallengeLogic.VIDEO_CODEC)
        assertEquals("main", ChallengeLogic.VIDEO_PROFILE)
        assertEquals("yuv420p", ChallengeLogic.VIDEO_PIXEL_FORMAT)
        assertEquals("avc1", ChallengeLogic.VIDEO_TAG)
    }

    @Test
    fun captionWordsFallBackToEvenTranscriptTimings() {
        val existing = listOf(ChallengeWord("kept", 0, 400))
        assertEquals(existing, ChallengeLogic.captionWords(existing, "ignored text", 1_000))
        val generated = ChallengeLogic.captionWords(emptyList(), "pool day fun", 900)
        assertEquals(listOf("pool", "day", "fun"), generated.map { it.text })
        assertEquals(0, generated.first().startMs)
        assertEquals(900, generated.last().endMs)
        assertTrue(ChallengeLogic.timedWordsFromText("   ", 1_000).isEmpty())
    }

    @Test
    fun swipeUpGoesToNextClipLikeTikTok() {
        assertEquals(true, ChallengeLogic.swipeGoesNext(-80.0))
        assertEquals(false, ChallengeLogic.swipeGoesNext(80.0))
        assertNull(ChallengeLogic.swipeGoesNext(-20.0))
        assertNull(ChallengeLogic.swipeGoesNext(20.0))
    }

    @Test
    fun captionFollowsWordTimingsAndHoldsBriefly() {
        val words = listOf(
            ChallengeWord("Hello", 0, 400),
            ChallengeWord("neighbors", 400, 900),
        )
        assertEquals("", ChallengeLogic.captionAt(words, -10))
        assertEquals("Hello", ChallengeLogic.captionAt(words, 120))
        assertEquals("neighbors", ChallengeLogic.captionAt(words, 400))
        assertEquals("neighbors", ChallengeLogic.captionAt(words, 1_200))
        assertEquals("", ChallengeLogic.captionAt(words, 2_000))
        assertEquals("", ChallengeLogic.captionAt(emptyList(), 10))
    }

    @Test
    fun sanitizeChallengeNameKeepsTwoToFourWords() {
        assertEquals("", ChallengeLogic.sanitizeChallengeName("   "))
        assertEquals("Pool laps", ChallengeLogic.sanitizeChallengeName("Pool laps!!!"))
        assertEquals("Night swim race fun", ChallengeLogic.sanitizeChallengeName("Night swim race fun extra words"))
        assertEquals("Swimming", ChallengeLogic.sanitizeChallengeName("Swimming"))
    }

    @Test
    fun ownerInReviewListsOnlyThatDeviceHiddenEntries() {
        val mine = listOf(
            submission("proc", status = ChallengeLogic.STATUS_PROCESSING, deviceTokenHash = "me", createdAt = 2),
            submission("rev", status = ChallengeLogic.STATUS_IN_REVIEW, deviceTokenHash = "me", createdAt = 5),
            submission("fail", status = ChallengeLogic.STATUS_FAILED, deviceTokenHash = "me", createdAt = 1),
            submission("live", status = ChallengeLogic.STATUS_PUBLISHED, deviceTokenHash = "me", createdAt = 9),
            submission("other", status = ChallengeLogic.STATUS_IN_REVIEW, deviceTokenHash = "them", createdAt = 8),
        )
        assertEquals(listOf("rev", "proc", "fail"), ChallengeLogic.ownerInReview(mine, "me").map { it.id })
        assertTrue(ChallengeLogic.ownerInReview(mine, null).isEmpty())
    }

    @Test
    fun applyVoteCountsMovesASingleVote() {
        val a = submission("a", voteCount = 3)
        val b = submission("b", voteCount = 1)
        val previous = ChallengeVote(id = "v", challengeId = "c", submissionId = "a", deviceTokenHash = "me")

        val moved = ChallengeLogic.applyVoteCounts(listOf(a, b), previous, "b")
        assertEquals(2, moved.first { it.id == "a" }.voteCount)
        assertEquals(2, moved.first { it.id == "b" }.voteCount)

        val same = ChallengeLogic.applyVoteCounts(listOf(a, b), previous, "a")
        assertEquals(listOf(3, 1), same.map { it.voteCount })
    }

    @Test
    fun nextIndexReturnsNullAfterLastSubmission() {
        assertEquals(1, ChallengeLogic.nextIndexOrDone(0, 2))
        assertNull(ChallengeLogic.nextIndexOrDone(1, 2))
        assertNull(ChallengeLogic.nextIndexOrDone(0, 0))
        assertTrue(ChallengeLogic.shouldExitAfterLast(1, 2))
        assertTrue(ChallengeLogic.shouldExitAfterLast(0, 1))
        assertTrue(!ChallengeLogic.shouldExitAfterLast(0, 2))
        assertTrue(!ChallengeLogic.shouldExitAfterLast(0, 0))
    }

    @Test
    fun previousIndexStaysOnFirstSubmission() {
        assertNull(ChallengeLogic.previousIndex(0))
        assertEquals(0, ChallengeLogic.previousIndex(1))
        assertEquals(2, ChallengeLogic.previousIndex(3))
        assertNull(ChallengeLogic.previousIndex(-1))
    }

    @Test
    fun indexFollowsSubmissionWhenLeaderboardReshuffles() {
        val afterVote = listOf("new", "lead", "old")
        assertEquals(0, ChallengeLogic.indexOfSubmission(afterVote, "new", fallback = 1))
        assertEquals(1, ChallengeLogic.indexOfSubmission(afterVote, "lead", fallback = 0))
        assertEquals(0, ChallengeLogic.indexOfSubmission(afterVote, "missing", fallback = 0))
        assertEquals(0, ChallengeLogic.indexOfSubmission(emptyList(), "new", fallback = 3))
    }

    @Test
    fun naturalLoopSeekAllowsEndToStartButBlocksScrubs() {
        assertTrue(ChallengeLogic.isNaturalLoopSeek(0.0, 29.8))
        assertTrue(ChallengeLogic.isNaturalLoopSeek(0.12, 4.9))
        assertTrue(!ChallengeLogic.isNaturalLoopSeek(12.04, 11.96))
        assertTrue(!ChallengeLogic.isNaturalLoopSeek(15.0, 10.0))
        assertTrue(!ChallengeLogic.isNaturalLoopSeek(10.0, 15.0))
    }

    @Test
    fun canAdvanceIgnoresRapidRepeatsFromOneScroll() {
        assertTrue(ChallengeLogic.canAdvance(nowMs = 1_000, lastAdvanceMs = 200))
        assertTrue(!ChallengeLogic.canAdvance(nowMs = 1_000, lastAdvanceMs = 800))
        assertTrue(ChallengeLogic.canAdvance(nowMs = 1_000, lastAdvanceMs = 1_000 - ChallengeLogic.ADVANCE_COOLDOWN_MS))
    }

    @Test
    fun statusLabelIsHumanReadable() {
        assertEquals("In Review", ChallengeLogic.statusLabel(ChallengeLogic.STATUS_IN_REVIEW))
        assertEquals("Processing", ChallengeLogic.statusLabel(ChallengeLogic.STATUS_PROCESSING))
        assertEquals("Published", ChallengeLogic.statusLabel(ChallengeLogic.STATUS_PUBLISHED))
        assertEquals("Failed", ChallengeLogic.statusLabel(ChallengeLogic.STATUS_FAILED))
    }

    @Test
    fun toViewHidesTokenAndMarksMineAndVote() {
        val submission = submission("s", deviceTokenHash = "secret", voteCount = 4)
        val vote = ChallengeVote(
            id = "v",
            challengeId = "c",
            submissionId = "s",
            deviceTokenHash = "secret",
            emoji = ChallengeLogic.EMOJI_WOW,
        )
        val view = ChallengeLogic.toView(
            submission = submission,
            challengeName = "Pool laps",
            medal = ChallengeLogic.MEDAL_GOLD,
            myVote = vote,
            votes = listOf(vote),
            viewerHash = "secret",
        )
        assertTrue(view.mine)
        assertTrue(view.votedByMe)
        assertEquals(ChallengeLogic.EMOJI_WOW, view.myEmoji)
        assertEquals(ChallengeLogic.MEDAL_GOLD, view.medal)
        assertEquals("Pool laps", view.challengeName)
        assertEquals(1, view.emojis.single().count)
    }

    @Test
    fun normalizeEmojiFallsBackToHeart() {
        assertEquals(ChallengeLogic.EMOJI_STRONG, ChallengeLogic.normalizeEmoji("Strong"))
        assertEquals(ChallengeLogic.EMOJI_HEART, ChallengeLogic.normalizeEmoji("???") )
        assertEquals("💪", ChallengeLogic.emojiGlyph("strong"))
    }

    private fun submission(
        id: String,
        challengeId: String = "c",
        status: String = ChallengeLogic.STATUS_PUBLISHED,
        voteCount: Int = 0,
        createdAt: Long = 0,
        publishedAt: Long? = if (status == ChallengeLogic.STATUS_PUBLISHED) createdAt else null,
        thumbnailUrl: String? = null,
        videoUrl: String = "",
        deviceTokenHash: String = "dev",
    ) = ChallengeSubmission(
        id = id,
        challengeId = challengeId,
        challengerName = "Lan",
        deviceTokenHash = deviceTokenHash,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        status = status,
        voteCount = voteCount,
        createdAt = createdAt,
        publishedAt = publishedAt,
    )
}
