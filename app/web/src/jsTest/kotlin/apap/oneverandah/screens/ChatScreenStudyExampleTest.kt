package apap.oneverandah.screens

import apap.oneverandah.api.ChatMessage
import apap.oneverandah.api.ChatSender
import apap.oneverandah.api.LanguageStudyMiniApp
import apap.oneverandah.api.LanguageStudyToken
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

    @Test
    fun `story meaning narration keeps literal then context and trims`() {
        assertEquals(
            listOf("hello", "greeting in this sentence"),
            collectStoryMeaningNarrationTexts(
                literalMeaning = "  hello  ",
                contextualMeaning = " greeting in this sentence ",
            ),
        )
    }

    @Test
    fun `story meaning narration skips blanks and duplicate glosses`() {
        assertEquals(
            listOf("hello"),
            collectStoryMeaningNarrationTexts(
                literalMeaning = "Hello",
                contextualMeaning = "hello",
            ),
        )
        assertEquals(
            listOf("only context"),
            collectStoryMeaningNarrationTexts(
                literalMeaning = "   ",
                contextualMeaning = "only context",
            ),
        )
        assertTrue(
            collectStoryMeaningNarrationTexts(
                literalMeaning = "",
                contextualMeaning = "  ",
            ).isEmpty(),
        )
    }

    @Test
    fun `study text prefers language-study widget sentence`() {
        val message = studyChatMessage(
            text = "Here's how to greet someone in Vietnamese.\n\nXin chào bạn.",
            miniApp = LanguageStudyMiniApp(
                sentence = "Xin chào bạn.",
                targetLanguageTag = "vi",
                tokens = listOf(
                    LanguageStudyToken("Xin chào", "hello", "a greeting"),
                    LanguageStudyToken("bạn", "you", "the listener"),
                ),
            ),
        )
        assertEquals(
            "Xin chào bạn.",
            studyTextFromChatMessage(message, learnerLanguageTag = "en"),
        )
    }

    @Test
    fun `study text uses widget tokens when sentence is blank`() {
        val message = studyChatMessage(
            text = "Let's practise this Vietnamese sentence.",
            miniApp = LanguageStudyMiniApp(
                sentence = "  ",
                targetLanguageTag = "vi",
                tokens = listOf(
                    LanguageStudyToken("Tôi", "I", "the speaker"),
                    LanguageStudyToken(" đang học.", "am studying", "now"),
                ),
            ),
        )
        assertEquals(
            "Tôi đang học.",
            studyTextFromChatMessage(message, learnerLanguageTag = "en"),
        )
    }

    @Test
    fun `study text extracts target-language lines when widget is missing`() {
        val message = studyChatMessage(
            text = """
                Here's a natural way to talk about stickiness in Vietnamese:

                Thời gian dính như mật ong.

                You can also close with:

                Tạm biệt.
            """.trimIndent(),
        )
        assertEquals(
            "Thời gian dính như mật ong.\nTạm biệt.",
            studyTextFromChatMessage(message, learnerLanguageTag = "en"),
        )
    }

    @Test
    fun `study text extracts quoted target language from an explanation`() {
        val message = studyChatMessage(
            text = "A polite greeting is \"你好世界\" when you meet someone new.",
        )
        assertEquals(
            "你好世界",
            studyTextFromChatMessage(message, learnerLanguageTag = "en"),
        )
    }

    @Test
    fun `study text keeps monolingual material that is already the target language`() {
        val message = studyChatMessage(text = "  Xin chào bạn. Tôi tên là An.  ")
        assertEquals(
            "Xin chào bạn. Tôi tên là An.",
            studyTextFromChatMessage(message, learnerLanguageTag = "en"),
        )
    }

    @Test
    fun `study text keeps learner-language-only material so pasted study still works`() {
        val message = studyChatMessage(text = "Practice this English paragraph about the park.")
        assertEquals(
            "Practice this English paragraph about the park.",
            studyTextFromChatMessage(message, learnerLanguageTag = "en"),
        )
    }
}

private fun studyChatMessage(
    text: String,
    miniApp: LanguageStudyMiniApp? = null,
): ChatMessage = ChatMessage(
    id = "msg-1",
    sender = ChatSender.AI,
    text = text,
    createdAt = 1L,
    miniApp = miniApp,
)
