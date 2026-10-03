package apap.oneverandah.stats

import kotlinx.serialization.Serializable

/**
 * One recorded app open / navigation ping.
 *
 * [dayKey] is the calendar day in UTC+7 (`yyyy-MM-dd`) so daily unique
 * visitors stay stable regardless of the server's local timezone.
 */
@Serializable
data class VisitRecord(
    val id: String,
    val deviceTokenHash: String,
    val path: String = "/",
    val languageTag: String = "",
    val recordedAt: Long,
    val dayKey: String,
)

@Serializable
data class VisitPingRequest(
    val path: String = "/",
    val languageTag: String = "",
)

@Serializable
data class VisitPingResponse(
    val ok: Boolean = true,
)

@Serializable
data class DailyVisitStat(
    val date: String = "",
    val visitors: Long = 0,
    val visits: Long = 0,
    val newVisitors: Long = 0,
)

@Serializable
data class StatsTotals(
    val visitorsToday: Long = 0,
    val visitsToday: Long = 0,
    val newVisitorsToday: Long = 0,
    val returningToday: Long = 0,
    val visitors7d: Long = 0,
    val visits7d: Long = 0,
    val visitors30d: Long = 0,
    val visits30d: Long = 0,
    val visitorsAllTime: Long = 0,
    val visitsAllTime: Long = 0,
)

@Serializable
data class NamedCountStat(
    val key: String = "",
    val visits: Long = 0,
    val visitors: Long = 0,
)

@Serializable
data class AdminStatsResponse(
    val timezone: String = StatsLogic.TIMEZONE,
    val generatedAt: Long = 0,
    val today: DailyVisitStat = DailyVisitStat(),
    val totals: StatsTotals = StatsTotals(),
    val daily: List<DailyVisitStat> = emptyList(),
    val topPaths: List<NamedCountStat> = emptyList(),
    val languages: List<NamedCountStat> = emptyList(),
)

@Serializable
data class StatsApiError(
    val error: String,
)
