package apap.oneverandah.screens

import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyWorksheetTest {
    @Test
    fun `collects ready vocabulary and translated phrases once`() {
        val session = StudySessionResponse(
            text = "Hola, mundo.",
            textLanguageTag = "es",
            sentences = listOf(
                StudySentence(
                    text = "Hola, mundo.",
                    translation = "Hello, world.",
                    tokens = listOf(
                        StudyToken(
                            text = "Hola",
                            literalMeaning = "hello",
                            contextualMeaning = "greeting",
                            exampleSentences = listOf(
                                StudyExampleSentence("Hola, mundo.", "Hello, world."),
                                StudyExampleSentence("¿Dónde estás?", "Where are you?"),
                                StudyExampleSentence("Sin traducción", ""),
                            ),
                        ),
                        StudyToken(text = "mundo", literalMeaning = "world"),
                        StudyToken(
                            text = "incomplete",
                            status = StudyTokenStatus.PENDING,
                            literalMeaning = "not ready",
                        ),
                        StudyToken(
                            text = "example-only",
                            exampleSentences = listOf(StudyExampleSentence("Only an example", "Solo un ejemplo")),
                        ),
                    ),
                ),
            ),
            practiceSentences = listOf(
                StudyExampleSentence("¿Dónde estás?", "Where are you?"),
                StudyExampleSentence("No translation", ""),
            ),
        )

        assertEquals(
            StudyWorksheetContent(
                vocabulary = listOf(
                    StudyWorksheetVocabularyItem("hello / greeting", "Hola"),
                    StudyWorksheetVocabularyItem("world", "mundo"),
                ),
                phrases = listOf(
                    StudyWorksheetPhraseItem("Hello, world.", "Hola, mundo."),
                    StudyWorksheetPhraseItem("Where are you?", "¿Dónde estás?"),
                    StudyWorksheetPhraseItem("Solo un ejemplo", "Only an example"),
                ),
            ),
            studyWorksheetContent(session),
        )
    }

    @Test
    fun `escapes content and keeps answers after the answer key page break`() {
        val session = StudySessionResponse(
            text = "Say <hello> & 'thanks'.",
            textLanguageTag = "en",
            sentences = listOf(
                StudySentence(
                    text = "Say <hello> & 'thanks'.",
                    translation = "Diga \"sí\" & <por favor>",
                    tokens = listOf(StudyToken(text = "<hello>", literalMeaning = "salutation & 'greeting'")),
                ),
            ),
        )
        val html = assertNotNull(buildStudyWorksheetHtml(session, labels))
        val exercises = html.substringBefore("<section class=\"answer-key\">")
        val answerKey = html.substringAfter("<section class=\"answer-key\">")

        assertTrue(html.contains("Diga &quot;sí&quot; &amp; &lt;por favor&gt;"))
        assertTrue(html.contains("salutation &amp; &#39;greeting&#39;"))
        assertFalse(exercises.contains("&lt;hello&gt;"))
        assertTrue(answerKey.contains("&lt;hello&gt;"))
        assertTrue(html.contains("break-before: page"))
    }

    @Test
    fun `empty worksheet has no printable html`() {
        val session = StudySessionResponse(
            text = "Unfinished",
            textLanguageTag = "es",
            sentences = listOf(
                StudySentence(
                    text = "Unfinished phrase",
                    tokens = listOf(StudyToken(text = "unfinished", status = StudyTokenStatus.PENDING)),
                ),
            ),
        )

        assertTrue(studyWorksheetContent(session).isEmpty)
        assertNull(buildStudyWorksheetHtml(session, labels))
    }

    private val labels = StudyWorksheetLabels(
        title = "Worksheet",
        instructions = "Write the answer.",
        vocabulary = "Vocabulary",
        phrases = "Phrases",
        answerKey = "Answer key",
    )
}