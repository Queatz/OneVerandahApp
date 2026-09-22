package apap.oneverandah.locations

import kotlinx.serialization.Serializable

/**
 * A nearby place people can discover, fund, or visit.
 *
 * [status] is the lifecycle the admin sets (`planning`, `coming_soon`, or
 * `operating`). Open vs closed for operating places is computed from
 * [schedule] in UTC+7. [hostZalo] is shown on the public location so people
 * can contact the host directly.
 */
@Serializable
data class Location(
    val id: String,
    val name: String = "",
    val type: String = "",
    val category: String = "",
    val status: String = LocationLogic.STATUS_PLANNING,
    val address: String = "",
    val highlights: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val content: List<LocationContentBlock> = emptyList(),
    val schedule: List<LocationHours> = emptyList(),
    val hostZalo: String = "",
    val mapsUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val likeCount: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** One weekly opening window. [dayOfWeek] is ISO-8601 (1 = Monday … 7 = Sunday). */
@Serializable
data class LocationHours(
    val dayOfWeek: Int = 1,
    val openMinutes: Int = 0,
    val closeMinutes: Int = 0,
)

/** Vertical story block on the location page: markdown text or a photo. */
@Serializable
data class LocationContentBlock(
    val type: String = LocationLogic.BLOCK_TEXT,
    val text: String = "",
    val imageUrl: String = "",
)

/** A VND pledge while the location is still in planning. [zaloNumber] is admin-only. */
@Serializable
data class LocationCommitment(
    val id: String,
    val locationId: String,
    val amountVnd: Long = 0,
    val message: String = "",
    val zaloNumber: String = "",
    val deviceTokenHash: String = "",
    val createdAt: Long = 0,
)

/** A message to the host once funding is closed or the place is operating. */
@Serializable
data class LocationContact(
    val id: String,
    val locationId: String,
    val message: String = "",
    val zaloNumber: String = "",
    val deviceTokenHash: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class LocationLike(
    val id: String,
    val locationId: String,
    val deviceTokenHash: String = "",
    val createdAt: Long = 0,
)

/** Public list/detail card. Includes [hostZalo] so people can reach the host. */
@Serializable
data class LocationCard(
    val id: String,
    val name: String = "",
    val type: String = "",
    val category: String = "",
    val status: String = LocationLogic.STATUS_PLANNING,
    val displayStatus: String = LocationLogic.DISPLAY_PLANNING,
    val address: String = "",
    val highlights: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val schedule: List<LocationHours> = emptyList(),
    val closesAt: String? = null,
    val opensAt: String? = null,
    val opensDayOfWeek: Int? = null,
    val likeCount: Int = 0,
    val liked: Boolean = false,
    val mapsUrl: String = "",
    val hostZalo: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class LocationsFeedResponse(
    val locations: List<LocationCard> = emptyList(),
    val categories: List<String> = emptyList(),
)

@Serializable
data class LocationDetailResponse(
    val location: LocationCard,
    val content: List<LocationContentBlock> = emptyList(),
    val canCommit: Boolean = false,
    val canContact: Boolean = false,
)

@Serializable
data class CommitLocationFundsRequest(
    val amountVnd: Long,
    val message: String,
    val zaloNumber: String,
)

@Serializable
data class ContactLocationRequest(
    val message: String,
    val zaloNumber: String,
)

@Serializable
data class LocationUpsertRequest(
    val id: String? = null,
    val name: String = "",
    val type: String = "",
    val category: String = "",
    val status: String = LocationLogic.STATUS_PLANNING,
    val address: String = "",
    val highlights: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val content: List<LocationContentBlock> = emptyList(),
    val schedule: List<LocationHours> = emptyList(),
    val hostZalo: String = "",
    val mapsUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class LocationPhotoUploadResponse(
    val url: String,
)

@Serializable
data class LocationCommitmentView(
    val id: String,
    val locationId: String,
    val locationName: String = "",
    val amountVnd: Long = 0,
    val message: String = "",
    val zaloNumber: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class LocationContactView(
    val id: String,
    val locationId: String,
    val locationName: String = "",
    val message: String = "",
    val zaloNumber: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class AdminLocationView(
    val id: String,
    val name: String = "",
    val type: String = "",
    val category: String = "",
    val status: String = LocationLogic.STATUS_PLANNING,
    val address: String = "",
    val highlights: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val content: List<LocationContentBlock> = emptyList(),
    val schedule: List<LocationHours> = emptyList(),
    val hostZalo: String = "",
    val mapsUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val likeCount: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val commitments: List<LocationCommitmentView> = emptyList(),
    val contacts: List<LocationContactView> = emptyList(),
)

@Serializable
data class AdminLocationsResponse(
    val locations: List<AdminLocationView> = emptyList(),
)

@Serializable
data class LocationApiError(
    val error: String,
)

/** Open/closed snapshot derived from a weekly schedule in UTC+7. */
data class LocationHoursSnapshot(
    val displayStatus: String,
    val closesAt: String? = null,
    val opensAt: String? = null,
    val opensDayOfWeek: Int? = null,
)
