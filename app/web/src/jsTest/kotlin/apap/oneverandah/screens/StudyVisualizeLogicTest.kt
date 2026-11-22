package apap.oneverandah.screens

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyVisualizeLogicTest {
    private val words = (1..10).map { StudyVisualizeWord("word$it") }

    @Test
    fun `deck contains each unique target word three times in shuffled order`() {
        val deck = buildStudyVisualizeDeck(words + StudyVisualizeWord(" word1 "), random = Random(21))

        assertEquals(words.size * STUDY_VISUALIZE_REPETITIONS, deck.size)
        words.forEach { word -> assertEquals(3, deck.count { it.text == word.text }) }
        assertFalse(deck.map { it.text } == words.flatMap { word -> List(3) { word.text } })
    }

    @Test
    fun `choice set is unique includes the correct word and never exceeds eight`() {
        val correct = words.last()
        val choices = chooseStudyVisualizeWords(words + words.first(), correct, random = Random(8))

        assertEquals(STUDY_VISUALIZE_MAX_CHOICES, choices.size)
        assertEquals(choices.size, choices.map { it.text.lowercase() }.toSet().size)
        assertTrue(choices.any { it.text == correct.text })
        assertEquals(2, chooseStudyVisualizeWords(words.take(2), words.first(), random = Random(2)).size)
    }

    @Test
    fun `wrong answer keeps the word and rebuilds the choices`() {
        val correct = words.first()
        val initialChoices = chooseStudyVisualizeWords(words, correct, random = Random(1))
        val choosing = setupStudyVisualizeChoices(
            StudyVisualizeProgress(phase = StudyVisualizePhase.SETUP),
            initialChoices.mapIndexed { index, word -> StudyVisualizeChoice(word, index.toDouble(), index.toDouble()) },
        )

        val wrong = answerStudyVisualize(choosing, selectedWord = words[1].text, correctWord = correct.text)
        val reshuffledChoices = chooseStudyVisualizeWords(words, correct, random = Random(2))

        assertEquals(StudyVisualizePhase.SETUP, wrong.phase)
        assertEquals(0, wrong.wordIndex)
        assertEquals(1, wrong.setupNonce)
        assertEquals(initialChoices.map { it.text }.toSet(), wrong.choices.map { it.word.text }.toSet())
        assertFalse(initialChoices.map { it.text } == reshuffledChoices.map { it.text })
    }

    @Test
    fun `correct answer stays visible until advanced and final answer completes the deck`() {
        val choices = listOf(
            StudyVisualizeChoice(words[0], 20.0, 30.0),
            StudyVisualizeChoice(words[1], 70.0, 60.0),
        )
        val choosing = StudyVisualizeProgress(
            phase = StudyVisualizePhase.CHOOSING,
            wordIndex = 0,
            choices = choices,
        )
        val holding = answerStudyVisualize(choosing, selectedWord = " Word1 ", correctWord = "word1")

        assertEquals(StudyVisualizePhase.CORRECT, holding.phase)
        assertEquals(0, holding.wordIndex)
        assertEquals(choices, holding.choices)

        val next = advanceStudyVisualize(holding, deckSize = 2)
        val finalHolding = answerStudyVisualize(
            next.copy(phase = StudyVisualizePhase.CHOOSING, choices = choices),
            selectedWord = "word2",
            correctWord = "word2",
        )
        val final = advanceStudyVisualize(finalHolding, deckSize = 2)

        assertEquals(StudyVisualizePhase.IMAGE, next.phase)
        assertEquals(1, next.wordIndex)
        assertTrue(next.choices.isEmpty())
        assertEquals(StudyVisualizePhase.CORRECT, finalHolding.phase)
        assertEquals(StudyVisualizePhase.COMPLETE, final.phase)
        assertEquals(2, final.wordIndex)
    }

    @Test
    fun `restart rebuilds a shuffled deck and begins from ready state`() {
        val firstDeck = buildStudyVisualizeDeck(words, random = Random(3))
        val restartedDeck = buildStudyVisualizeDeck(words, random = Random(4))

        assertEquals(StudyVisualizePhase.READY, StudyVisualizeProgress().phase)
        assertEquals(words.size * STUDY_VISUALIZE_REPETITIONS, restartedDeck.size)
        assertFalse(firstDeck.map { it.text } == restartedDeck.map { it.text })
    }
}