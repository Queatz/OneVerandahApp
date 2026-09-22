package apap.oneverandah.locations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocationLogicTest {
    /** Monday 15 Jan 2024 10:00 in Saigon (UTC+7). */
    private val mondayTenAm = 1_705_287_600_000L

    /** Friday 19 Jan 2024 23:00 in Saigon. */
    private val fridayElevenPm = mondayTenAm + 4 * LocationLogic.DAY_MS + 13 * 60 * LocationLogic.MINUTE_MS

    /** Saturday 20 Jan 2024 01:00 in Saigon. */
    private val saturdayOneAm = fridayElevenPm + 2 * 60 * LocationLogic.MINUTE_MS

    @Test
    fun saigonClockReadsMondayMorning() {
        assertEquals(1, LocationLogic.saigonDayOfWeek(mondayTenAm))
        assertEquals(10 * 60, LocationLogic.saigonMinutesOfDay(mondayTenAm))
    }

    @Test
    fun operatingPlaceIsOpenDuringWeekdayHours() {
        val cafe = place(
            status = LocationLogic.STATUS_OPERATING,
            schedule = listOf(hours(1, "09:00", "18:00")),
        )
        val snap = LocationLogic.hoursSnapshot(cafe, mondayTenAm)
        assertEquals(LocationLogic.DISPLAY_OPEN, snap.displayStatus)
        assertEquals("18:00", snap.closesAt)
        assertNull(snap.opensAt)
    }

    @Test
    fun operatingPlaceIsClosedAfterHoursAndShowsNextOpen() {
        val cafe = place(
            status = LocationLogic.STATUS_OPERATING,
            schedule = listOf(
                hours(1, "09:00", "18:00"),
                hours(2, "09:00", "18:00"),
            ),
        )
        val mondayEightPm = mondayTenAm + 10 * 60 * LocationLogic.MINUTE_MS
        val snap = LocationLogic.hoursSnapshot(cafe, mondayEightPm)
        assertEquals(LocationLogic.DISPLAY_CLOSED, snap.displayStatus)
        assertEquals("09:00", snap.opensAt)
        assertEquals(2, snap.opensDayOfWeek)
    }

    @Test
    fun overnightHoursStayOpenPastMidnight() {
        val bar = place(
            status = LocationLogic.STATUS_OPERATING,
            schedule = listOf(hours(5, "22:00", "02:00")),
        )
        val friday = LocationLogic.hoursSnapshot(bar, fridayElevenPm)
        assertEquals(LocationLogic.DISPLAY_OPEN, friday.displayStatus)
        assertEquals("02:00", friday.closesAt)

        val saturday = LocationLogic.hoursSnapshot(bar, saturdayOneAm)
        assertEquals(LocationLogic.DISPLAY_OPEN, saturday.displayStatus)
        assertEquals("02:00", saturday.closesAt)

        val saturdayThree = LocationLogic.hoursSnapshot(bar, saturdayOneAm + 2 * 60 * LocationLogic.MINUTE_MS)
        assertEquals(LocationLogic.DISPLAY_CLOSED, saturdayThree.displayStatus)
        assertEquals("22:00", saturdayThree.opensAt)
        assertEquals(5, saturdayThree.opensDayOfWeek)
    }

    @Test
    fun planningAndComingSoonIgnoreSchedule() {
        val planning = place(status = LocationLogic.STATUS_PLANNING, schedule = listOf(hours(1, "09:00", "18:00")))
        val soon = place(status = LocationLogic.STATUS_COMING_SOON, schedule = listOf(hours(1, "09:00", "18:00")))
        assertEquals(LocationLogic.DISPLAY_PLANNING, LocationLogic.hoursSnapshot(planning, mondayTenAm).displayStatus)
        assertEquals(LocationLogic.DISPLAY_COMING_SOON, LocationLogic.hoursSnapshot(soon, mondayTenAm).displayStatus)
        assertTrue(LocationLogic.canCommit(planning))
        assertFalse(LocationLogic.canCommit(soon))
        assertFalse(LocationLogic.canContact(planning))
        assertTrue(LocationLogic.canContact(soon))
        assertTrue(LocationLogic.canContact(place(status = LocationLogic.STATUS_OPERATING)))
    }

    @Test
    fun publicCardsSortNewestFirstAndHideZalo() {
        val older = place("old", name = "Old", createdAt = 10, hostZalo = "090111")
        val newer = place("new", name = "New", createdAt = 50, hostZalo = "090222")
        val cards = LocationLogic.publicCards(listOf(older, newer), likedIds = setOf("new"), nowMs = mondayTenAm)
        assertEquals(listOf("new", "old"), cards.map { it.id })
        assertTrue(cards.first().liked)
        assertFalse(cards.last().liked)
        assertTrue(cards.none { "090" in it.address })
        assertEquals("", cards.first().mapsUrl.takeIf { it.contains("090") }.orEmpty())
    }

    @Test
    fun publicCardCopiesHostZalo() {
        val card = LocationLogic.toPublicCard(
            place(hostZalo = "0901234567", address = "1 River"),
            nowMs = mondayTenAm,
        )
        assertEquals("1 River", card.address)
        assertEquals("0901234567", card.hostZalo)
        assertFalse(card.liked)
    }

    @Test
    fun searchMatchesNameTypeCategoryAddressAndHighlights() {
        val card = LocationLogic.toPublicCard(
            place(
                name = "River Cafe",
                type = "Coffee",
                category = "Food",
                address = "Lobby 2",
                highlights = listOf("Pour over", "Quiet nook"),
            ),
            nowMs = mondayTenAm,
        )
        assertTrue(LocationLogic.matchesQuery(card, "river"))
        assertTrue(LocationLogic.matchesQuery(card, "coffee"))
        assertTrue(LocationLogic.matchesQuery(card, "food"))
        assertTrue(LocationLogic.matchesQuery(card, "lobby"))
        assertTrue(LocationLogic.matchesQuery(card, "nook"))
        assertFalse(LocationLogic.matchesQuery(card, "yoga"))
        assertTrue(LocationLogic.matchesQuery(card, "  "))
    }

    @Test
    fun sanitizeAndParseInputs() {
        assertEquals("River Cafe", LocationLogic.sanitizeName("  River   Cafe  "))
        assertEquals("0901 234 567", LocationLogic.sanitizeZalo("0901 234 567abc"))
        assertEquals(listOf("A", "B", "C"), LocationLogic.sanitizeHighlights(listOf(" A ", "", "B", "C", "D")))
        assertEquals(1_000L, LocationLogic.parseAmountVnd("500"))
        assertEquals(50_000L, LocationLogic.parseAmountVnd("50,000 ₫"))
        assertEquals(540, LocationLogic.parseMinutes("09:00"))
        assertEquals("09:00", LocationLogic.formatMinutes(540))
        assertEquals(LocationLogic.STATUS_OPERATING, LocationLogic.normalizeStatus("open"))
        assertEquals(LocationLogic.STATUS_COMING_SOON, LocationLogic.normalizeStatus("Coming Soon"))
    }

    @Test
    fun directionsPreferMapsUrlThenCoordinatesThenAddress() {
        assertEquals(
            "https://maps.example/x",
            LocationLogic.directionsUrl("https://maps.example/x", 10.0, 106.0, "Lobby"),
        )
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=10.8,106.7",
            LocationLogic.directionsUrl("", 10.8, 106.7, "Lobby"),
        )
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Lobby+2",
            LocationLogic.directionsUrl("", null, null, "Lobby 2"),
        )
        assertEquals("", LocationLogic.directionsUrl("", null, null, "  "))
    }

    @Test
    fun likeDeltaNeverGoesNegative() {
        val liked = LocationLogic.applyLikeDelta(place(likeCount = 2), liked = true)
        assertEquals(3, liked.likeCount)
        assertEquals(0, LocationLogic.applyLikeDelta(place(likeCount = 0), liked = false).likeCount)
    }

    private fun hours(day: Int, open: String, close: String) = LocationHours(
        dayOfWeek = day,
        openMinutes = LocationLogic.parseMinutes(open) ?: 0,
        closeMinutes = LocationLogic.parseMinutes(close) ?: 0,
    )

    private fun place(
        id: String = "loc",
        name: String = "Cafe",
        type: String = "Cafe",
        category: String = "Food",
        status: String = LocationLogic.STATUS_PLANNING,
        address: String = "Lobby",
        highlights: List<String> = listOf("Wifi"),
        photoUrls: List<String> = listOf("photo.jpg"),
        schedule: List<LocationHours> = emptyList(),
        hostZalo: String = "090000",
        likeCount: Int = 0,
        createdAt: Long = 0,
    ) = Location(
        id = id,
        name = name,
        type = type,
        category = category,
        status = status,
        address = address,
        highlights = highlights,
        photoUrls = photoUrls,
        schedule = schedule,
        hostZalo = hostZalo,
        likeCount = likeCount,
        createdAt = createdAt,
    )
}
