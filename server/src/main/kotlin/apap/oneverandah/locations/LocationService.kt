package apap.oneverandah.locations

import java.util.UUID

/**
 * Location persistence, weekly hours, likes, funding, and host contact.
 *
 * Photo upload lives on [LocationSystem] so this class stays easy to test with
 * an in-memory store.
 */
class LocationService(
    private val repository: LocationRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun feed(deviceTokenHash: String?): LocationsFeedResponse {
        val locations = repository.findAll()
        val likedIds = likedIds(locations, deviceTokenHash)
        return LocationsFeedResponse(
            locations = LocationLogic.publicCards(locations, likedIds, clock()),
            categories = LocationLogic.categories(locations),
        )
    }

    suspend fun getPublic(id: String, deviceTokenHash: String?): LocationDetailResponse? {
        val location = repository.find(id) ?: return null
        val liked = deviceTokenHash
            ?.takeIf { it.isNotBlank() }
            ?.let { repository.findLike(id, it) } != null
        val likedIds = if (liked) setOf(id) else emptySet()
        return LocationDetailResponse(
            location = LocationLogic.toPublicCard(location, likedIds, clock()),
            content = location.content,
            canCommit = LocationLogic.canCommit(location),
            canContact = LocationLogic.canContact(location),
        )
    }

    suspend fun upsert(request: LocationUpsertRequest): Location {
        val name = LocationLogic.sanitizeName(request.name)
        val category = LocationLogic.sanitizeCategory(request.category)
        require(name.isNotEmpty()) { "A name is required" }
        require(category.isNotEmpty()) { "A category is required" }
        val now = clock()
        val existingId = request.id?.trim().orEmpty()
        val existing = existingId.takeIf { it.isNotEmpty() }?.let { repository.find(it) }
        val id = existing?.id
            ?: existingId.takeIf { it.isNotEmpty() }
            ?: "location-${idGenerator()}"
        val location = Location(
            id = id,
            name = name,
            type = LocationLogic.sanitizeType(request.type),
            category = category,
            status = LocationLogic.normalizeStatus(request.status),
            address = LocationLogic.sanitizeAddress(request.address),
            highlights = LocationLogic.sanitizeHighlights(request.highlights),
            photoUrls = LocationLogic.sanitizePhotoUrls(request.photoUrls),
            content = LocationLogic.sanitizeContent(request.content),
            schedule = LocationLogic.sanitizeSchedule(request.schedule),
            hostZalo = LocationLogic.sanitizeZalo(request.hostZalo),
            mapsUrl = LocationLogic.sanitizeMapsUrl(request.mapsUrl),
            latitude = request.latitude,
            longitude = request.longitude,
            likeCount = existing?.likeCount ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        repository.save(location)
        return location
    }

    suspend fun toggleLike(id: String, deviceTokenHash: String): LocationCard {
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val location = repository.find(id) ?: error("Location '$id' not found")
        val existing = repository.findLike(id, deviceTokenHash)
        val now = clock()
        val liked = if (existing == null) {
            repository.saveLike(
                LocationLike(
                    id = "llike-${idGenerator()}",
                    locationId = id,
                    deviceTokenHash = deviceTokenHash,
                    createdAt = now,
                ),
            )
            true
        } else {
            repository.deleteLike(existing.id)
            false
        }
        val updated = LocationLogic.applyLikeDelta(location, liked).copy(updatedAt = now)
        repository.save(updated)
        val likedIds = if (liked) setOf(id) else emptySet()
        return LocationLogic.toPublicCard(updated, likedIds, now)
    }

    suspend fun commitFunds(
        locationId: String,
        amountVnd: Long,
        message: String,
        zaloNumber: String,
        deviceTokenHash: String,
    ): LocationCommitment {
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val location = repository.find(locationId) ?: error("Location '$locationId' not found")
        require(LocationLogic.canCommit(location)) { "This location is not accepting funds" }
        require(amountVnd >= LocationLogic.MIN_AMOUNT_VND) { "An amount is required" }
        val amount = LocationLogic.clampAmountVnd(amountVnd)
        val cleanedMessage = LocationLogic.sanitizeMessage(message)
        val cleanedZalo = LocationLogic.sanitizeZalo(zaloNumber)
        require(cleanedZalo.isNotEmpty()) { "A Zalo number is required" }
        val now = clock()
        val commitment = LocationCommitment(
            id = "lcommit-${idGenerator()}",
            locationId = locationId,
            amountVnd = amount,
            message = cleanedMessage,
            zaloNumber = cleanedZalo,
            deviceTokenHash = deviceTokenHash,
            createdAt = now,
        )
        repository.saveCommitment(commitment)
        return commitment
    }

    suspend fun contactHost(
        locationId: String,
        message: String,
        zaloNumber: String,
        deviceTokenHash: String,
    ): LocationContact {
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val location = repository.find(locationId) ?: error("Location '$locationId' not found")
        require(LocationLogic.canContact(location)) { "This location cannot be contacted yet" }
        val cleanedMessage = LocationLogic.sanitizeMessage(message)
        val cleanedZalo = LocationLogic.sanitizeZalo(zaloNumber)
        require(cleanedMessage.isNotEmpty()) { "A message is required" }
        require(cleanedZalo.isNotEmpty()) { "A Zalo number is required" }
        val now = clock()
        val contact = LocationContact(
            id = "lcontact-${idGenerator()}",
            locationId = locationId,
            message = cleanedMessage,
            zaloNumber = cleanedZalo,
            deviceTokenHash = deviceTokenHash,
            createdAt = now,
        )
        repository.saveContact(contact)
        return contact
    }

    suspend fun listAdmin(): AdminLocationsResponse {
        val locations = repository.findAll()
            .sortedWith(compareByDescending<Location> { it.updatedAt }.thenBy { it.id })
        return AdminLocationsResponse(
            locations = locations.map { location ->
                LocationLogic.toAdminView(
                    location = location,
                    commitments = repository.findCommitmentsByLocation(location.id),
                    contacts = repository.findContactsByLocation(location.id),
                )
            },
        )
    }

    suspend fun delete(id: String): Boolean {
        repository.deleteLikesFor(id)
        repository.deleteCommitmentsFor(id)
        repository.deleteContactsFor(id)
        return repository.delete(id)
    }

    private suspend fun likedIds(locations: List<Location>, deviceTokenHash: String?): Set<String> {
        val hash = deviceTokenHash?.trim().orEmpty()
        if (hash.isEmpty()) return emptySet()
        return locations.mapNotNull { location ->
            repository.findLike(location.id, hash)?.locationId
        }.toSet()
    }
}
