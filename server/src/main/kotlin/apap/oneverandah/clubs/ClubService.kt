package apap.oneverandah.clubs

import java.util.UUID

/**
 * Club persistence, create-club requests, and join requests.
 *
 * Photo upload lives on [ClubSystem] so this class stays easy to test with
 * an in-memory store.
 */
class ClubService(
    private val repository: ClubRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun feed(): ClubsFeedResponse {
        val clubs = repository.findAll()
        return ClubsFeedResponse(
            clubs = ClubLogic.publicCards(clubs),
            categories = ClubLogic.categories(clubs),
        )
    }

    suspend fun getPublic(id: String): ClubDetailResponse? {
        val club = repository.find(id) ?: return null
        return ClubDetailResponse(
            club = ClubLogic.toPublicCard(club),
            content = club.content,
            joinQuestions = club.joinQuestions,
        )
    }

    suspend fun upsert(request: ClubUpsertRequest): Club {
        val name = ClubLogic.sanitizeName(request.name)
        val category = ClubLogic.sanitizeCategory(request.category)
        require(name.isNotEmpty()) { "A name is required" }
        require(category.isNotEmpty()) { "A category is required" }
        val now = clock()
        val existingId = request.id?.trim().orEmpty()
        val existing = existingId.takeIf { it.isNotEmpty() }?.let { repository.find(it) }
        val id = existing?.id
            ?: existingId.takeIf { it.isNotEmpty() }
            ?: "club-${idGenerator()}"
        val club = Club(
            id = id,
            name = name,
            tagline = ClubLogic.sanitizeTagline(request.tagline),
            description = ClubLogic.sanitizeDescription(request.description),
            category = category,
            activities = ClubLogic.sanitizeActivities(request.activities),
            photoUrls = ClubLogic.sanitizePhotoUrls(request.photoUrls),
            content = ClubLogic.sanitizeContent(request.content),
            joinQuestions = ClubLogic.sanitizeJoinQuestions(request.joinQuestions),
            hostName = ClubLogic.sanitizeHostName(request.hostName),
            hostZalo = ClubLogic.sanitizeZalo(request.hostZalo),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        repository.save(club)
        return club
    }

    suspend fun requestCreate(
        name: String,
        tagline: String,
        description: String,
        hostName: String,
        hostZalo: String,
        deviceTokenHash: String,
    ): ClubCreateRequest {
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val cleanedName = ClubLogic.sanitizeName(name)
        val cleanedHost = ClubLogic.sanitizeHostName(hostName)
        val cleanedZalo = ClubLogic.sanitizeZalo(hostZalo)
        require(cleanedName.isNotEmpty()) { "A name is required" }
        require(cleanedHost.isNotEmpty()) { "A host name is required" }
        require(cleanedZalo.isNotEmpty()) { "A Zalo number is required" }
        val now = clock()
        val request = ClubCreateRequest(
            id = "ccreate-${idGenerator()}",
            name = cleanedName,
            tagline = ClubLogic.sanitizeTagline(tagline),
            description = ClubLogic.sanitizeDescription(description),
            hostName = cleanedHost,
            hostZalo = cleanedZalo,
            deviceTokenHash = deviceTokenHash,
            createdAt = now,
        )
        repository.saveCreateRequest(request)
        return request
    }

    suspend fun join(
        clubId: String,
        name: String,
        zaloNumber: String,
        answers: List<String>,
        message: String,
        deviceTokenHash: String,
    ): ClubJoin {
        require(deviceTokenHash.isNotBlank()) { "A device token is required" }
        val club = repository.find(clubId) ?: error("Club '$clubId' not found")
        val cleanedName = ClubLogic.sanitizeHostName(name)
        val cleanedZalo = ClubLogic.sanitizeZalo(zaloNumber)
        require(cleanedName.isNotEmpty()) { "A name is required" }
        require(cleanedZalo.isNotEmpty()) { "A Zalo number is required" }
        val now = clock()
        val join = ClubJoin(
            id = "cjoin-${idGenerator()}",
            clubId = clubId,
            name = cleanedName,
            zaloNumber = cleanedZalo,
            answers = ClubLogic.sanitizeAnswers(club.joinQuestions, answers),
            message = ClubLogic.sanitizeMessage(message),
            deviceTokenHash = deviceTokenHash,
            createdAt = now,
        )
        repository.saveJoin(join)
        return join
    }

    suspend fun listAdmin(): AdminClubsResponse {
        val clubs = repository.findAll()
            .sortedWith(compareByDescending<Club> { it.updatedAt }.thenBy { it.id })
        val clubsById = clubs.associateBy { it.id }
        val joins = repository.findJoins()
        return AdminClubsResponse(
            clubs = clubs.map { club ->
                ClubLogic.toAdminView(club, repository.findJoinsByClub(club.id))
            },
            createRequests = repository.findCreateRequests()
                .sortedWith(compareByDescending<ClubCreateRequest> { it.createdAt }.thenBy { it.id })
                .map(ClubLogic::toCreateRequestView),
            joins = joins
                .sortedWith(compareByDescending<ClubJoin> { it.createdAt }.thenBy { it.id })
                .map { join ->
                    val club = clubsById[join.clubId]
                    ClubLogic.toJoinView(join, club?.name.orEmpty(), club?.hostZalo.orEmpty())
                },
        )
    }

    suspend fun delete(id: String): Boolean {
        repository.deleteJoinsFor(id)
        return repository.delete(id)
    }

    suspend fun deleteCreateRequest(id: String): Boolean = repository.deleteCreateRequest(id)

    suspend fun deleteJoin(id: String): Boolean = repository.deleteJoin(id)
}
