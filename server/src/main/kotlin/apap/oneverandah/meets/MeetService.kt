package apap.oneverandah.meets

import java.util.UUID

/**
 * Meet persistence, approval, and pitch-quota rules.
 *
 * Photo upload lives on [MeetSystem] so this class stays easy to test with
 * an in-memory store.
 */
class MeetService(
    private val repository: MeetRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun feed(deviceTokenHash: String?): MeetsFeedResponse {
        val meets = repository.findAllMeets()
        return MeetsFeedResponse(
            inReview = MeetLogic.ownerPending(meets, deviceTokenHash),
            meets = MeetLogic.publicCards(meets, deviceTokenHash),
        )
    }

    suspend fun getPublic(id: String, deviceTokenHash: String?): MeetDetailResponse? {
        val meet = repository.findMeet(id) ?: return null
        if (!MeetLogic.isPublished(meet) && meet.deviceTokenHash != deviceTokenHash?.trim().orEmpty()) {
            return null
        }
        if (!MeetLogic.isPublished(meet) && !MeetLogic.isPending(meet)) return null
        val pendingFromViewer = MeetLogic.hasPendingPitchFrom(
            repository.findPitchesByMeet(id),
            id,
            deviceTokenHash,
        )
        val card = MeetLogic.toPublicCard(meet, deviceTokenHash)
        return MeetDetailResponse(
            meet = card,
            canPitch = MeetLogic.canPitch(meet, deviceTokenHash, pendingFromViewer),
        )
    }

    suspend fun create(
        name: String,
        pitch: String,
        zaloNumber: String,
        pitchLimit: Int,
        photoUrl: String,
        deviceTokenHash: String,
    ): Meet {
        val cleanedName = MeetLogic.sanitizeName(name)
        val cleanedPitch = MeetLogic.sanitizePitch(pitch)
        val cleanedZalo = MeetLogic.sanitizeZalo(zaloNumber)
        val limit = MeetLogic.clampPitchLimit(pitchLimit)
        require(cleanedName.isNotEmpty()) { "A name is required" }
        require(cleanedPitch.isNotEmpty()) { "A pitch is required" }
        require(cleanedZalo.isNotEmpty()) { "A Zalo number is required" }
        require(photoUrl.isNotBlank()) { "A photo is required" }
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val now = clock()
        val meet = Meet(
            id = "meet-${idGenerator()}",
            name = cleanedName,
            photoUrl = photoUrl,
            pitch = cleanedPitch,
            zaloNumber = cleanedZalo,
            remainingPitches = limit,
            maxPitches = limit,
            status = MeetLogic.STATUS_PENDING,
            deviceTokenHash = deviceTokenHash,
            createdAt = now,
            updatedAt = now,
        )
        repository.saveMeet(meet)
        return meet
    }

    suspend fun sendPitch(
        meetId: String,
        message: String,
        zaloNumber: String,
        deviceTokenHash: String,
    ): MeetPitch {
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val meet = repository.findMeet(meetId) ?: error("Meet '$meetId' not found")
        val cleanedMessage = MeetLogic.sanitizePitch(message)
        val cleanedZalo = MeetLogic.sanitizeZalo(zaloNumber)
        require(cleanedMessage.isNotEmpty()) { "A pitch is required" }
        require(cleanedZalo.isNotEmpty()) { "A Zalo number is required" }
        val existing = repository.findPitchesByMeet(meetId)
        val pendingFromViewer = MeetLogic.hasPendingPitchFrom(existing, meetId, deviceTokenHash)
        require(MeetLogic.canPitch(meet, deviceTokenHash, pendingFromViewer)) {
            when {
                meet.deviceTokenHash == deviceTokenHash -> "You cannot pitch your own meet"
                meet.remainingPitches <= 0 -> "This meet has no pitches remaining"
                pendingFromViewer -> "You already sent a pitch"
                else -> "This meet is not open for pitches"
            }
        }
        val now = clock()
        val pitch = MeetPitch(
            id = "mpitch-${idGenerator()}",
            meetId = meetId,
            message = cleanedMessage,
            zaloNumber = cleanedZalo,
            deviceTokenHash = deviceTokenHash,
            status = MeetLogic.PITCH_PENDING,
            createdAt = now,
            updatedAt = now,
        )
        repository.savePitch(pitch)
        return pitch
    }

    suspend fun listAdmin(): AdminMeetsResponse {
        val meets = repository.findAllMeets()
            .sortedWith(compareByDescending<Meet> { it.updatedAt }.thenBy { it.id })
        val pitches = repository.findAllPitches()
        val pendingMeets = meets.filter { MeetLogic.isPending(it) }.map { MeetLogic.toAdminView(it) }
        val published = meets.filter { MeetLogic.isPublished(it) }
        val pendingPitches = pitches
            .filter { it.status == MeetLogic.PITCH_PENDING }
            .sortedByDescending { it.createdAt }
            .map { pitch ->
                val meetName = meets.firstOrNull { it.id == pitch.meetId }?.name.orEmpty()
                MeetLogic.toPitchView(pitch, meetName = meetName, includeZalo = true)
            }
        return AdminMeetsResponse(
            pendingMeets = pendingMeets,
            pendingPitches = pendingPitches,
            meets = published.map { meet ->
                MeetLogic.toAdminView(meet, pitches.filter { it.meetId == meet.id })
            },
        )
    }

    suspend fun approve(id: String): Meet {
        val existing = repository.findMeet(id) ?: error("Meet '$id' not found")
        require(existing.status == MeetLogic.STATUS_PENDING || existing.status == MeetLogic.STATUS_PUBLISHED) {
            "Only pending meets can be approved"
        }
        val now = clock()
        val updated = existing.copy(status = MeetLogic.STATUS_PUBLISHED, updatedAt = now)
        repository.saveMeet(updated)
        return updated
    }

    suspend fun completePitch(id: String): MeetPitch {
        val existing = repository.findPitch(id) ?: error("Pitch '$id' not found")
        require(existing.status == MeetLogic.PITCH_PENDING || existing.status == MeetLogic.PITCH_COMPLETED) {
            "Only pending pitches can be completed"
        }
        val now = clock()
        val updated = existing.copy(status = MeetLogic.PITCH_COMPLETED, updatedAt = now)
        repository.savePitch(updated)
        if (existing.status == MeetLogic.PITCH_PENDING) {
            repository.findMeet(existing.meetId)?.let { meet ->
                repository.saveMeet(MeetLogic.decrementRemaining(meet).copy(updatedAt = now))
            }
        }
        return updated
    }

    suspend fun resetRemaining(id: String): Meet {
        val existing = repository.findMeet(id) ?: error("Meet '$id' not found")
        val updated = MeetLogic.resetRemaining(existing).copy(updatedAt = clock())
        repository.saveMeet(updated)
        return updated
    }

    suspend fun deleteMeet(id: String): Boolean {
        repository.deletePitchesForMeet(id)
        return repository.deleteMeet(id)
    }

    suspend fun deletePitch(id: String): Boolean = repository.deletePitch(id)
}
