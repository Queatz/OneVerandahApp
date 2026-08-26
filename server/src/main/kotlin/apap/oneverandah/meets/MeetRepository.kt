package apap.oneverandah.meets

import java.util.concurrent.ConcurrentHashMap

interface MeetRepository {
    suspend fun findAllMeets(): List<Meet>
    suspend fun findMeet(id: String): Meet?
    suspend fun saveMeet(meet: Meet)
    suspend fun deleteMeet(id: String): Boolean

    suspend fun findAllPitches(): List<MeetPitch>
    suspend fun findPitch(id: String): MeetPitch?
    suspend fun findPitchesByMeet(meetId: String): List<MeetPitch>
    suspend fun savePitch(pitch: MeetPitch)
    suspend fun deletePitch(id: String): Boolean
    suspend fun deletePitchesForMeet(meetId: String)
}

/** Development / fallback store kept entirely in memory. */
class InMemoryMeetRepository : MeetRepository {
    private val meets = ConcurrentHashMap<String, Meet>()
    private val pitches = ConcurrentHashMap<String, MeetPitch>()

    override suspend fun findAllMeets(): List<Meet> =
        meets.values.sortedByDescending { it.createdAt }

    override suspend fun findMeet(id: String): Meet? = meets[id]

    override suspend fun saveMeet(meet: Meet) {
        meets[meet.id] = meet
    }

    override suspend fun deleteMeet(id: String): Boolean {
        val removed = meets.remove(id) != null
        pitches.values.filter { it.meetId == id }.forEach { pitches.remove(it.id) }
        return removed
    }

    override suspend fun findAllPitches(): List<MeetPitch> =
        pitches.values.sortedByDescending { it.createdAt }

    override suspend fun findPitch(id: String): MeetPitch? = pitches[id]

    override suspend fun findPitchesByMeet(meetId: String): List<MeetPitch> =
        pitches.values.filter { it.meetId == meetId }.sortedByDescending { it.createdAt }

    override suspend fun savePitch(pitch: MeetPitch) {
        pitches[pitch.id] = pitch
    }

    override suspend fun deletePitch(id: String): Boolean = pitches.remove(id) != null

    override suspend fun deletePitchesForMeet(meetId: String) {
        pitches.values.filter { it.meetId == meetId }.forEach { pitches.remove(it.id) }
    }
}
