package apap.oneverandah.images

import apap.oneverandah.images.ImageEntity
import apap.oneverandah.images.ImageStatus
import apap.oneverandah.images.ImageTargetType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistence abstraction for [ImageEntity] records.
 *
 * Two implementations exist: [InMemoryImageRepository] (always available, the
 * default) and [apap.oneverandah.images.arango.ArangoImageRepository] (used
 * when ArangoDB is configured and reachable). Both are safe to call from
 * coroutines.
 */
interface ImageRepository {
    suspend fun findByImageId(id: String): ImageEntity?
    suspend fun findByTarget(targetId: String, targetType: ImageTargetType): ImageEntity?
    suspend fun findByStatus(status: ImageStatus): List<ImageEntity>
    suspend fun findAll(): List<ImageEntity>

    /** Inserts or replaces [entity] by its [ImageEntity.id]. */
    suspend fun save(entity: ImageEntity)

    suspend fun updateStatus(id: String, status: ImageStatus)
    suspend fun updateImage(id: String, url: String, status: ImageStatus)
}

/**
 * Thread-safe in-memory repository. Perfect for local development, tests and as
 * a graceful fallback when ArangoDB is not configured. Data does not survive a
 * restart, which is fine because the catalog is re-seeded on every startup.
 */
class InMemoryImageRepository : ImageRepository {
    private val byId = ConcurrentHashMap<String, ImageEntity>()
    private val mutex = Mutex()

    override suspend fun findByImageId(id: String): ImageEntity? = byId[id]

    override suspend fun findByTarget(targetId: String, targetType: ImageTargetType): ImageEntity? =
        byId.values.firstOrNull { it.targetId == targetId && it.targetType == targetType }

    override suspend fun findByStatus(status: ImageStatus): List<ImageEntity> =
        byId.values.filter { it.status == status }.sortedBy { it.createdAt }

    override suspend fun findAll(): List<ImageEntity> =
        byId.values.sortedBy { it.createdAt }

    override suspend fun save(entity: ImageEntity) {
        byId[entity.id] = entity
    }

    override suspend fun updateStatus(id: String, status: ImageStatus) = mutex.withLock {
        byId[id]?.let { byId[id] = it.copy(status = status, updatedAt = System.currentTimeMillis()) }
        Unit
    }

    override suspend fun updateImage(id: String, url: String, status: ImageStatus) = mutex.withLock {
        byId[id]?.let {
            byId[id] = it.copy(imageUrl = url, status = status, updatedAt = System.currentTimeMillis())
        }
        Unit
    }
}
