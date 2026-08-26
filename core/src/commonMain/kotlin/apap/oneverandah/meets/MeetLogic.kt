package apap.oneverandah.meets

/**
 * Pure Meet rules shared by the server and client.
 *
 * Keep this free of I/O so approval, remaining-pitch counts, and public
 * cards stay deterministic.
 */
object MeetLogic {
    const val STATUS_PENDING = "pending"
    const val STATUS_PUBLISHED = "published"

    const val PITCH_PENDING = "pending"
    const val PITCH_COMPLETED = "completed"

    const val MIN_PITCH_LIMIT = 1
    const val MAX_PITCH_LIMIT = 50
    const val DEFAULT_PITCH_LIMIT = 2
    const val MAX_NAME_LENGTH = 80
    const val MAX_PITCH_LENGTH = 500
    const val CARD_PITCH_CHARS = 80

    fun isPublished(meet: Meet): Boolean = meet.status == STATUS_PUBLISHED

    fun isPending(meet: Meet): Boolean = meet.status == STATUS_PENDING

    fun sanitizeName(raw: String?): String {
        val cleaned = raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        return cleaned.take(MAX_NAME_LENGTH)
    }

    fun sanitizePitch(raw: String?): String = raw?.trim().orEmpty().take(MAX_PITCH_LENGTH)

    fun sanitizeZalo(raw: String?): String =
        raw.orEmpty().filter { it.isDigit() || it == '+' || it.isWhitespace() }.trim()

    fun clampPitchLimit(raw: Int): Int = raw.coerceIn(MIN_PITCH_LIMIT, MAX_PITCH_LIMIT)

    fun parsePitchLimit(raw: String?): Int {
        val parsed = raw?.trim()?.toIntOrNull() ?: DEFAULT_PITCH_LIMIT
        return clampPitchLimit(parsed)
    }

    fun ellipsize(text: String, maxChars: Int = CARD_PITCH_CHARS): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        if (maxChars <= 1) return "…"
        return trimmed.take(maxChars - 1).trimEnd() + "…"
    }

    fun publicCards(meets: List<Meet>, viewerHash: String? = null): List<MeetCard> =
        meets.filter(::isPublished)
            .filter { it.remainingPitches > 0 }
            .sortedWith(newestFirst())
            .map { toPublicCard(it, viewerHash) }

    fun ownerPending(meets: List<Meet>, tokenHash: String?): List<MeetCard> {
        val hash = tokenHash?.trim().orEmpty()
        if (hash.isEmpty()) return emptyList()
        return meets.filter { it.deviceTokenHash == hash && isPending(it) }
            .sortedWith(newestFirst())
            .map { toPublicCard(it, hash) }
    }

    fun toPublicCard(meet: Meet, viewerHash: String? = null): MeetCard {
        val mine = !viewerHash.isNullOrBlank() && meet.deviceTokenHash == viewerHash
        return MeetCard(
            id = meet.id,
            name = meet.name,
            photoUrl = meet.photoUrl,
            pitch = meet.pitch,
            remainingPitches = meet.remainingPitches,
            status = meet.status,
            mine = mine,
            createdAt = meet.createdAt,
        )
    }

    fun toAdminView(meet: Meet, pitches: List<MeetPitch> = emptyList()): AdminMeetView =
        AdminMeetView(
            id = meet.id,
            name = meet.name,
            photoUrl = meet.photoUrl,
            pitch = meet.pitch,
            zaloNumber = meet.zaloNumber,
            remainingPitches = meet.remainingPitches,
            maxPitches = meet.maxPitches,
            status = meet.status,
            createdAt = meet.createdAt,
            updatedAt = meet.updatedAt,
            pitches = pitches.sortedWith(newestPitchFirst()).map {
                toPitchView(
                    pitch = it,
                    meetName = meet.name,
                    includeZalo = true,
                    meetZaloNumber = meet.zaloNumber,
                )
            },
        )

    fun toPitchView(
        pitch: MeetPitch,
        meetName: String = "",
        includeZalo: Boolean = false,
        meetZaloNumber: String = "",
    ): MeetPitchView = MeetPitchView(
        id = pitch.id,
        meetId = pitch.meetId,
        meetName = meetName,
        message = pitch.message,
        zaloNumber = if (includeZalo) pitch.zaloNumber else "",
        meetZaloNumber = if (includeZalo) meetZaloNumber else "",
        status = pitch.status,
        createdAt = pitch.createdAt,
    )

    fun canPitch(
        meet: Meet,
        viewerHash: String?,
        hasPendingPitch: Boolean = false,
    ): Boolean {
        val hash = viewerHash?.trim().orEmpty()
        if (hash.isEmpty()) return false
        if (!isPublished(meet)) return false
        if (meet.remainingPitches <= 0) return false
        if (meet.deviceTokenHash == hash) return false
        if (hasPendingPitch) return false
        return true
    }

    fun hasPendingPitchFrom(pitches: List<MeetPitch>, meetId: String, tokenHash: String?): Boolean {
        val hash = tokenHash?.trim().orEmpty()
        if (hash.isEmpty()) return false
        return pitches.any { it.meetId == meetId && it.deviceTokenHash == hash && it.status == PITCH_PENDING }
    }

    fun decrementRemaining(meet: Meet): Meet =
        meet.copy(remainingPitches = (meet.remainingPitches - 1).coerceAtLeast(0))

    fun resetRemaining(meet: Meet, remaining: Int? = null): Meet {
        val next = remaining?.coerceIn(0, MAX_PITCH_LIMIT) ?: meet.maxPitches.coerceAtLeast(0)
        return meet.copy(
            remainingPitches = next,
            maxPitches = maxOf(meet.maxPitches, next),
        )
    }

    private fun newestFirst(): Comparator<Meet> =
        compareByDescending<Meet> { it.createdAt }.thenBy { it.id }

    private fun newestPitchFirst(): Comparator<MeetPitch> =
        compareByDescending<MeetPitch> { it.createdAt }.thenBy { it.id }
}
