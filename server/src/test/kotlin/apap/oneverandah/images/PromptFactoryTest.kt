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
        assertContains(prompt, "NEW story beat")
        assertContains(prompt, PromptFactory.STORY_SHOTS[0])
        assertContains(prompt, "Story position: opening")
    }

    @Test
    fun forSentence_variesCameraAcrossStoryBeats() {
        val story = "Lan mỉm cười. Minh vẫy tay. Họ đi vào quán."
        val prompts = listOf(
            PromptFactory.forSentence(
                sentence = "Lan mỉm cười.",
                fullText = story,
                sentenceIndex = 0,
                sentenceCount = 3,
            ),
            PromptFactory.forSentence(
                sentence = "Minh vẫy tay.",
                fullText = story,
                sentenceIndex = 1,
                sentenceCount = 3,
            ),
            PromptFactory.forSentence(
                sentence = "Họ đi vào quán.",
                fullText = story,
                sentenceIndex = 2,
                sentenceCount = 3,
            ),
        )
        assertContains(prompts[0], PromptFactory.STORY_SHOTS[0])
        assertContains(prompts[1], PromptFactory.STORY_SHOTS[1])
        assertContains(prompts[2], PromptFactory.STORY_SHOTS[2])
        assertContains(prompts[0], "Story position: opening")
        assertContains(prompts[1], "progressing beat 2")
        assertContains(prompts[2], "Story position: ending")
        assertTrue(prompts.distinct().size == prompts.size)
        prompts.forEach { prompt ->
            assertContains(prompt, "do not illustrate the whole story")
            assertContains(prompt, "Never generate the same medium shot")
        }
    }

    @Test
    fun forCharacterPortrait_isolatesSubjectOnBlankBackground() {
        val prompt = PromptFactory.forCharacterPortrait(
            name = "Lan",
            role = "main",
            description = "tall sexy girl standing on a crowded beach at sunset",
        )
        assertContains(prompt, "Character reference portrait")
        assertContains(prompt, "Lan")
        assertContains(prompt, "tall sexy girl standing on a crowded beach at sunset")
        assertContains(prompt, PromptFactory.PORTRAIT_ISOLATION)
        assertContains(prompt, "blank empty background")
        assertContains(prompt, "ignore any scene, place, activity, or background")
        assertTrue(prompt.endsWith(PromptFactory.PORTRAIT_ISOLATION))
    }
}
