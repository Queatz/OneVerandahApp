package apap.oneverandah.stories

import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.chat.StudyService
import apap.oneverandah.model.Language
import java.util.UUID

fun interface StoryTokenizer {
    suspend fun tokenize(text: String, languageTag: String): StudySessionResponse
}

fun interface StoryLanguageDetector {
    suspend fun detect(text: String): String
}

fun interface StoryCharacterSource {
    suspend fun studyCharacters(): List<StudyStoryCharacter>
}

/**
 * Mini Story persistence + publish/tokenize rules.
 *
 * Image generation lives on [StorySystem] so this class stays easy to test
 * with in-memory stores and a fake tokenizer.
 */
class StoryService(
    private val stories: StoryRepository,
    private val characters: StoryCharacterRepository,
    private val tokenizer: StoryTokenizer? = null,
    private val languageDetector: StoryLanguageDetector? = null,
    private val studyCharacterSource: StoryCharacterSource? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun listPublic(): List<Story> =
        StoryLogic.publishedNewestFirst(stories.findAll()).map(StoryLogic::toPublicSummary)

    suspend fun listAdmin(): List<Story> = StoryLogic.adminNewestFirst(stories.findAll())

    suspend fun getPublic(id: String): Story? {
        val existing = stories.findById(id)?.takeIf { it.published } ?: return null
        val opened = existing.copy(openCount = existing.openCount + 1)
        stories.save(opened)
        return opened
    }

    suspend fun getAdmin(id: String): Story? = stories.findById(id)

    suspend fun create(request: StoryUpsertRequest): Story {
        val name = request.name.trim()
        require(name.isNotEmpty()) { "A book name is required" }
        val now = clock()
        val story = Story(
            id = request.id?.trim()?.takeIf { it.isNotEmpty() } ?: "story-${idGenerator()}",
            name = name,
            category = request.category.trim(),
            coverImageUrl = request.coverImageUrl?.trim()?.takeIf { it.isNotEmpty() },
            pages = request.pages.map { it.toPage() },
            createdAt = now,
            updatedAt = now,
        )
        stories.save(story)
        return story
    }

    suspend fun update(id: String, request: StoryUpsertRequest): Story {
        val existing = stories.findById(id) ?: error("Story '$id' not found")
        val name = request.name.trim()
        require(name.isNotEmpty()) { "A book name is required" }
        val updated = existing.copy(
            name = name,
            category = request.category.trim(),
            coverImageUrl = request.coverImageUrl?.trim()?.takeIf { it.isNotEmpty() },
            pages = request.pages.map { draft ->
                val previous = draft.id?.let { pageId -> existing.pages.firstOrNull { it.id == pageId } }
                draft.toPage(previous)
            },
            updatedAt = clock(),
        )
        stories.save(updated)
        return updated
    }

    suspend fun delete(id: String): Boolean = stories.delete(id)

    suspend fun detectLanguage(id: String): String {
        val story = stories.findById(id) ?: error("Story '$id' not found")
        return detectLanguage(story)
    }

    suspend fun detectLanguage(story: Story): String {
        val text = story.pages.joinToString("\n\n") { it.text }.trim()
        if (text.isEmpty()) return Language.ENGLISH.tag
        val detected = runCatching {
            languageDetector?.detect(text.take(DETECT_SAMPLE_CHARS))
        }.getOrNull()?.trim().orEmpty()
        return if (detected.isNotEmpty()) {
            StoryLogic.normalizeLanguageTag(detected)
        } else {
            StoryLogic.detectLanguageFallback(text)
        }
    }

    suspend fun publish(id: String, languageTag: String): Story {
        val existing = stories.findById(id) ?: error("Story '$id' not found")
        val error = StoryLogic.publishError(existing)
        if (error != null) throw IllegalArgumentException(error)
        val language = StoryLogic.normalizeLanguageTag(languageTag)
        val tokenize = tokenizer ?: error("Story tokenizer is unavailable")
        val pages = existing.pages.map { page ->
            val text = page.text.trim()
            if (text.isEmpty()) {
                page.copy(text = "", session = null)
            } else {
                page.copy(text = text, session = tokenize.tokenize(text, language))
            }
        }
        val now = clock()
        val published = existing.copy(
            published = true,
            languageTag = language,
            releaseDate = existing.releaseDate ?: now,
            pages = pages,
            updatedAt = now,
        )
        stories.save(published)
        return published
    }

    suspend fun unpublish(id: String): Story {
        val existing = stories.findById(id) ?: error("Story '$id' not found")
        val updated = existing.copy(published = false, updatedAt = clock())
        stories.save(updated)
        return updated
    }

    suspend fun attachImage(storyId: String, imageUrl: String, pageId: String?, asCover: Boolean): Story {
        val existing = stories.findById(storyId) ?: error("Story '$storyId' not found")
        val updated = when {
            asCover || pageId.isNullOrBlank() -> existing.copy(
                coverImageUrl = imageUrl,
                updatedAt = clock(),
            )
            else -> existing.copy(
                pages = existing.pages.map { page ->
                    if (page.id == pageId) page.copy(photoUrl = imageUrl) else page
                },
                updatedAt = clock(),
            )
        }
        stories.save(updated)
        return updated
    }

    suspend fun listCharacters(): List<StoryCharacter> {
        val uploaded = characters.findAll()
        val harvested = runCatching { studyCharacterSource?.studyCharacters().orEmpty() }
            .getOrDefault(emptyList())
            .mapNotNull { character ->
                val url = character.imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val name = character.name.trim().ifBlank { "Character" }
                StoryCharacter(
                    id = StoryLogic.studyCharacterId(character.id, url),
                    name = name,
                    imageUrl = url,
                    source = StoryCharacter.SOURCE_STUDY,
                    createdAt = 0,
                )
            }
            .distinctBy { it.imageUrl }
        val uploadedUrls = uploaded.map { it.imageUrl }.toSet()
        return (uploaded + harvested.filter { it.imageUrl !in uploadedUrls })
            .sortedByDescending { it.createdAt }
    }

    suspend fun addCharacter(name: String, imageUrl: String): StoryCharacter {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A character name is required" }
        val url = imageUrl.trim()
        require(url.isNotEmpty()) { "A character photo is required" }
        val character = StoryCharacter(
            id = "char-${idGenerator()}",
            name = trimmed,
            imageUrl = url,
            source = StoryCharacter.SOURCE_UPLOAD,
            createdAt = clock(),
        )
        characters.save(character)
        return character
    }

    suspend fun deleteCharacter(id: String): Boolean {
        if (id.startsWith("study-")) return false
        return characters.delete(id)
    }

    suspend fun findCharacter(id: String): StoryCharacter? {
        characters.findById(id)?.let { return it }
        return listCharacters().firstOrNull { it.id == id }
    }

    private fun StoryPageUpsert.toPage(previous: StoryPage? = null): StoryPage {
        val text = text.trim()
        val photo = photoUrl?.trim()?.takeIf { it.isNotEmpty() }
        val keepSession = previous != null &&
            previous.text == text &&
            previous.photoUrl == photo
        return StoryPage(
            id = id?.trim()?.takeIf { it.isNotEmpty() } ?: previous?.id ?: "page-${idGenerator()}",
            photoUrl = photo,
            text = text,
            session = if (keepSession) previous.session else null,
        )
    }

    companion object {
        private const val DETECT_SAMPLE_CHARS = 2_500

        fun tokenizerFrom(study: StudyService): StoryTokenizer = StoryTokenizer { text, languageTag ->
            val language = StoryLogic.normalizeLanguageTag(languageTag)
            val skeleton = study.skeleton(StudySessionRequest(text = text, languageTag = language))
            val sentences = skeleton.sentences.map { sentence ->
                val dissected = study.dissectSentence(
                    StudyDissectSentenceRequest(
                        text = text,
                        sentenceText = sentence.text,
                        tokens = sentence.tokens.map { it.text },
                        textLanguageTag = language,
                        languageTag = language,
                    ),
                )
                StudySentence(text = dissected.sentenceText, tokens = dissected.tokens)
            }
            skeleton.copy(text = text, textLanguageTag = language, sentences = sentences)
        }

        fun detectorFrom(study: StudyService): StoryLanguageDetector = StoryLanguageDetector { text ->
            val skeleton = study.skeleton(StudySessionRequest(text = text, languageTag = Language.ENGLISH.tag))
            StoryLogic.normalizeLanguageTag(skeleton.textLanguageTag)
        }
    }
}
