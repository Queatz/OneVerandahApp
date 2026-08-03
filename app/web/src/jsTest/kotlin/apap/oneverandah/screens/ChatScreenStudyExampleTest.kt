package apap.oneverandah.screens

import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatScreenStudyExampleTest {
    @Test
    fun `highlights single case-insensitive match`() {
        assertEquals(
            listOf(0 until 5),
            studyExampleHighlightRanges("Hello there.", "hello"),
        )
    }

    @Test
    fun `highlights every non-overlapping occurrence`() {
        // "the cat is the best" → first "the" at 0, second at 11
        assertEquals(
            listOf(0 until 3, 11 until 14),
            studyExampleHighlightRanges("the cat is the best", "the"),
        )
    }

    @Test
    fun `highlights multi-word phrases`() {
        assertEquals(
            listOf(4 until 10),
            studyExampleHighlightRanges("Tôi đi học sớm.", "đi học"),
        )
    }

    @Test
    fun `returns empty when word is missing or blank`() {
        assertTrue(studyExampleHighlightRanges("Hello friend.", "bye").isEmpty())
        assertTrue(studyExampleHighlightRanges("Hello friend.", "   ").isEmpty())
        assertTrue(studyExampleHighlightRanges("", "Hello").isEmpty())
    }

    @Test
    fun `collects unique express practice sentences from token examples`() {
        val session = StudySessionResponse(
            text = "Xin chào bạn.",
            textLanguageTag = "vi",
            sentences = listOf(
                StudySentence(
                    text = "Xin chào bạn.",
                    tokens = listOf(
                        StudyToken(
                            text = "Xin chào",
                            status = StudyTokenStatus.READY,
                            exampleSentences = listOf(
                                StudyExampleSentence(text = "Xin chào.", translation = "Hello."),
                                StudyExampleSentence(text = "Chào bạn.", translation = "Hi friend."),
                                StudyExampleSentence(text = "Xin chào.", translation = "Hello."),
                            ),
                        ),
                        StudyToken(text = ".", status = StudyTokenStatus.PLAIN),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                StudyExampleSentence(text = "Xin chào.", translation = "Hello."),
                StudyExampleSentence(text = "Chào bạn.", translation = "Hi friend."),
            ),
            collectExpressPracticeSentences(session),
        )
        assertEquals(listOf("Xin chào"), collectExpressVocabulary(session))
    }

    @Test
    fun `collects story word steps in reading order and skips plain tokens`() {
        val session = StudySessionResponse(
            text = "Xin chào bạn.",
            textLanguageTag = "vi",
            sentences = listOf(
                StudySentence(
                    text = "Xin chào bạn.",
                    tokens = listOf(
                        StudyToken(text = "Xin chào", status = StudyTokenStatus.READY),
                        StudyToken(text = " ", status = StudyTokenStatus.PLAIN),
                        StudyToken(text = "bạn", status = StudyTokenStatus.PENDING),
                        StudyToken(text = ".", status = StudyTokenStatus.PLAIN),
                    ),
                ),
                StudySentence(
                    text = "Tạm biệt.",
                    tokens = listOf(
                        StudyToken(text = "Tạm biệt", status = StudyTokenStatus.READY),
                        StudyToken(text = ".", status = StudyTokenStatus.PLAIN),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                StoryWordStep(sentenceIndex = 0, tokenIndex = 0),
                StoryWordStep(sentenceIndex = 0, tokenIndex = 2),
                StoryWordStep(sentenceIndex = 1, tokenIndex = 0),
            ),
            collectStoryWordSteps(session),
        )
    }

    @Test
    fun `story word steps ignore blank non-plain tokens`() {
        val session = StudySessionResponse(
            text = "Hi",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(
                    text = "Hi",
                    tokens = listOf(
                        StudyToken(text = "   ", status = StudyTokenStatus.READY),
                        StudyToken(text = "Hi", status = StudyTokenStatus.READY),
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(StoryWordStep(sentenceIndex = 0, tokenIndex = 1)),
            collectStoryWordSteps(session),
        )
    }
}
