package apap.oneverandah.meets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class MeetServiceTest {
    @Test
    fun newMeetStaysHiddenUntilApproved() = runBlocking {
        val service = service()
        val created = service.create("Lan", "Coffee in the lobby", "090111", 3, "lan.jpg", "hash-lan")
        assertEquals(MeetLogic.STATUS_PENDING, created.status)
        assertTrue(service.feed(null).meets.isEmpty())

        val reviewing = service.feed("hash-lan")
        assertTrue(reviewing.meets.isEmpty())
        assertEquals(1, reviewing.inReview.size)
        assertTrue(reviewing.inReview.single().mine)

        val published = service.approve(created.id)
        assertEquals(MeetLogic.STATUS_PUBLISHED, published.status)
        val feed = service.feed("hash-lan")
        assertEquals(1, feed.meets.size)
        assertTrue(feed.inReview.isEmpty())
        assertEquals("Lan", feed.meets.single().name)
        assertEquals(3, service.getPublic(created.id, "visitor")?.meet?.remainingPitches)
        assertTrue(service.getPublic(created.id, "visitor")?.canPitch == true)
        assertFalse(service.getPublic(created.id, "hash-lan")?.canPitch == true)
    }

    @Test
    fun publicDetailHidesPendingFromOthers() = runBlocking {
        val service = service()
        val created = service.create("Lan", "Let's walk", "090111", 2, "lan.jpg", "hash-lan")
        assertNull(service.getPublic(created.id, "visitor"))
        assertEquals(MeetLogic.STATUS_PENDING, service.getPublic(created.id, "hash-lan")?.meet?.status)
    }

    @Test
    fun pitchRequiresPublishedOpenMeet() = runBlocking {
        val service = service()
        val created = service.create("Lan", "Coffee", "090111", 2, "lan.jpg", "hash-lan")
        assertFailsWith<IllegalArgumentException> {
            service.sendPitch(created.id, "Hi there", "090222", "visitor")
        }
        service.approve(created.id)
        val pitch = service.sendPitch(created.id, "Hi there", "090222", "visitor")
        assertEquals(MeetLogic.PITCH_PENDING, pitch.status)
        assertEquals("Hi there", pitch.message)
        assertFalse(service.getPublic(created.id, "visitor")?.canPitch == true)
    }

    @Test
    fun cannotPitchOwnMeetOrDuplicatePending() = runBlocking {
        val service = service()
        val created = published(service)
        assertFailsWith<IllegalArgumentException> {
            service.sendPitch(created.id, "Hello me", "090111", "hash-lan")
        }
        service.sendPitch(created.id, "Let's meet", "090222", "visitor")
        assertFailsWith<IllegalArgumentException> {
            service.sendPitch(created.id, "Again", "090222", "visitor")
        }
        Unit
    }

    @Test
    fun completingPitchDecrementsRemaining() = runBlocking {
        val service = service()
        val created = published(service, limit = 2)
        val pitch = service.sendPitch(created.id, "Hello", "090222", "visitor")
        assertEquals(2, service.getPublic(created.id, "other")?.meet?.remainingPitches)
        service.completePitch(pitch.id)
        assertEquals(1, service.getPublic(created.id, "other")?.meet?.remainingPitches)
        service.completePitch(pitch.id)
        assertEquals(1, service.getPublic(created.id, "other")?.meet?.remainingPitches)
    }

    @Test
    fun completingLastPitchClosesMeet() = runBlocking {
        val service = service()
        val created = published(service, limit = 1)
        val pitch = service.sendPitch(created.id, "Hello", "090222", "visitor")
        service.completePitch(pitch.id)
        assertEquals(0, service.getPublic(created.id, "other")?.meet?.remainingPitches)
        assertFailsWith<IllegalArgumentException> {
            service.sendPitch(created.id, "Too late", "090333", "other")
        }
        Unit
    }

    @Test
    fun resetRestoresOriginalLimit() = runBlocking {
        val service = service()
        val created = published(service, limit = 4)
        val pitch = service.sendPitch(created.id, "Hello", "090222", "visitor")
        service.completePitch(pitch.id)
        val reset = service.resetRemaining(created.id)
        assertEquals(4, reset.remainingPitches)
        assertEquals(4, reset.maxPitches)
    }

    @Test
    fun adminListShowsZaloAndPendingPitches() = runBlocking {
        val service = service()
        val created = published(service)
        service.sendPitch(created.id, "River walk", "090222", "visitor")
        val admin = service.listAdmin()
        assertEquals("090111", admin.meets.single().zaloNumber)
        assertEquals(1, admin.pendingPitches.size)
        assertEquals("090222", admin.pendingPitches.single().zaloNumber)
        assertEquals("River walk", admin.pendingPitches.single().message)
        assertTrue(admin.pendingMeets.isEmpty())
    }

    @Test
    fun deleteMeetRemovesPitches() = runBlocking {
        val service = service()
        val created = published(service)
        service.sendPitch(created.id, "Hello", "090222", "visitor")
        assertTrue(service.deleteMeet(created.id))
        assertNull(service.getPublic(created.id, "visitor"))
        assertTrue(service.listAdmin().pendingPitches.isEmpty())
    }

    @Test
    fun requireNamePitchZaloPhotoAndToken() = runBlocking {
        val service = service()
        assertFailsWith<IllegalArgumentException> { service.create("  ", "pitch", "090", 3, "p.jpg", "hash") }
        assertFailsWith<IllegalArgumentException> { service.create("Lan", "  ", "090", 3, "p.jpg", "hash") }
        assertFailsWith<IllegalArgumentException> { service.create("Lan", "pitch", "abc", 3, "p.jpg", "hash") }
        assertFailsWith<IllegalArgumentException> { service.create("Lan", "pitch", "090", 3, " ", "hash") }
        assertFailsWith<IllegalArgumentException> { service.create("Lan", "pitch", "090", 3, "p.jpg", " ") }
        Unit
    }

    private fun service(): MeetService {
        var next = 0
        return MeetService(
            repository = InMemoryMeetRepository(),
            clock = { 1_700_000_000_000L + next },
            idGenerator = { "id-${next++}" },
        )
    }

    private suspend fun published(service: MeetService, limit: Int = 3): Meet {
        val created = service.create("Lan", "Coffee in the lobby", "090111", limit, "lan.jpg", "hash-lan")
        return service.approve(created.id)
    }
}
