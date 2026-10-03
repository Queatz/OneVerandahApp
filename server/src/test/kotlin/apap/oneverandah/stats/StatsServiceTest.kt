package apap.oneverandah.stats

import apap.oneverandah.locations.LocationLogic
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StatsServiceTest {
    /** Monday 15 Jan 2024 10:00 in Saigon (UTC+7). */
    private val mondayTenAm = 1_705_287_600_000L

    @Test
    fun recordVisitRequiresDeviceHash() {
        runBlocking {
            val service = service()
            assertFailsWith<IllegalArgumentException> { service.recordVisit("  ") }
        }
    }

    @Test
    fun uniqueVisitorsArePerDayAndNewVisitorsUseFirstSeen() = runBlocking {
        var now = mondayTenAm
        val service = StatsService(
            repository = InMemoryVisitRepository(),
            clock = { now },
            idGenerator = { "id-$now" },
        )

        service.recordVisit("dev-a", "/", "en")
        now += 1_000
        service.recordVisit("dev-a", "/chat", "en")
        now += 1_000
        service.recordVisit("dev-b", "/", "vi")

        val today = service.adminStats()
        assertEquals(2, today.today.visitors)
        assertEquals(3, today.today.visits)
        assertEquals(2, today.today.newVisitors)
        assertEquals(0, today.totals.returningToday)
        assertEquals(30, today.daily.size)

        now += LocationLogic.DAY_MS
        service.recordVisit("dev-a", "/stories", "en")

        val nextDay = service.adminStats()
        assertEquals("2024-01-16", nextDay.today.date)
        assertEquals(1, nextDay.today.visitors)
        assertEquals(1, nextDay.today.visits)
        assertEquals(0, nextDay.today.newVisitors)
        assertEquals(1, nextDay.totals.returningToday)
        assertEquals(2, nextDay.totals.visitors7d)
        assertEquals(4, nextDay.totals.visitsAllTime)
        assertTrue(nextDay.topPaths.any { it.key == "/chat" })
        assertEquals(setOf("en", "vi"), nextDay.languages.map { it.key }.toSet())
    }

    private fun service() = StatsService(
        repository = InMemoryVisitRepository(),
        clock = { mondayTenAm },
        idGenerator = { "id-1" },
    )
}
