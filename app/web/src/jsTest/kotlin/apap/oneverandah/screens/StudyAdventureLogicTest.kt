package apap.oneverandah.screens

import apap.oneverandah.api.StudyAdventureProgress
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyExampleToken
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyAdventureLogicTest {
    @Test
    fun `levels include translatable ready words and deduplicated translated examples`() {
        val example = StudyExampleSentence(
            text = "Children are playing.",
            translation = "Children are playing.",
            tokens = listOf(
                StudyExampleToken("Children", contextualMeaning = "kids"),
                StudyExampleToken("are", translation = "are"),
                StudyExampleToken("playing", contextualMeaning = "playing a game"),
            ),
        )
        val session = StudySessionResponse(
            text = "In the park, children play.",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(
                    text = "In the park, children play.",
                    imageUrl = "https://example.test/park.jpg",
                    tokens = listOf(
                        StudyToken("In", literalMeaning = "in", contextualMeaning = "inside"),
                        StudyToken("the", status = StudyTokenStatus.PLAIN),
                        StudyToken("park", contextualMeaning = "garden", exampleSentences = listOf(example, example)),
                        StudyToken("later", status = StudyTokenStatus.PENDING, contextualMeaning = "after"),
                        StudyToken("play", status = StudyTokenStatus.READY),
                    ),
                ),
                StudySentence(
                    text = "Still loading.",
                    tokens = listOf(StudyToken("loading", status = StudyTokenStatus.PENDING)),
                ),
            ),
        )

        val levels = buildAdventureLevels(session)

        assertEquals(1, levels.size)
        assertEquals("https://example.test/park.jpg", levels.single().imageUrl)
        assertEquals(listOf("In", "park"), levels.single().sentence.words.map { it.targetText })
        assertEquals(listOf("inside", "garden"), levels.single().sentence.words.map { it.sourceText })
        assertEquals(listOf("In the park, children play.", "Children are playing."), levels.single().sentences.map { it.text })
        assertEquals(listOf("kids", "are", "playing a game"), levels.single().sentences.last().words.map { it.sourceText })
    }

    @Test
    fun `timer shortens by health and is bounded at three seconds`() {
        assertEquals(listOf(15_000, 13_000, 11_000, 9_000, 7_000, 5_000, 3_000), (0..6).map(::adventureTimerMs))
        assertEquals(3_000, adventureTimerMs(7))
        assertEquals(15_000, adventureTimerMs(-1))
    }

    @Test
    fun `correct wrong and duplicate word clicks update only the prompted word`() {
        val words = listOf(
            AdventureWord("play", "play"),
            AdventureWord("playing", "playing"),
            AdventureWord("play", "play"),
        )
        assertEquals(listOf(1, 0, 0), applyAdventureClick(listOf(0, 0, 0), words, 0, 0, 1))
        assertEquals(listOf(0, 0, 0), applyAdventureClick(listOf(1, 0, 0), words, 0, 1, 1))
        assertEquals(listOf(2, 0, 0), applyAdventureClick(listOf(1, 0, 0), words, 0, 2, 1))
        assertEquals(listOf(7, 0, 0), applyAdventureClick(listOf(7, 0, 0), words, 0, 0, 1))
        assertEquals(listOf(1, 0, 0), applyAdventureClick(listOf(0, 0, 0), words, 0, 2, 2))
    }

    @Test
    fun `timeouts lose one health and never go below zero`() {
        assertEquals(listOf(0, 2), applyAdventureTimeout(listOf(0, 3), 0))
        assertEquals(listOf(0, 2), applyAdventureTimeout(listOf(0, 3), 1))
        assertEquals(listOf(0, 3), applyAdventureTimeout(listOf(0, 3), 8))
    }

    @Test
    fun `prompt selection skips completed words and avoids repeating the previous prompt`() {
        assertEquals(1, pickAdventurePrompt(listOf(7, 0, 7), random = Random(4)))
        val next = pickAdventurePrompt(listOf(0, 0, 0), previous = 1, random = Random(2))
        assertTrue(next == 0 || next == 2)
        assertNull(pickAdventurePrompt(listOf(7, 7)))
    }

    @Test
    fun `unlocking follows completed levels and progress merge never regresses completion`() {
        assertEquals(setOf(0), adventureUnlockedLevels(null, 4))
        assertEquals(setOf(0, 1, 2), adventureUnlockedLevels(StudyAdventureProgress(completedLevels = 2), 4))
        assertEquals(setOf(0, 1, 2, 3), adventureUnlockedLevels(StudyAdventureProgress(completedLevels = 8), 4))

        val completed = StudyAdventureProgress(completedLevels = 2, levelIndex = 2, updatedAt = 100)
        val older = StudyAdventureProgress(completedLevels = 1, levelIndex = 1, updatedAt = 200)
        assertEquals(completed, mergeAdventureProgress(completed, older))
        assertEquals(StudyAdventureProgress(completedLevels = 2, levelIndex = 3, updatedAt = 300), mergeAdventureProgress(
            completed,
            StudyAdventureProgress(completedLevels = 2, levelIndex = 3, updatedAt = 300),
        ))
    }
}