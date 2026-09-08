package apap.oneverandah.locations

import java.util.concurrent.ConcurrentHashMap

interface LocationRepository {
    suspend fun findAll(): List<Location>
    suspend fun find(id: String): Location?
    suspend fun save(location: Location)
    suspend fun delete(id: String): Boolean

    suspend fun findLikesByLocation(locationId: String): List<LocationLike>
    suspend fun findLike(locationId: String, deviceTokenHash: String): LocationLike?
    suspend fun saveLike(like: LocationLike)
    suspend fun deleteLike(id: String): Boolean
    suspend fun deleteLikesFor(locationId: String)

    suspend fun findCommitmentsByLocation(locationId: String): List<LocationCommitment>
    suspend fun saveCommitment(commitment: LocationCommitment)
    suspend fun deleteCommitmentsFor(locationId: String)

    suspend fun findContactsByLocation(locationId: String): List<LocationContact>
    suspend fun saveContact(contact: LocationContact)
    suspend fun deleteContactsFor(locationId: String)
}

/** Development / fallback store kept entirely in memory. */
class InMemoryLocationRepository : LocationRepository {
    private val locations = ConcurrentHashMap<String, Location>()
    private val likes = ConcurrentHashMap<String, LocationLike>()
    private val commitments = ConcurrentHashMap<String, LocationCommitment>()
    private val contacts = ConcurrentHashMap<String, LocationContact>()

    override suspend fun findAll(): List<Location> =
        locations.values.sortedByDescending { it.createdAt }

    override suspend fun find(id: String): Location? = locations[id]

    override suspend fun save(location: Location) {
        locations[location.id] = location
    }

    override suspend fun delete(id: String): Boolean {
        val removed = locations.remove(id) != null
        deleteLikesFor(id)
        deleteCommitmentsFor(id)
        deleteContactsFor(id)
        return removed
    }

    override suspend fun findLikesByLocation(locationId: String): List<LocationLike> =
        likes.values.filter { it.locationId == locationId }.sortedByDescending { it.createdAt }

    override suspend fun findLike(locationId: String, deviceTokenHash: String): LocationLike? =
        likes.values.firstOrNull { it.locationId == locationId && it.deviceTokenHash == deviceTokenHash }

    override suspend fun saveLike(like: LocationLike) {
        likes[like.id] = like
    }

    override suspend fun deleteLike(id: String): Boolean = likes.remove(id) != null

    override suspend fun deleteLikesFor(locationId: String) {
        likes.values.filter { it.locationId == locationId }.forEach { likes.remove(it.id) }
    }

    override suspend fun findCommitmentsByLocation(locationId: String): List<LocationCommitment> =
        commitments.values.filter { it.locationId == locationId }.sortedByDescending { it.createdAt }

    override suspend fun saveCommitment(commitment: LocationCommitment) {
        commitments[commitment.id] = commitment
    }

    override suspend fun deleteCommitmentsFor(locationId: String) {
        commitments.values.filter { it.locationId == locationId }.forEach { commitments.remove(it.id) }
    }

    override suspend fun findContactsByLocation(locationId: String): List<LocationContact> =
        contacts.values.filter { it.locationId == locationId }.sortedByDescending { it.createdAt }

    override suspend fun saveContact(contact: LocationContact) {
        contacts[contact.id] = contact
    }

    override suspend fun deleteContactsFor(locationId: String) {
        contacts.values.filter { it.locationId == locationId }.forEach { contacts.remove(it.id) }
    }
}
