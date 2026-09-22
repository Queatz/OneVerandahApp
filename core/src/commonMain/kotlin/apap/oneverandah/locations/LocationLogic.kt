package apap.oneverandah.locations

/**
 * Pure Location rules shared by the server and client.
 *
 * Keep this free of I/O so weekly hours in UTC+7, public cards, and
 * commit/contact gating stay deterministic.
 */
object LocationLogic {
    const val STATUS_PLANNING = "planning"
    const val STATUS_COMING_SOON = "coming_soon"
    const val STATUS_OPERATING = "operating"

    const val DISPLAY_PLANNING = "planning"
    const val DISPLAY_COMING_SOON = "coming_soon"
    const val DISPLAY_OPEN = "open"
    const val DISPLAY_CLOSED = "closed"

    const val BLOCK_TEXT = "text"
    const val BLOCK_IMAGE = "image"

    const val MAX_NAME_LENGTH = 80
    const val MAX_TYPE_LENGTH = 40
    const val MAX_CATEGORY_LENGTH = 40
    const val MAX_ADDRESS_LENGTH = 200
    const val MAX_HIGHLIGHT_LENGTH = 80
    const val MAX_HIGHLIGHTS = 3
    const val MAX_PHOTOS = 20
    const val MAX_CONTENT_BLOCKS = 40
    const val MAX_BLOCK_TEXT_LENGTH = 20_000
    const val MAX_MESSAGE_LENGTH = 500
    const val MAX_MAPS_URL_LENGTH = 500
    const val MIN_AMOUNT_VND = 1_000L
    const val MAX_AMOUNT_VND = 1_000_000_000L

    const val SAIGON_OFFSET_MS = 7L * 60 * 60 * 1000
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val MINUTE_MS = 60L * 1000
    const val MINUTES_PER_DAY = 24 * 60

    fun isPlanning(location: Location): Boolean = normalizeStatus(location.status) == STATUS_PLANNING

    fun isComingSoon(location: Location): Boolean = normalizeStatus(location.status) == STATUS_COMING_SOON

    fun isOperating(location: Location): Boolean = normalizeStatus(location.status) == STATUS_OPERATING

    fun normalizeStatus(raw: String?): String = when (raw?.trim()?.lowercase()) {
        STATUS_COMING_SOON, "coming soon", "coming-soon" -> STATUS_COMING_SOON
        STATUS_OPERATING, DISPLAY_OPEN, DISPLAY_CLOSED -> STATUS_OPERATING
        else -> STATUS_PLANNING
    }

    fun sanitizeName(raw: String?): String = collapseSpaces(raw).take(MAX_NAME_LENGTH)

    fun sanitizeType(raw: String?): String = collapseSpaces(raw).take(MAX_TYPE_LENGTH)

    fun sanitizeCategory(raw: String?): String = collapseSpaces(raw).take(MAX_CATEGORY_LENGTH)

    fun sanitizeAddress(raw: String?): String = collapseSpaces(raw).take(MAX_ADDRESS_LENGTH)

    fun sanitizeMapsUrl(raw: String?): String = raw?.trim().orEmpty().take(MAX_MAPS_URL_LENGTH)

    fun sanitizeZalo(raw: String?): String =
        raw.orEmpty().filter { it.isDigit() || it == '+' || it.isWhitespace() }.trim()

    fun sanitizeMessage(raw: String?): String = raw?.trim().orEmpty().take(MAX_MESSAGE_LENGTH)

    fun sanitizeHighlights(raw: List<String>?): List<String> =
        raw.orEmpty()
            .map { collapseSpaces(it).take(MAX_HIGHLIGHT_LENGTH) }
            .filter { it.isNotEmpty() }
            .take(MAX_HIGHLIGHTS)

