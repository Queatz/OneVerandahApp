package apap.oneverandah.stories

import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StoryServiceTest {
    @Test
    fun createAndListHidesDraftsFromPublicShelf() = runBlocking {
        val service = service()
        val draft = service.create(
            StoryUpsertRequest(
                name = "Night Pool",
                category = "Mystery",
                pages = listOf(StoryPageUpsert(text = "Lan waits by the water.")),
            ),
        )
        assertFalse(draft.published)
        assertTrue(service.listPublic().isEmpty())
        assertEquals(1, service.listAdmin().size)
    }

    @Test
    fun publishTokenizesPagesAndPublicGetIncrementsOpens() = runBlocking {
        var tokenizeCalls = 0
        val service = service(
            tokenizer = StoryTokenizer { text, language ->
                tokenizeCalls += 1
                session(text, language)
            },
        )
        val created = service.create(
            StoryUpsertRequest(
                name = "Garden Walk",
                category = "Bedtime",
                pages = listOf(
                    StoryPageUpsert(text = "We walk slowly."),
                    StoryPageUpsert(text = ""),
                ),
            ),
        )

        val published = service.publish(created.id, "vi-VN")
        assertTrue(published.published)
        assertEquals("vi", published.languageTag)
        assertNotNull(published.releaseDate)
        assertEquals(1, tokenizeCalls)
        assertNotNull(published.pages.first().session)
        assertEquals("vi", published.pages.first().session?.textLanguageTag)
        assertNull(published.pages.last().session)

        val firstOpen = service.getPublic(created.id)
        val secondOpen = service.getPublic(created.id)
        assertEquals(1, firstOpen?.openCount)
        assertEquals(2, secondOpen?.openCount)
        assertTrue(service.listPublic().single().pages.isEmpty())
        assertFalse(published.publishing)
    }

    @Test
    fun beginPublishMarksBookWithoutTokenizing() = runBlocking {
        var tokenizeCalls = 0
        val service = service(
            tokenizer = StoryTokenizer { text, language ->
                tokenizeCalls += 1
                session(text, language)
            },
        )
        val created = service.create(
            StoryUpsertRequest(
                name = "Poolside",
                category = "Social",
                pages = listOf(StoryPageUpsert(text = "Lan waves from the deck.")),
            ),
        )

        val started = service.beginPublish(created.id)
        assertTrue(started.publishing)
        assertFalse(started.published)
        assertEquals(0, tokenizeCalls)
        assertTrue(service.listPublic().isEmpty())

        assertFailsWith<IllegalArgumentException> { service.beginPublish(created.id) }

        val failed = service.failPublish(created.id)
        assertFalse(failed.publishing)
        assertFalse(failed.published)
        assertEquals(0, tokenizeCalls)
    }

    @Test
    fun publicListOmitsPagesAndDraftsStayHidden() = runBlocking {
        val service = service()
        val created = service.create(
            StoryUpsertRequest(
                name = "Sky Lounge",
                category = "Social",
                pages = listOf(StoryPageUpsert(text = "Hello there.")),
            ),
        )
        service.publish(created.id, "en")
        val listed = service.listPublic().single()
        assertTrue(listed.pages.isEmpty())
        service.unpublish(created.id)
        assertTrue(service.listPublic().isEmpty())
        assertNotNull(service.getAdmin(created.id))
        assertNull(service.getPublic(created.id))
    }

    @Test
    fun cannotPublishNamelessOrEmptyBook() = runBlocking {
        val service = service()
        assertFailsWith<IllegalArgumentException> {
            service.create(StoryUpsertRequest(name = "  ", category = "Mystery"))
        }
        val empty = service.create(StoryUpsertRequest(name = "Blank", category = "Mystery"))
        assertFailsWith<IllegalArgumentException> { service.publish(empty.id, "en") }
        Unit
    }

    @Test
    fun detectLanguageUsesDetectorThenFallback() = runBlocking {
        val withAi = service(detector = StoryLanguageDetector { "ja" })
        val book = withAi.create(
            StoryUpsertRequest(
                name = "Lanterns",
                category = "Bedtime",
                pages = listOf(StoryPageUpsert(text = "Một buổi chiều")),
            ),
        )
        assertEquals("ja", withAi.detectLanguage(book.id))

        val fallback = service()
        val viet = fallback.create(
            StoryUpsertRequest(
                name = "Hiên",
                category = "Social",
                pages = listOf(StoryPageUpsert(text = "Một buổi chiều trên hiên nhà")),
            ),
        )
        assertEquals("vi", fallback.detectLanguage(viet.id))
    }

    @Test
    fun charactersMergeUploadsWithStudyPortraits() = runBlocking {
        val service = service(
            studyCharacters = listOf(
                StudyStoryCharacter(
                    id = "main",
                    name = "Lan",
                    role = "main",
                    description = "soft smile",
                    imageUrl = "https://example.test/lan.png",
                ),
                StudyStoryCharacter(
                    id = "ghost",
                    name = "No Photo",
                    role = "secondary",
                    description = "",
                    imageUrl = null,
                ),
            ),
        )
        service.addCharacter("Minh", "https://example.test/minh.png")
        val listed = service.listCharacters()
        assertEquals(setOf("Minh", "Lan"), listed.map { it.name }.toSet())
        assertTrue(listed.any { it.source == StoryCharacter.SOURCE_STUDY })
        val studyId = listed.first { it.source == StoryCharacter.SOURCE_STUDY }.id
        assertFalse(service.deleteCharacter(studyId))
    }

    @Test
    fun renameCharacterUpdatesPersistedName() = runBlocking {
        val service = service()
        val created = service.addCharacter("Minh", "https://example.test/minh.png")
        val renamed = service.renameCharacter(created.id, "  Minh Anh  ")
        assertEquals("Minh Anh", renamed.name)
        assertEquals(created.id, renamed.id)
        assertEquals("Minh Anh", service.listCharacters().single().name)
    }

    @Test
    fun renameCharacterRejectsBlankAndUnknown() = runBlocking {
        val service = service()
        val created = service.addCharacter("Minh", "https://example.test/minh.png")
        assertFailsWith<IllegalArgumentException> { service.renameCharacter(created.id, "  ") }
        assertFailsWith<IllegalStateException> { service.renameCharacter("missing", "Lan") }
        Unit
    }

    @Test
    fun renameStudyCharacterPersistsUploadedCopy() = runBlocking {
        val service = service(
            studyCharacters = listOf(
                StudyStoryCharacter(
                    id = "main",
                    name = "Lan",
                    role = "main",
                    description = "soft smile",
                    imageUrl = "https://example.test/lan.png",
                ),
            ),
        )
        val studyId = service.listCharacters().single().id
        val renamed = service.renameCharacter(studyId, "Lan Chi")
        assertEquals("Lan Chi", renamed.name)
        assertEquals(StoryCharacter.SOURCE_UPLOAD, renamed.source)
        assertEquals(listOf("Lan Chi"), service.listCharacters().map { it.name })
        assertTrue(service.deleteCharacter(renamed.id))
    }

    @Test
    fun harvestedStudyCharactersKeepUniqueIdsWhenRolesCollide() = runBlocking {
        val service = service(
            studyCharacters = listOf(
                StudyStoryCharacter(
                    id = "main",
                    name = "Lan",
                    role = "main",
                    description = "soft smile",
                    imageUrl = "https://example.test/lan.png",
                ),
                StudyStoryCharacter(
                    id = "main",
                    name = "Minh",
                    role = "main",
                    description = "wide grin",
                    imageUrl = "https://example.test/minh.png",
                ),
            ),
        )

        val listed = service.listCharacters()
        assertEquals(listOf("Lan", "Minh"), listed.map { it.name }.sorted())
        assertEquals(2, listed.map { it.id }.toSet().size)
        assertTrue(listed.all { it.id.startsWith("study-") })
    }

    @Test
    fun editingPageTextClearsStaleTokens() = runBlocking {
        val service = service()
        val created = service.create(
            StoryUpsertRequest(
                name = "Edit Me",
                category = "Mystery",
                pages = listOf(StoryPageUpsert(id = "p1", text = "First draft.")),
            ),
        )
        val published = service.publish(created.id, "en")
        assertNotNull(published.pages.single().session)
        val updated = service.update(
            created.id,
            StoryUpsertRequest(
                name = "Edit Me",
                category = "Mystery",
                pages = listOf(StoryPageUpsert(id = "p1", text = "Second draft.")),
            ),
        )
        assertNull(updated.pages.single().session)
        assertTrue(updated.published)
    }

    private fun service(
        tokenizer: StoryTokenizer = StoryTokenizer { text, language -> session(text, language) },
        detector: StoryLanguageDetector? = null,
        studyCharacters: List<StudyStoryCharacter> = emptyList(),
    ) = StoryService(
        stories = InMemoryStoryRepository(),
        characters = InMemoryStoryCharacterRepository(),
        tokenizer = tokenizer,
        languageDetector = detector,
        studyCharacterSource = StoryCharacterSource { studyCharacters },
        clock = { 1_700_000_000_000L },
        idGenerator = { "fixed" },
    )

    private fun session(text: String, language: String) = StudySessionResponse(
        text = text,
        textLanguageTag = language,
        sentences = listOf(
            StudySentence(
                text = text,
                tokens = text.split(" ").filter { it.isNotBlank() }.map { word ->
                    StudyToken(text = word, status = StudyTokenStatus.READY, literalMeaning = word)
                },
            ),
        ),
    )
}
