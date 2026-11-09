package apap.oneverandah.screens

import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyStoryTabTest {
    @Test
    fun `nextStoryContinuePosition advances within words then transitions to sentences`() {
        val (phase1, step1) = nextStoryContinuePosition(
            phase = StudyStoryPhase.WORDS,
            stepIndex = 0,
            wordStepCount = 3,
            sentenceCount = 2,
        )
        assertEquals(StudyStoryPhase.WORDS, phase1)
        assertEquals(1, step1)

        val (phase2, step2) = nextStoryContinuePosition(
            phase = StudyStoryPhase.WORDS,
            stepIndex = 2,
            wordStepCount = 3,
            sentenceCount = 2,
        )
        assertEquals(StudyStoryPhase.SENTENCES, phase2)
        assertEquals(0, step2)
    }

    @Test
    fun `nextStoryContinuePosition loops or settles at final sentence`() {
        val (phase1, step1) = nextStoryContinuePosition(
            phase = StudyStoryPhase.SENTENCES,
            stepIndex = 0,
            wordStepCount = 3,
            sentenceCount = 2,
        )
        assertEquals(StudyStoryPhase.SENTENCES, phase1)
        assertEquals(1, step1)

        val (phase2, step2) = nextStoryContinuePosition(
            phase = StudyStoryPhase.SENTENCES,
            stepIndex = 1,
            wordStepCount = 3,
            sentenceCount = 2,
        )
        assertEquals(StudyStoryPhase.SENTENCES, phase2)
        assertEquals(1, step2)
    }

    @Test
    fun `collectStoryMeaningNarrationTexts deduplicates and prioritizes literal meaning`() {
        assertEquals(
            listOf("to leave", "to exit"),
            collectStoryMeaningNarrationTexts("to leave", "to exit"),
        )
        assertEquals(
            listOf("hello"),
            collectStoryMeaningNarrationTexts("hello", "hello"),
        )
        assertEquals(
            listOf("greeting"),
            collectStoryMeaningNarrationTexts("", "greeting"),
        )
        assertEquals(
            emptyList(),
            collectStoryMeaningNarrationTexts("", ""),
        )
    }

    @Test
    fun `collectStoryWordSteps produces steps for all dissectable tokens and examples`() {
        val session = StudySessionResponse(
            text = "Hello world",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(
                    text = "Hello world",
                    tokens = listOf(
                        StudyToken(
                            text = "Hello",
                            literalMeaning = "greeting",
                            exampleSentences = listOf(
                                StudyExampleSentence("Hello there", "General greeting"),
                            ),
                        ),
                        StudyToken(text = " ", status = StudyTokenStatus.PLAIN),
                        StudyToken(text = "world", literalMeaning = "planet"),
                    ),
                ),
            ),
        )
        val steps = collectStoryWordSteps(session)
        assertEquals(3, steps.size)
        assertEquals(0, steps[0].sentenceIndex)
        assertEquals(0, steps[0].tokenIndex)
        assertEquals(null, steps[0].exampleIndex)

        assertEquals(0, steps[1].sentenceIndex)
        assertEquals(0, steps[1].tokenIndex)
        assertEquals(0, steps[1].exampleIndex)

        assertEquals(0, steps[2].sentenceIndex)
        assertEquals(2, steps[2].tokenIndex)
        assertEquals(null, steps[2].exampleIndex)
    }
}
