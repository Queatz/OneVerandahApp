package apap.oneverandah.meets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeetLogicTest {
    @Test
    fun publicCardsHidePendingAndSortNewestFirst() {
        val pending = meet("p", status = MeetLogic.STATUS_PENDING, createdAt = 90)
        val older = meet("old", createdAt = 10, name = "Minh")
        val newer = meet("new", createdAt = 50, name = "Lan")

        val cards = MeetLogic.publicCards(listOf(pending, older, newer))

        assertEquals(listOf("new", "old"), cards.map { it.id })
        assertTrue(cards.none { it.id == "p" })
        assertEquals("Coffee in the lobby", cards.first().pitch)
    }

    @Test
    fun ownerPendingShowsOnlyThisDevice() {
        val mine = meet("mine", status = MeetLogic.STATUS_PENDING, deviceTokenHash = "hash-lan")
        val other = meet("other", status = MeetLogic.STATUS_PENDING, deviceTokenHash = "hash-minh")
        val published = meet("pub", deviceTokenHash = "hash-lan")

        val pending = MeetLogic.ownerPending(listOf(mine, other, published), "hash-lan")

        assertEquals(listOf("mine"), pending.map { it.id })
        assertTrue(pending.single().mine)
    }

    @Test
    fun publicCardNeverIncludesZaloAndMarksMine() {
        val card = MeetLogic.toPublicCard(
            meet("m", zaloNumber = "0901234567", deviceTokenHash = "hash-lan"),
            viewerHash = "hash-lan",
        )
        assertTrue(card.mine)
        assertEquals("Coffee in the lobby", card.pitch)
        assertEquals("Lan", card.name)
    }

    @Test
    fun ellipsizeShortTextIsUnchanged() {
        assertEquals("Hello", MeetLogic.ellipsize("Hello", 80))
        assertEquals("Hello world…", MeetLogic.ellipsize("Hello world neighbors", 12))
    }

    @Test
    fun canPitchRequiresPublishedOpenMeetAndOtherDevice() {
        val published = meet("m", remainingPitches = 3, deviceTokenHash = "owner")
        assertTrue(MeetLogic.canPitch(published, "visitor"))
        assertFalse(MeetLogic.canPitch(published, "owner"))
        assertFalse(MeetLogic.canPitch(published, "visitor", hasPendingPitch = true))
        assertFalse(MeetLogic.canPitch(published.copy(remainingPitches = 0), "visitor"))
        assertFalse(MeetLogic.canPitch(published.copy(status = MeetLogic.STATUS_PENDING), "visitor"))
        assertFalse(MeetLogic.canPitch(published, "  "))
    }

    @Test
    fun decrementAndResetRemaining() {
        val meet = meet("m", remainingPitches = 3, maxPitches = 5)
        val afterOne = MeetLogic.decrementRemaining(meet)
        assertEquals(2, afterOne.remainingPitches)
        assertEquals(5, MeetLogic.resetRemaining(afterOne).remainingPitches)
        assertEquals(0, MeetLogic.decrementRemaining(meet.copy(remainingPitches = 0)).remainingPitches)
    }

    @Test
    fun sanitizeAndClampInputs() {
        assertEquals("Lan Anh", MeetLogic.sanitizeName("  Lan   Anh  "))
        assertEquals("hi", MeetLogic.sanitizePitch("  hi  "))
        assertEquals("0901 234 567", MeetLogic.sanitizeZalo("0901 234 567abc"))
        assertEquals(5, MeetLogic.parsePitchLimit(null))
        assertEquals(1, MeetLogic.parsePitchLimit("0"))
        assertEquals(50, MeetLogic.parsePitchLimit("999"))
        assertEquals(8, MeetLogic.parsePitchLimit("8"))
    }

    @Test
    fun adminViewKeepsZaloAndPendingPitchFromDevice() {
        val meet = meet("m", zaloNumber = "090111")
        val pitch = MeetPitch(
            id = "pitch-1",
            meetId = "m",
            message = "Let's walk the river",
            zaloNumber = "090222",
            deviceTokenHash = "visitor",
            status = MeetLogic.PITCH_PENDING,
            createdAt = 1,
        )
        val admin = MeetLogic.toAdminView(meet, listOf(pitch))
        assertEquals("090111", admin.zaloNumber)
        assertEquals("090222", admin.pitches.single().zaloNumber)
        assertTrue(MeetLogic.hasPendingPitchFrom(listOf(pitch), "m", "visitor"))
        assertFalse(MeetLogic.hasPendingPitchFrom(listOf(pitch), "m", "other"))
        assertEquals("", MeetLogic.toPitchView(pitch, includeZalo = false).zaloNumber)
    }

    private fun meet(
        id: String,
        name: String = "Lan",
        photoUrl: String = "lan.jpg",
        pitch: String = "Coffee in the lobby",
        zaloNumber: String = "090000",
        remainingPitches: Int = 3,
        maxPitches: Int = 5,
        status: String = MeetLogic.STATUS_PUBLISHED,
        deviceTokenHash: String = "dev",
        createdAt: Long = 0,
    ) = Meet(
        id = id,
        name = name,
        photoUrl = photoUrl,
        pitch = pitch,
        zaloNumber = zaloNumber,
        remainingPitches = remainingPitches,
        maxPitches = maxPitches,
        status = status,
        deviceTokenHash = deviceTokenHash,
        createdAt = createdAt,
    )
}
