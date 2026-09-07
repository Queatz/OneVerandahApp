package apap.oneverandah.images

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptFactoryTest {
    @Test
    fun withArtDirection_appendsStyleAndNoTextOnce() {
        val first = PromptFactory.withArtDirection("A quiet courtyard at dusk")
        assertContains(first, PromptFactory.ART_STYLE)
        assertContains(first, PromptFactory.NO_TEXT)
        assertEquals(first, PromptFactory.withArtDirection(first))
    }

    @Test
    fun forWord_leadsWithMeaningAndForbidsRenderingTheWord() {
        val prompt = PromptFactory.forWord("chào", "a friendly greeting")
        assertContains(prompt, "a friendly greeting")
        assertContains(prompt, "Do not render these characters")
        assertContains(prompt, "chào")
        assertTrue(!prompt.contains("**chào**"))
        assertContains(prompt, "not cartoon")
        assertContains(prompt, "zero written language")
    }

    @Test
    fun forSentence_usesScenesNotComicPanels() {
        val prompt = PromptFactory.forSentence(
            sentence = "Lan mỉm cười.",
            sentenceIndex = 0,
            sentenceCount = 2,
            characters = listOf(
                PromptFactory.CharacterRef(
                    name = "Lan",
                    role = "main",
                    description = "young woman",
                    hasReferenceImage = true,
                ),
            ),
        )
        assertContains(prompt, "scene 1 of 2")
        assertTrue(!prompt.contains("panel 1 of 2"))
        assertContains(prompt, "Image 1 is main character \"Lan\"")
        assertContains(prompt, "pretty realistic expressive art")
    }
}