    fun sanitizePhotoUrls(raw: List<String>?): List<String> =
        raw.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_PHOTOS)

    fun sanitizeContent(raw: List<LocationContentBlock>?): List<LocationContentBlock> =
        raw.orEmpty().mapNotNull { block ->
            when (block.type.trim().lowercase()) {
                BLOCK_IMAGE -> {
                    val url = block.imageUrl.trim()
                    if (url.isEmpty()) null else LocationContentBlock(type = BLOCK_IMAGE, imageUrl = url)
                }
                else -> {
                    val text = block.text.trim().take(MAX_BLOCK_TEXT_LENGTH)
                    if (text.isEmpty()) null else LocationContentBlock(type = BLOCK_TEXT, text = text)
                }
            }
        }.take(MAX_CONTENT_BLOCKS)

    fun sanitizeSchedule(raw: List<LocationHours>?): List<LocationHours> =
        raw.orEmpty()
            .mapNotNull { slot ->
                val day = slot.dayOfWeek
                if (day !in 1..7) return@mapNotNull null
                val open = slot.openMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
                val close = slot.closeMinutes.coerceIn(0, MINUTES_PER_DAY)
                if (open == close) return@mapNotNull null
                LocationHours(dayOfWeek = day, openMinutes = open, closeMinutes = close)
            }
            .distinctBy { Triple(it.dayOfWeek, it.openMinutes, it.closeMinutes) }
            .sortedWith(compareBy<LocationHours> { it.dayOfWeek }.thenBy { it.openMinutes })

    fun clampAmountVnd(raw: Long): Long = raw.coerceIn(MIN_AMOUNT_VND, MAX_AMOUNT_VND)

    fun parseAmountVnd(raw: String?): Long? {
        val digits = raw.orEmpty().filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val parsed = digits.toLongOrNull() ?: return null
        return clampAmountVnd(parsed)
    }

    fun formatMinutes(minutes: Int): String {
        val wrapped = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val hours = wrapped / 60
        val mins = wrapped % 60
        val hh = hours.toString().padStart(2, '0')
        val mm = mins.toString().padStart(2, '0')
        return "$hh:$mm"
    }

    fun parseMinutes(raw: String?): Int? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(':')
        if (parts.size < 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val mins = parts[1].take(2).toIntOrNull() ?: return null
        if (hours !in 0..24 || mins !in 0..59) return null
        val total = hours * 60 + mins
        if (total < 0 || total > MINUTES_PER_DAY) return null
        return total.coerceAtMost(MINUTES_PER_DAY - 1)
    }

    fun saigonDayOfWeek(nowMs: Long): Int = saigonParts(nowMs).first

    fun saigonMinutesOfDay(nowMs: Long): Int = saigonParts(nowMs).second

    fun hoursSnapshot(location: Location, nowMs: Long): LocationHoursSnapshot =
        hoursSnapshot(location.status, location.schedule, nowMs)

    fun hoursSnapshot(status: String, schedule: List<LocationHours>, nowMs: Long): LocationHoursSnapshot {
        val lifecycle = normalizeStatus(status)
        if (lifecycle == STATUS_PLANNING) return LocationHoursSnapshot(DISPLAY_PLANNING)
        if (lifecycle == STATUS_COMING_SOON) return LocationHoursSnapshot(DISPLAY_COMING_SOON)
        val (day, minutes) = saigonParts(nowMs)
        if (isOpenAt(schedule, day, minutes)) {
            return LocationHoursSnapshot(
                displayStatus = DISPLAY_OPEN,
                closesAt = currentClose(schedule, day, minutes),
            )
        }
        val next = nextOpen(schedule, day, minutes)
        return LocationHoursSnapshot(
            displayStatus = DISPLAY_CLOSED,
            opensAt = next?.second?.let(::formatMinutes),
            opensDayOfWeek = next?.first?.takeIf { it != day },
        )
    }

    fun isOpenAt(schedule: List<LocationHours>, dayOfWeek: Int, minutesOfDay: Int): Boolean {
        for (slot in schedule.filter { it.dayOfWeek == dayOfWeek }) {
            if (containsToday(slot, minutesOfDay)) return true
        }
        val yesterday = previousDay(dayOfWeek)
        return schedule.any { slot ->
            slot.dayOfWeek == yesterday && wrapsOvernight(slot) && minutesOfDay < slot.closeMinutes
        }
    }

    fun canCommit(location: Location): Boolean = isPlanning(location)

    fun canContact(location: Location): Boolean = isComingSoon(location) || isOperating(location)

    fun directionsUrl(location: Location): String = directionsUrl(
        mapsUrl = location.mapsUrl,
        latitude = location.latitude,
        longitude = location.longitude,
        address = location.address,
    )

    fun directionsUrl(
        mapsUrl: String,
        latitude: Double?,
        longitude: Double?,
        address: String,
    ): String {
        val explicit = mapsUrl.trim()
        if (explicit.isNotEmpty()) return explicit
        if (latitude != null && longitude != null) {
            return "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
        }
        val query = address.trim().replace(" ", "+")
        if (query.isEmpty()) return ""
        return "https://www.google.com/maps/search/?api=1&query=$query"
    }

    fun publicCards(
        locations: List<Location>,
        likedIds: Set<String> = emptySet(),
        nowMs: Long,
    ): List<LocationCard> =
        locations.sortedWith(newestFirst()).map { toPublicCard(it, likedIds, nowMs) }

    fun categories(locations: List<Location>): List<String> =
        locations.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    fun matchesQuery(card: LocationCard, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return listOf(card.name, card.type, card.category, card.address)
            .any { it.lowercase().contains(q) } ||
            card.highlights.any { it.lowercase().contains(q) }
    }

    fun toPublicCard(
        location: Location,
        likedIds: Set<String> = emptySet(),
        nowMs: Long,
    ): LocationCard {
        val hours = hoursSnapshot(location, nowMs)
        return LocationCard(
            id = location.id,
            name = location.name,
            type = location.type,
            category = location.category,
            status = normalizeStatus(location.status),
            displayStatus = hours.displayStatus,
            address = location.address,
            highlights = location.highlights,
            photoUrls = location.photoUrls,
            schedule = location.schedule,
            closesAt = hours.closesAt,
            opensAt = hours.opensAt,
            opensDayOfWeek = hours.opensDayOfWeek,
            likeCount = location.likeCount.coerceAtLeast(0),
            liked = location.id in likedIds,
            mapsUrl = directionsUrl(location),
            hostZalo = location.hostZalo,
            createdAt = location.createdAt,
        )
    }

    fun toAdminView(
        location: Location,
        commitments: List<LocationCommitment> = emptyList(),
        contacts: List<LocationContact> = emptyList(),
    ): AdminLocationView = AdminLocationView(
        id = location.id,
        name = location.name,
        type = location.type,
        category = location.category,
        status = normalizeStatus(location.status),
        address = location.address,
        highlights = location.highlights,
        photoUrls = location.photoUrls,
        content = location.content,
        schedule = location.schedule,
        hostZalo = location.hostZalo,
        mapsUrl = location.mapsUrl,
        latitude = location.latitude,
        longitude = location.longitude,
        likeCount = location.likeCount,
        createdAt = location.createdAt,
        updatedAt = location.updatedAt,
        commitments = commitments.sortedWith(newestCommitmentFirst()).map {
            toCommitmentView(it, location.name)
        },
        contacts = contacts.sortedWith(newestContactFirst()).map {
            toContactView(it, location.name)
        },
    )

    fun toCommitmentView(commitment: LocationCommitment, locationName: String = ""): LocationCommitmentView =
        LocationCommitmentView(
            id = commitment.id,
            locationId = commitment.locationId,
            locationName = locationName,
            amountVnd = commitment.amountVnd,
            message = commitment.message,
            zaloNumber = commitment.zaloNumber,
            createdAt = commitment.createdAt,
        )

    fun toContactView(contact: LocationContact, locationName: String = ""): LocationContactView =
        LocationContactView(
            id = contact.id,
            locationId = contact.locationId,
            locationName = locationName,
            message = contact.message,
            zaloNumber = contact.zaloNumber,
            createdAt = contact.createdAt,
        )

    fun applyLikeDelta(location: Location, liked: Boolean): Location {
        val next = if (liked) location.likeCount + 1 else location.likeCount - 1
        return location.copy(likeCount = next.coerceAtLeast(0))
    }

    internal fun saigonParts(nowMs: Long): Pair<Int, Int> {
        val local = nowMs + SAIGON_OFFSET_MS
        val days = local / DAY_MS
        val millisOfDay = local % DAY_MS
        val minutesOfDay = (millisOfDay / MINUTE_MS).toInt()
        val isoDow = ((days + 3) % 7).toInt() + 1
        return isoDow to minutesOfDay
    }

    internal fun wrapsOvernight(slot: LocationHours): Boolean = slot.closeMinutes <= slot.openMinutes

    private fun containsToday(slot: LocationHours, minutesOfDay: Int): Boolean {
        return if (wrapsOvernight(slot)) {
            minutesOfDay >= slot.openMinutes
        } else {
            minutesOfDay >= slot.openMinutes && minutesOfDay < slot.closeMinutes
        }
    }

    private fun currentClose(schedule: List<LocationHours>, day: Int, minutes: Int): String? {
        val yesterday = previousDay(day)
        schedule.firstOrNull { slot ->
            slot.dayOfWeek == yesterday && wrapsOvernight(slot) && minutes < slot.closeMinutes
        }?.let { return formatMinutes(it.closeMinutes) }
        schedule.firstOrNull { slot ->
            slot.dayOfWeek == day && containsToday(slot, minutes)
        }?.let { return formatMinutes(it.closeMinutes) }
        return null
    }

    private fun nextOpen(schedule: List<LocationHours>, day: Int, minutes: Int): Pair<Int, Int>? {
        if (schedule.isEmpty()) return null
        val todayLater = schedule
            .filter { it.dayOfWeek == day && it.openMinutes > minutes }
            .minByOrNull { it.openMinutes }
        if (todayLater != null) return day to todayLater.openMinutes
        for (offset in 1..7) {
            val nextDay = ((day - 1 + offset) % 7) + 1
            val slot = schedule.filter { it.dayOfWeek == nextDay }.minByOrNull { it.openMinutes }
            if (slot != null) return nextDay to slot.openMinutes
        }
        return null
    }

    private fun previousDay(day: Int): Int = if (day == 1) 7 else day - 1

    private fun collapseSpaces(raw: String?): String =
        raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()

    private fun newestFirst(): Comparator<Location> =
        compareByDescending<Location> { it.createdAt }.thenBy { it.id }

    private fun newestCommitmentFirst(): Comparator<LocationCommitment> =
        compareByDescending<LocationCommitment> { it.createdAt }.thenBy { it.id }

    private fun newestContactFirst(): Comparator<LocationContact> =
        compareByDescending<LocationContact> { it.createdAt }.thenBy { it.id }
}
