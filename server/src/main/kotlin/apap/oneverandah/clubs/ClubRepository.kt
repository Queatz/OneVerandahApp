package apap.oneverandah.clubs

import java.util.concurrent.ConcurrentHashMap

interface ClubRepository {
    suspend fun findAll(): List<Club>
    suspend fun find(id: String): Club?
    suspend fun save(club: Club)
    suspend fun delete(id: String): Boolean

    suspend fun findCreateRequests(): List<ClubCreateRequest>
    suspend fun saveCreateRequest(request: ClubCreateRequest)
    suspend fun deleteCreateRequest(id: String): Boolean

    suspend fun findJoins(): List<ClubJoin>
    suspend fun findJoinsByClub(clubId: String): List<ClubJoin>
    suspend fun saveJoin(join: ClubJoin)
    suspend fun deleteJoin(id: String): Boolean
    suspend fun deleteJoinsFor(clubId: String)
}

/** Development / fallback store kept entirely in memory. */
class InMemoryClubRepository : ClubRepository {
    private val clubs = ConcurrentHashMap<String, Club>()
    private val createRequests = ConcurrentHashMap<String, ClubCreateRequest>()
    private val joins = ConcurrentHashMap<String, ClubJoin>()

    override suspend fun findAll(): List<Club> =
        clubs.values.sortedByDescending { it.createdAt }

    override suspend fun find(id: String): Club? = clubs[id]

    override suspend fun save(club: Club) {
        clubs[club.id] = club
    }

    override suspend fun delete(id: String): Boolean {
        val removed = clubs.remove(id) != null
        deleteJoinsFor(id)
        return removed
    }

    override suspend fun findCreateRequests(): List<ClubCreateRequest> =
        createRequests.values.sortedByDescending { it.createdAt }

    override suspend fun saveCreateRequest(request: ClubCreateRequest) {
        createRequests[request.id] = request
    }

    override suspend fun deleteCreateRequest(id: String): Boolean = createRequests.remove(id) != null

    override suspend fun findJoins(): List<ClubJoin> =
        joins.values.sortedByDescending { it.createdAt }

    override suspend fun findJoinsByClub(clubId: String): List<ClubJoin> =
        joins.values.filter { it.clubId == clubId }.sortedByDescending { it.createdAt }

    override suspend fun saveJoin(join: ClubJoin) {
        joins[join.id] = join
    }

    override suspend fun deleteJoin(id: String): Boolean = joins.remove(id) != null

    override suspend fun deleteJoinsFor(clubId: String) {
        joins.values.filter { it.clubId == clubId }.forEach { joins.remove(it.id) }
    }
}
