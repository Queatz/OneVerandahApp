package apap.oneverandah.stats

import apap.oneverandah.locations.LocationLogic

/**
 * Pure visitor-stats rules shared by the server and tests.
 *
 * Days are always Vietnam time (UTC+7) so the admin graph matches how
 * people at One Verandah experience "today".
 */
object StatsLogic {
    const val TIMEZONE = "UTC+7"
    const val GRAPH_DAYS = 30
    const val WEEK_DAYS = 7
    const val MAX_PATH_LENGTH = 80
    const val TOP_LIMIT = 8

    fun dayKey(nowMs: Long): String {
        val epochDay = (nowMs + LocationLogic.SAIGON_OFFSET_MS).floorDiv(LocationLogic.DAY_MS)
        return formatDay(epochDay)
    }

    fun startOfDayMs(dayKey: String): Long =
        parseDay(dayKey) * LocationLogic.DAY_MS - LocationLogic.SAIGON_OFFSET_MS

    fun addDays(dayKey: String, days: Int): String = formatDay(parseDay(dayKey) + days)

    fun dayKeysEndingOn(endKey: String, count: Int): List<String> {
        val end = parseDay(endKey)
        val n = count.coerceAtLeast(1)
        return (n - 1 downTo 0).map { formatDay(end - it) }
    }

    fun normalizePath(raw: String?): String {
        val trimmed = raw.orEmpty()
            .trim()
            .substringBefore('?')
            .substringBefore('#')
            .ifBlank { "/" }
        val withSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return withSlash.take(MAX_PATH_LENGTH).ifBlank { "/" }
    }

    fun normalizeLanguage(raw: String?): String =
        raw?.trim()?.lowercase()?.take(8).orEmpty()

    fun report(
        nowMs: Long,
        windowVisits: List<VisitRecord>,
        firstSeenDayKeys: Map<String, String>,
        visitsAllTime: Long,
        visitorsAllTime: Long,
    ): AdminStatsResponse {
        val today = dayKey(nowMs)
        val dates = dayKeysEndingOn(today, GRAPH_DAYS)
        val dateSet = dates.toSet()
        val inWindow = windowVisits.filter { it.dayKey in dateSet }
        val byDay = inWindow.groupBy { it.dayKey }

        val daily = dates.map { date ->
            val rows = byDay[date].orEmpty()
            val hashes = rows.map { it.deviceTokenHash }.toSet()
            DailyVisitStat(
                date = date,
                visitors = hashes.size.toLong(),
                visits = rows.size.toLong(),
                newVisitors = hashes.count { firstSeenDayKeys[it] == date }.toLong(),
            )
        }

        val last7Dates = dates.takeLast(WEEK_DAYS).toSet()
        val last7 = inWindow.filter { it.dayKey in last7Dates }
        val todayStat = daily.last()

        return AdminStatsResponse(
            timezone = TIMEZONE,
            generatedAt = nowMs,
            today = todayStat,
            totals = StatsTotals(
                visitorsToday = todayStat.visitors,
                visitsToday = todayStat.visits,
                newVisitorsToday = todayStat.newVisitors,
                returningToday = (todayStat.visitors - todayStat.newVisitors).coerceAtLeast(0),
                visitors7d = last7.map { it.deviceTokenHash }.toSet().size.toLong(),
                visits7d = last7.size.toLong(),
                visitors30d = inWindow.map { it.deviceTokenHash }.toSet().size.toLong(),
                visits30d = inWindow.size.toLong(),
                visitorsAllTime = visitorsAllTime.coerceAtLeast(0),
                visitsAllTime = visitsAllTime.coerceAtLeast(0),
            ),
            daily = daily,
            topPaths = namedCounts(inWindow) { it.path.ifBlank { "/" } },
            languages = namedCounts(inWindow) { it.languageTag.ifBlank { "unknown" } },
        )
    }

    private fun namedCounts(
        visits: List<VisitRecord>,
        keyOf: (VisitRecord) -> String,
    ): List<NamedCountStat> =
        visits.groupBy(keyOf)
            .map { (key, rows) ->
                NamedCountStat(
                    key = key,
                    visits = rows.size.toLong(),
                    visitors = rows.map { it.deviceTokenHash }.toSet().size.toLong(),
                )
            }
            .sortedWith(compareByDescending<NamedCountStat> { it.visits }.thenBy { it.key })
            .take(TOP_LIMIT)

    internal fun formatDay(epochDay: Long): String {
        val (year, month, day) = civilFromDays(epochDay)
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    internal fun parseDay(dayKey: String): Long {
        val parts = dayKey.split('-')
        require(parts.size == 3) { "Invalid day key '$dayKey'" }
        return daysFromCivil(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }

    /** Howard Hinnant's `days_from_civil` — days since 1970-01-01. */
    internal fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        var y = year
        y -= if (month <= 2) 1 else 0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (month + if (month > 2) -3 else 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097 + doe - 719468
    }

    /** Howard Hinnant's `civil_from_days`. */
    internal fun civilFromDays(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719468
        val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + if (mp < 10) 3 else -9
        val year = y + if (m <= 2) 1 else 0
        return Triple(year.toInt(), m.toInt(), d.toInt())
    }
}
