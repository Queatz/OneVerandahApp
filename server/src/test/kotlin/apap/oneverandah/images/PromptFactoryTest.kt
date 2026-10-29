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
    fun forWord_leadsWithIntendedSceneAndOmitsSourceWord() {
        val prompt = PromptFactory.forWord(
            "A neighbor waves from a sunlit apartment doorway, warm cinematic medium shot",
        )
        assertContains(prompt, "A neighbor waves from a sunlit apartment doorway")
        assertContains(prompt, "movie-like")
        assertTrue(!prompt.contains("chào"))
        assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        assertContains(prompt, "not cartoon")
        assertContains(prompt, "zero written language")
    }

    @Test
    fun forWord_usesLanguageAndSessionAppearanceCuesWithoutRequiringTheSamePeople() {
        val prompt = PromptFactory.forWord(
            intendedScene = "A woman smiles at her friend in a bright room",
            textLanguageTag = "my",
            characterDescriptions = listOf("young Burmese woman with dark hair"),
        )

        assertContains(prompt, "Source language tag: my")
        assertContains(prompt, "ethnic appearance and cultural cues native to this language community")
        assertContains(prompt, "young Burmese woman with dark hair")
        assertContains(prompt, "this example may feature different people")
    }

    @Test
    fun forPlayground_usesAnEnglishIntendedSceneWithoutSourceWording() {
        val prompt = PromptFactory.forPlayground(
            intendedScene = "A neighbor waves from a sunlit apartment doorway",
            natural = true,
        )

        assertContains(prompt, "A neighbor waves from a sunlit apartment doorway")
        assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        assertTrue(!prompt.contains("chào", ignoreCase = true))
        assertTrue(!prompt.contains("Xin chào bạn!"))
    }

    @Test
    fun forPlayScenario_usesAnEnglishIntendedSceneWithoutSourceWording() {
        val prompt = PromptFactory.forPlayScenario(
            intendedScene = "A neighbor waves from a sunlit apartment doorway",
            characters = emptyList(),
        )

        assertContains(prompt, "A neighbor waves from a sunlit apartment doorway")
        assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        assertTrue(!prompt.contains("chào", ignoreCase = true))
    }

    @Test
    fun forSentence_usesScenesNotComicPanels() {
        val prompt = PromptFactory.forSentence(
            intendedScene = "Lan smiles from a sunlit doorway, cinematic medium shot",
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
        assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        assertTrue(!prompt.contains("mỉm cười"))
    }

    @Test
    fun forSentence_variesCameraAcrossStoryBeats() {
        val prompts = listOf(
            PromptFactory.forSentence(
                intendedScene = "Lan smiles in a sunlit doorway",
                sentenceIndex = 0,
                sentenceCount = 3,
            ),
            PromptFactory.forSentence(
                intendedScene = "Minh waves from across a courtyard",
                sentenceIndex = 1,
                sentenceCount = 3,
            ),
            PromptFactory.forSentence(
                intendedScene = "They step into a warm neighborhood cafe",
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
            assertContains(prompt, "Do not illustrate the whole story")
            assertContains(prompt, "Never generate the same medium shot")
            assertContains(prompt, PromptFactory.ENGLISH_ONLY)
        }
        assertTrue(prompts.none { it.contains("mỉm cười") || it.contains("vẫy tay") || it.contains("Họ đi") })
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
