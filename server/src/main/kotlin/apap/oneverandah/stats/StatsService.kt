package apap.oneverandah.stats

import java.util.UUID

/**
 * Records visitor pings and builds the admin stats snapshot.
 *
 * Aggregation lives in [StatsLogic] so timezone buckets stay easy to test.
 */
class StatsService(
    private val repository: VisitRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun recordVisit(
        deviceTokenHash: String,
        path: String? = null,
        languageTag: String? = null,
    ): VisitRecord {
        val hash = deviceTokenHash.trim()
        require(hash.isNotEmpty()) { "Device token required" }
        val now = clock()
        val visit = VisitRecord(
            id = idGenerator(),
            deviceTokenHash = hash,
            path = StatsLogic.normalizePath(path),
            languageTag = StatsLogic.normalizeLanguage(languageTag),
            recordedAt = now,
            dayKey = StatsLogic.dayKey(now),
        )
        repository.save(visit)
        return visit
    }

    suspend fun adminStats(): AdminStatsResponse {
        val now = clock()
        val today = StatsLogic.dayKey(now)
        val fromMs = StatsLogic.startOfDayMs(StatsLogic.addDays(today, 1 - StatsLogic.GRAPH_DAYS))
        return StatsLogic.report(
            nowMs = now,
            windowVisits = repository.listSince(fromMs),
            firstSeenDayKeys = repository.firstSeenDayKeys(),
            visitsAllTime = repository.countAll(),
            visitorsAllTime = repository.countUniqueDevices(),
        )
    }
}
