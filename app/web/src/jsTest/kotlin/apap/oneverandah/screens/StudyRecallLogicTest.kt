package apap.oneverandah.screens

import apap.oneverandah.api.StudyExampleSentence
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyRecallLogicTest {
    @Test
    fun `words appear three times shuffled before the phrase section`() {
        val vocabulary = listOf(
            StudyVocabularyRow(0, 0, "hello", "greeting", "greeting"),
            StudyVocabularyRow(0, 1, "goodbye", "farewell", "farewell"),
            StudyVocabularyRow(0, 2, "thanks", "gratitude", "gratitude"),
        )
        val phrases = listOf(
            StudyExampleSentence(text = "See you soon.", translation = "See you soon."),
            StudyExampleSentence(text = "Have a nice day.", translation = "Have a nice day."),
        )

        val deck = buildStudyRecallDeck(vocabulary, phrases, random = Random(14))
        val wordCards = deck.take(vocabulary.size * STUDY_RECALL_WORD_REPETITIONS)
        val phraseCards = deck.drop(wordCards.size)

        assertTrue(wordCards.all { it.kind == StudyRecallCardKind.WORD })
        assertEquals(3, wordCards.count { it.answer == "hello" })
        assertEquals(3, wordCards.count { it.answer == "goodbye" })
        assertEquals(3, wordCards.count { it.answer == "thanks" })
        assertEquals(phrases.map { it.text }.toSet(), phraseCards.map { it.answer }.toSet())
        assertTrue(phraseCards.all { it.kind == StudyRecallCardKind.PHRASE })
        assertFalse(wordCards.map { it.answer } == listOf("hello", "hello", "hello", "goodbye", "goodbye", "goodbye", "thanks", "thanks", "thanks"))
    }

    @Test
    fun `phrase cards are available when the vocabulary list is empty`() {
        val deck = buildStudyRecallDeck(
            vocabulary = emptyList(),
            phrases = listOf(StudyExampleSentence(text = "Good morning.", translation = "Good morning.")),
            random = Random(1),
        )

        assertEquals(1, deck.size)
        assertEquals(StudyRecallCardKind.PHRASE, deck.single().kind)
    }

    @Test
    fun `word and phrase timers use five and twenty seconds`() {
        assertEquals(5_000, studyRecallTimeLimitMs(StudyRecallCardKind.WORD))
        assertEquals(20_000, studyRecallTimeLimitMs(StudyRecallCardKind.PHRASE))
    }

    @Test
    fun `timeout stays on the revealed answer until next advances`() {
        val deckSize = 2
        val prompted = startStudyRecall(StudyRecallProgress())
        val timedOut = timeoutStudyRecall(prompted)

        assertEquals(StudyRecallPhase.REVEAL, timedOut.phase)
        assertTrue(timedOut.timedOut)
        assertEquals(0, timedOut.cardIndex)
        assertEquals(0, timedOut.correct)
        assertEquals(1, timedOut.answered)
        assertEquals(
            StudyRecallProgress(StudyRecallPhase.PROMPT, cardIndex = 1, answered = 1),
            advanceTimedOutStudyRecall(timedOut, deckSize),
        )

        val manuallyWrong = gradeStudyRecall(revealStudyRecallAnswer(prompted), deckSize, correct = false)
        assertEquals(manuallyWrong, advanceTimedOutStudyRecall(timedOut, deckSize))
    }

    @Test
    fun `correct answers and failures produce the final score`() {
        val deckSize = 2
        var progress = revealStudyRecallAnswer(startStudyRecall(StudyRecallProgress()))
        progress = gradeStudyRecall(progress, deckSize, correct = true)
        progress = timeoutStudyRecall(progress)
        progress = advanceTimedOutStudyRecall(progress, deckSize)

        assertEquals(StudyRecallPhase.COMPLETE, progress.phase)
        assertEquals(deckSize, progress.answered)
        assertEquals(1, progress.correct)
    }

    @Test
    fun `invalid transitions do not change recall progress`() {
        val ready = StudyRecallProgress()
        assertEquals(ready, revealStudyRecallAnswer(ready))
        assertEquals(ready, timeoutStudyRecall(ready))
        assertEquals(ready, gradeStudyRecall(ready, 1, correct = true))
        assertEquals(ready, advanceTimedOutStudyRecall(ready, 1))
    }
}