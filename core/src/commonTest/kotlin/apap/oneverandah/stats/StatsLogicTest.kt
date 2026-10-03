package apap.oneverandah.stats

import apap.oneverandah.locations.LocationLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsLogicTest {
    /** Monday 15 Jan 2024 10:00 in Saigon (UTC+7). */
    private val mondayTenAm = 1_705_287_600_000L

    @Test
    fun epochDayZeroIsUnixEpoch() {
        assertEquals(0, StatsLogic.daysFromCivil(1970, 1, 1))
        assertEquals(Triple(1970, 1, 1), StatsLogic.civilFromDays(0))
    }

    @Test
    fun saigonDayKeyUsesUtcPlusSeven() {
        assertEquals("2024-01-15", StatsLogic.dayKey(mondayTenAm))
        val start = StatsLogic.startOfDayMs("2024-01-15")
        assertEquals("2024-01-15", StatsLogic.dayKey(start))
        assertEquals("2024-01-15", StatsLogic.dayKey(start + LocationLogic.DAY_MS - 1))
        assertEquals("2024-01-14", StatsLogic.dayKey(start - 1))
        assertEquals("2024-01-16", StatsLogic.dayKey(start + LocationLogic.DAY_MS))
    }

    @Test
    fun dayKeysEndingOnFillsInclusiveRange() {
        assertEquals(
            listOf("2024-01-09", "2024-01-10", "2024-01-11", "2024-01-12", "2024-01-13", "2024-01-14", "2024-01-15"),
            StatsLogic.dayKeysEndingOn("2024-01-15", 7),
        )
        assertEquals(30, StatsLogic.dayKeysEndingOn("2024-01-15", 30).size)
        assertEquals("2024-01-18", StatsLogic.addDays("2024-01-15", 3))
    }

    @Test
    fun normalizePathStripsQueryAndCapsLength() {
        assertEquals("/", StatsLogic.normalizePath(null))
        assertEquals("/", StatsLogic.normalizePath("   "))
        assertEquals("/chat", StatsLogic.normalizePath("chat?x=1#top"))
        assertEquals("/stories/abc", StatsLogic.normalizePath("/stories/abc"))
        assertEquals(80, StatsLogic.normalizePath("/" + "a".repeat(200)).length)
        assertEquals("en", StatsLogic.normalizeLanguage(" EN "))
        assertEquals("", StatsLogic.normalizeLanguage(null))
    }

    @Test
    fun reportCountsUniqueVisitorsAndFillsEmptyDays() {
        val dayA = StatsLogic.startOfDayMs("2024-01-15") + 3_600_000
        val dayBefore = StatsLogic.startOfDayMs("2024-01-14") + 3_600_000
        val visits = listOf(
            visit("v1", "dev-a", "/", "en", dayBefore, "2024-01-14"),
            visit("v2", "dev-a", "/chat", "en", dayA, "2024-01-15"),
            visit("v3", "dev-a", "/chat", "en", dayA + 1, "2024-01-15"),
            visit("v4", "dev-b", "/", "vi", dayA, "2024-01-15"),
        )
        val firstSeen = mapOf(
            "dev-a" to "2024-01-14",
            "dev-b" to "2024-01-15",
        )
        val report = StatsLogic.report(
            nowMs = mondayTenAm,
            windowVisits = visits,
            firstSeenDayKeys = firstSeen,
            visitsAllTime = 4,
            visitorsAllTime = 2,
        )

        assertEquals("UTC+7", report.timezone)
        assertEquals(30, report.daily.size)
        assertEquals("2023-12-17", report.daily.first().date)
        assertEquals("2024-01-15", report.daily.last().date)
        assertTrue(report.daily.dropLast(2).all { it.visits == 0L && it.visitors == 0L })

        val yesterday = report.daily[report.daily.size - 2]
        assertEquals("2024-01-14", yesterday.date)
        assertEquals(1, yesterday.visitors)
        assertEquals(1, yesterday.visits)
        assertEquals(1, yesterday.newVisitors)

        val today = report.today
        assertEquals("2024-01-15", today.date)
        assertEquals(2, today.visitors)
        assertEquals(3, today.visits)
        assertEquals(1, today.newVisitors)
        assertEquals(1, report.totals.returningToday)
        assertEquals(2, report.totals.visitors7d)
        assertEquals(4, report.totals.visits7d)
        assertEquals(2, report.totals.visitorsAllTime)
        assertEquals(4, report.totals.visitsAllTime)
        assertEquals(setOf("/", "/chat"), report.topPaths.map { it.key }.toSet())
        assertEquals(2, report.topPaths.first { it.key == "/chat" }.visits)
        assertEquals(setOf("en", "vi"), report.languages.map { it.key }.toSet())
    }

    private fun visit(
        id: String,
        hash: String,
        path: String,
        language: String,
        at: Long,
        day: String,
    ) = VisitRecord(
        id = id,
        deviceTokenHash = hash,
        path = path,
        languageTag = language,
        recordedAt = at,
        dayKey = day,
    )
}
