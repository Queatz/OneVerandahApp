package apap.oneverandah.challenges

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ChallengeServiceTest {
    @Test
    fun newUploadStaysHiddenUntilPublished() = runBlocking {
        val service = service()
        val (_, created) = service.beginUpload(null, "Lan", "hash-lan")
        assertEquals(ChallengeLogic.STATUS_PROCESSING, created.status)
        assertTrue(service.feed(null).challenges.isEmpty())

        service.completeProcessing(
            created.id,
            ChallengeTranscript("I swam twenty laps today", listOf(ChallengeWord("I", 0, 200))),
            "Pool laps",
        )
        val reviewing = service.feed("hash-lan")
        assertTrue(reviewing.challenges.isEmpty())
        assertEquals(1, reviewing.inReview.size)
        assertEquals("Pool laps", reviewing.inReview.single().challengeName)
        assertTrue(reviewing.inReview.single().mine)

        val published = service.publish(created.id)
        assertEquals(ChallengeLogic.STATUS_PUBLISHED, published.status)
        val feed = service.feed("hash-lan")
        assertEquals(1, feed.challenges.size)
        assertTrue(feed.inReview.isEmpty())
        assertEquals("Pool laps", feed.challenges.single().name)
    }

    @Test
    fun blankTranscriptLeavesChallengeNameEmpty() = runBlocking {
        val service = service()
        val (challenge, created) = service.beginUpload(null, "Minh", "hash-minh")
        service.completeProcessing(created.id, ChallengeTranscript(), generatedName = "Should ignore")
        assertEquals("", service.findChallenge(challenge.id)?.name)
        service.publish(created.id)
        assertEquals("", service.feed(null).challenges.single().name)
    }

    @Test
    fun enteringExistingChallengeKeepsTheName() = runBlocking {
        val service = service()
        val (challenge, first) = service.beginUpload(null, "Lan", "hash-lan")
        service.completeProcessing(first.id, ChallengeTranscript("hello there"), "Night swim")
        service.publish(first.id)

        val (_, second) = service.beginUpload(challenge.id, "Minh", "hash-minh")
        service.completeProcessing(second.id, ChallengeTranscript("a different speech"), "Should not rename")
        assertEquals("Night swim", service.findChallenge(challenge.id)?.name)
    }

    @Test
    fun voteIsOnePerDeviceAndCanMove() = runBlocking {
        val service = publishedPair()
        val challengeId = service.feed(null).challenges.single().id
        val detail = service.getPublic(challengeId, "voter")!!
        val first = detail.submissions[0]
        val second = detail.submissions[1]

        service.vote(challengeId, first.id, "voter", ChallengeLogic.EMOJI_LAUGH)
        val afterFirst = service.getPublic(challengeId, "voter")!!
        assertEquals(1, afterFirst.submissions.first { it.id == first.id }.voteCount)
        assertTrue(afterFirst.submissions.first { it.id == first.id }.votedByMe)
        assertEquals(ChallengeLogic.EMOJI_LAUGH, afterFirst.submissions.first { it.id == first.id }.myEmoji)

        service.vote(challengeId, second.id, "voter", ChallengeLogic.EMOJI_WOW)
        val afterMove = service.getPublic(challengeId, "voter")!!
        assertEquals(0, afterMove.submissions.first { it.id == first.id }.voteCount)
        assertEquals(1, afterMove.submissions.first { it.id == second.id }.voteCount)
        assertTrue(afterMove.submissions.first { it.id == second.id }.votedByMe)
        assertFalse(afterMove.submissions.first { it.id == first.id }.votedByMe)
    }

    @Test
    fun cannotVoteForOwnOrUnpublishedSubmission() = runBlocking {
        val service = service()
        val (_, created) = service.beginUpload(null, "Lan", "hash-lan")
        service.completeProcessing(created.id, ChallengeTranscript("hi"), "Hi there")
        assertFailsWith<IllegalArgumentException> {
            service.vote(created.challengeId, created.id, "someone", ChallengeLogic.EMOJI_HEART)
        }
        service.publish(created.id)
        assertFailsWith<IllegalArgumentException> {
            service.vote(created.challengeId, created.id, "hash-lan", ChallengeLogic.EMOJI_HEART)
        }
        Unit
    }

    @Test
    fun playbackPutsLeaderFirstThenNewest() = runBlocking {
        val service = service()
        val (challenge, first) = service.beginUpload(null, "A", "a")
        service.completeProcessing(first.id, ChallengeTranscript("one"), "Race day")
        service.publish(first.id)
        val (_, second) = service.beginUpload(challenge.id, "B", "b")
        service.completeProcessing(second.id, ChallengeTranscript("two"), null)
        service.publish(second.id)
        service.vote(challenge.id, first.id, "voter", ChallengeLogic.EMOJI_HEART)

        val order = service.getPublic(challenge.id, "voter")!!.submissions.map { it.id }
        assertEquals(listOf(first.id, second.id), order)
        assertEquals(ChallengeLogic.MEDAL_GOLD, service.getPublic(challenge.id, null)!!.submissions.first().medal)
    }

    @Test
    fun adminCanRenameAndDelete() = runBlocking {
        val service = publishedPair()
        val challengeId = service.listAdmin().challenges.single().id
        val renamed = service.rename(challengeId, "Garden poetry night extra")
        assertEquals("Garden poetry night extra", renamed.name)

        val submissionId = service.listAdmin().challenges.single().submissions.first().id
        assertTrue(service.deleteSubmission(submissionId))
        assertEquals(1, service.listAdmin().challenges.single().submissions.size)
        assertTrue(service.deleteChallenge(challengeId))
        assertTrue(service.listAdmin().challenges.isEmpty())
    }

    @Test
    fun failedProcessingIsVisibleToOwner() = runBlocking {
        val service = service()
        val (_, created) = service.beginUpload(null, "Lan", "hash-lan")
        service.failProcessing(created.id, "ffmpeg missing")
        val mine = service.feed("hash-lan").inReview.single()
        assertEquals(ChallengeLogic.STATUS_FAILED, mine.status)
        assertEquals("ffmpeg missing", mine.error)
        assertTrue(service.feed(null).challenges.isEmpty())
    }

    @Test
    fun requireNameAndToken() = runBlocking {
        val service = service()
        assertFailsWith<IllegalArgumentException> { service.beginUpload(null, "  ", "hash") }
        assertFailsWith<IllegalArgumentException> { service.beginUpload(null, "Lan", " ") }
        Unit
    }

    @Test
    fun completeProcessingFillsCaptionWordsFromTranscriptText() = runBlocking {
        val service = service()
        val (_, created) = service.beginUpload(null, "Lan", "hash-lan")
        service.attachMedia(created.id, "https://cdn/video.mp4", null, 900)
        val completed = service.completeProcessing(
            created.id,
            ChallengeTranscript("pool day fun"),
            "Pool day",
        )
        assertEquals(listOf("pool", "day", "fun"), completed.words.map { it.text })
        assertEquals(0, completed.words.first().startMs)
        assertEquals(900, completed.words.last().endMs)
    }

    @Test
    fun attachMediaStoresVideoAndThumbnail() = runBlocking {
        val service = service()
        val (_, created) = service.beginUpload(null, "Lan", "hash-lan")
        val attached = service.attachMedia(created.id, "https://cdn/video.mp4", "https://cdn/frame.jpg", 90_000)
        assertEquals("https://cdn/video.mp4", attached.videoUrl)
        assertEquals("https://cdn/frame.jpg", attached.thumbnailUrl)
        assertEquals(90_000, attached.durationMs)
    }

    private fun service(): ChallengeService {
        var next = 0
        return ChallengeService(
            repository = InMemoryChallengeRepository(),
            clock = { 1_700_000_000_000L + next },
            idGenerator = { "id-${next++}" },
        )
    }

    private suspend fun publishedPair(): ChallengeService {
        val service = service()
        val (challenge, first) = service.beginUpload(null, "Lan", "hash-lan")
        service.completeProcessing(first.id, ChallengeTranscript("hello friends"), "Hello friends")
        service.publish(first.id)
        val (_, second) = service.beginUpload(challenge.id, "Minh", "hash-minh")
        service.completeProcessing(second.id, ChallengeTranscript("more words here"), null)
        service.publish(second.id)
        return service
    }
}
