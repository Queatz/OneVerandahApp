package apap.oneverandah.stories

import java.util.concurrent.ConcurrentHashMap

interface StoryCharacterRepository {
    suspend fun findAll(): List<StoryCharacter>
    suspend fun findById(id: String): StoryCharacter?
    suspend fun save(character: StoryCharacter)
    suspend fun delete(id: String): Boolean
}

class InMemoryStoryCharacterRepository : StoryCharacterRepository {
    private val items = ConcurrentHashMap<String, StoryCharacter>()

    override suspend fun findAll(): List<StoryCharacter> =
        items.values.sortedByDescending { it.createdAt }

    override suspend fun findById(id: String): StoryCharacter? = items[id]

    override suspend fun save(character: StoryCharacter) {
        items[character.id] = character
    }

    override suspend fun delete(id: String): Boolean = items.remove(id) != null
}
