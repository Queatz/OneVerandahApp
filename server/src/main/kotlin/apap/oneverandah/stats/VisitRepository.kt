package apap.oneverandah.stats

import java.util.concurrent.ConcurrentHashMap

interface VisitRepository {
    suspend fun save(visit: VisitRecord)
    suspend fun listSince(fromEpochMs: Long): List<VisitRecord>
    suspend fun countAll(): Long
    suspend fun countUniqueDevices(): Long
    suspend fun firstSeenDayKeys(): Map<String, String>
}

/** Development / fallback store kept entirely in memory. */
class InMemoryVisitRepository : VisitRepository {
    private val visits = ConcurrentHashMap<String, VisitRecord>()

    override suspend fun save(visit: VisitRecord) {
        visits[visit.id] = visit
    }

    override suspend fun listSince(fromEpochMs: Long): List<VisitRecord> =
        visits.values.filter { it.recordedAt >= fromEpochMs }

    override suspend fun countAll(): Long = visits.size.toLong()

    override suspend fun countUniqueDevices(): Long =
        visits.values.map { it.deviceTokenHash }.toSet().size.toLong()

    override suspend fun firstSeenDayKeys(): Map<String, String> =
        visits.values
            .groupBy { it.deviceTokenHash }
            .mapValues { (_, rows) -> rows.minOf { it.dayKey } }
}
