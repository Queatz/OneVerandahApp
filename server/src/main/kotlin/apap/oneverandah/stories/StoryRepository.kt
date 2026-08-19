package apap.oneverandah.stories

import java.util.concurrent.ConcurrentHashMap

interface StoryRepository {
    suspend fun findAll(): List<Story>
    suspend fun findById(id: String): Story?
    suspend fun save(story: Story)
    suspend fun delete(id: String): Boolean
}

/** Development / fallback store kept entirely in memory. */
class InMemoryStoryRepository : StoryRepository {
    private val items = ConcurrentHashMap<String, Story>()

    override suspend fun findAll(): List<Story> =
        items.values.sortedByDescending { it.updatedAt }

    override suspend fun findById(id: String): Story? = items[id]

    override suspend fun save(story: Story) {
        items[story.id] = story
    }

    override suspend fun delete(id: String): Boolean = items.remove(id) != null
}
