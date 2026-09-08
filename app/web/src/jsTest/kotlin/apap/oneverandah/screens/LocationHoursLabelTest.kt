package apap.oneverandah.screens

import apap.oneverandah.locations.LocationHours
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationHoursLabelTest {
    @Test
    fun rangeFormatsOpenAndClose() {
        assertEquals("09:00–18:00", locationHoursRange(LocationHours(1, 9 * 60, 18 * 60)))
    }

    @Test
    fun overnightRangeWrapsPastMidnight() {
        assertEquals("22:00–02:00", locationHoursRange(LocationHours(5, 22 * 60, 2 * 60)))
    }

    @Test
    fun multipleSlotsJoinWithComma() {
        val slots = listOf(
            LocationHours(1, 9 * 60, 12 * 60),
            LocationHours(1, 14 * 60, 18 * 60),
        )
        assertEquals("09:00–12:00, 14:00–18:00", slots.joinToString(", ") { locationHoursRange(it) })
    }
}
