package apap.oneverandah.phrases

import apap.oneverandah.phrases.ReferencePhoto as ReferencePhotoModel
import java.util.concurrent.ConcurrentHashMap

interface ReferencePhotoRepository {
    suspend fun findAll(): List<ReferencePhotoModel>
    suspend fun findById(id: String): ReferencePhotoModel?
    suspend fun save(photo: ReferencePhotoModel)
    suspend fun delete(id: String): Boolean
}

class InMemoryReferencePhotoRepository : ReferencePhotoRepository {
    private val items = ConcurrentHashMap<String, ReferencePhotoModel>()

    override suspend fun findAll(): List<ReferencePhotoModel> =
        items.values.sortedByDescending { it.createdAt }

    override suspend fun findById(id: String): ReferencePhotoModel? = items[id]

    override suspend fun save(photo: ReferencePhotoModel) {
        items[photo.id] = photo
    }

    override suspend fun delete(id: String): Boolean = items.remove(id) != null
}
