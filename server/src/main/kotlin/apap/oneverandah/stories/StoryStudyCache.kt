package apap.oneverandah.stories

import apap.oneverandah.api.StudySessionResponse
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

@Serializable
data class StoryPageStudy(
    val id: String,
    val storyId: String,
    val pageId: String,
    val languageTag: String,
    val session: StudySessionResponse,
    val updatedAt: Long = 0,
)

interface StoryStudyCache {
    suspend fun get(storyId: String, pageId: String, languageTag: String): StudySessionResponse?
    suspend fun put(storyId: String, pageId: String, languageTag: String, session: StudySessionResponse)
    suspend fun evictPage(storyId: String, pageId: String)
    suspend fun evictStory(storyId: String)
}

class InMemoryStoryStudyCache : StoryStudyCache {
    private val items = ConcurrentHashMap<String, StoryPageStudy>()

    override suspend fun get(storyId: String, pageId: String, languageTag: String): StudySessionResponse? =
        items[StoryLogic.studyCacheKey(storyId, pageId, languageTag)]?.session

    override suspend fun put(
        storyId: String,
        pageId: String,
        languageTag: String,
        session: StudySessionResponse,
    ) {
        val language = StoryLogic.normalizeLanguageTag(languageTag)
        val id = StoryLogic.studyCacheKey(storyId, pageId, language)
        items[id] = StoryPageStudy(
            id = id,
            storyId = storyId,
            pageId = pageId,
            languageTag = language,
            session = session,
        )
    }

    override suspend fun evictPage(storyId: String, pageId: String) {
        items.values
            .filter { it.storyId == storyId && it.pageId == pageId }
            .forEach { items.remove(it.id) }
    }

    override suspend fun evictStory(storyId: String) {
        items.values
            .filter { it.storyId == storyId }
            .forEach { items.remove(it.id) }
    }
}
