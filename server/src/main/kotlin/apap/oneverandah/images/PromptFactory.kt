package apap.oneverandah.images

import apap.oneverandah.model.Category
import apap.oneverandah.model.Service

/**
 * Single source of art direction and image prompts.
 *
 * Catalog covers, study visuals, mini-story stills, and phrase scenes all go
 * through here so every generator sees the same pretty, realistic, expressive
 * look — and the same hard ban on written language in the picture.
 *
 * English subject names keep the visual world consistent regardless of UI language.
 */
object PromptFactory {
    /**
     * Shared look: pretty, realistic, expressive — not comic / storybook / cartoon.
     * Keep this string stable; [withArtDirection] uses it to avoid double-appending.
     */
    const val ART_STYLE =
        "pretty realistic expressive art, cinematic natural lighting, lifelike faces and hands, " +
            "rich emotion, fine tactile detail, warm inviting palette, photoreal with a painterly softness, " +
            "not cartoon, not comic, not manga, not anime, not storybook illustration, not clipart"

    const val NO_TEXT =
        "Absolutely no text, no letters, no numbers, no captions, no subtitles, no signs with writing, " +
            "no logos, no speech bubbles, no name labels, no watermark, no border, no framing. " +
            "The picture must contain zero written language of any kind."

    data class CharacterRef(
        val name: String,
        val role: String,
        val description: String = "",
        val hasReferenceImage: Boolean = false,
    )

    fun forCategory(category: Category): String =
        finish(
            "Beautiful realistic image representing the category '${category.title.en}' " +
                "for a premium lifestyle services marketplace.",
        )

    fun forService(service: Service, categoryName: String): String =
        finish(
            "Beautiful realistic image representing the service '${service.title.en}' " +
                "in the category '$categoryName' for a premium lifestyle services marketplace.",
        )

    /**
     * Word / phrase visual. Meaning drives the scene; the vocabulary string is
     * mentioned only as something that must never appear as writing.
     */
    fun forWord(word: String, contextualMeaning: String = ""): String {
        val meaning = contextualMeaning.trim()
        val concept = word.trim()
        val subject = if (meaning.isNotEmpty()) {
            "Meaning to depict through a clear everyday scene (show this visually, never write it): $meaning."
        } else {
            "Depict the real-world idea behind this language-learning concept through objects, people, and action only."
        }
        return finish(
            buildString {
                append("Create a single beautiful, realistic, expressive image that illustrates a vocabulary idea. ")
                append(subject)
                append(' ')
                if (concept.isNotEmpty()) {
                    append("Do not render these characters anywhere in the picture: \"$concept\". ")
                }
                append("No title card, no chalkboard, no textbook page, no subtitle.")
            },
        )
    }

    fun forPlayground(word: String, sentence: String, natural: Boolean): String {
        val sceneGuidance = if (natural) {
            "Depict the learner's sentence as a vivid, natural everyday moment."
        } else {
            "Interpret the intended meaning of the learner's attempt and depict a corrected, natural everyday moment."
        }
        return finish(
            buildString {
                append("Beautiful realistic expressive still for language learning. ")
                append("Show this everyday moment: \"$sentence\". ")
                append("Let the action carry the meaning of \"$word\" without writing either phrase. ")
                append(sceneGuidance)
                append(" Warm natural scene, clear character emotion, cinematic composition — not a comic panel.")
            },
        )
    }

    fun forCharacterPortrait(name: String, role: String, description: String): String {
        val roleLabel = when (role.lowercase()) {
            "secondary" -> "secondary supporting character"
            else -> "main protagonist"
        }
        return finish(
            buildString {
                append("Character reference portrait for a continuous short story. ")
                append("This image is ONLY a clean identity reference of one person. ")
                append("Name: $name. Role: $roleLabel. ")
                append("Appearance: $description. ")
                append("Show a clear front three-quarter view, head-to-waist or full body, plain soft neutral background, ")
                append("face and clothing highly readable, natural standing or gentle pose, no props that hide the face. ")
                append("No collage, no name tag.")
            },
        )
    }

    fun forSentence(
        sentence: String,
        fullText: String = "",
        sentenceIndex: Int = 0,
        sentenceCount: Int = 1,
        characters: List<CharacterRef> = emptyList(),
    ): String {
        val sceneNumber = sentenceIndex.coerceAtLeast(0) + 1
        val totalScenes = sentenceCount.coerceAtLeast(1)
        return finish(
            buildString {
                append("Beautiful realistic expressive still for language learners. ")
                append("This is scene $sceneNumber of $totalScenes in one continuous short story. ")
                append("Keep the same people, wardrobe, and identity — but this is a NEW story beat, not a reprint of another scene. ")
                if (fullText.isNotBlank()) {
                    append("Full story text for continuity only (do not render it, and do not illustrate the whole story): \"$fullText\". ")
                }
                append("THIS scene's unique beat to depict (do not render the sentence): \"$sentence\". ")
                append(characterGuidance(characters))
                append(sceneVariety(sentenceIndex, sentenceCount))
                append("Show a vivid story moment that captures THIS beat's meaning with clear emotion and natural body language. ")
                append("Setting: warm everyday life in Vietnam or Russia, whichever fits the text best — vary the room, street, weather, and props to match this beat. ")
                append("Not a comic panel.")
            },
        )
    }

    fun forPlayScenario(
        description: String,
        openingLine: String? = null,
        fullText: String = "",
        characters: List<CharacterRef> = emptyList(),
    ): String = finish(
        buildString {
            append("Beautiful realistic expressive still for an interactive language role-play. ")
            append("This is a continuous short story world — keep the same people, wardrobe, and look. ")
            if (fullText.isNotBlank()) {
                append("Source study text for continuity (do not render it): \"$fullText\". ")
            }
            append("Scene setup to depict: \"$description\". ")
            if (!openingLine.isNullOrBlank()) {
                append("Spoken moment / dialogue vibe (do not render text): \"$openingLine\". ")
            }
            append(characterGuidance(characters))
            append("Show a vivid conversational moment with clear emotion and natural body language. ")
            append("Setting: warm everyday life in Vietnam or Russia, whichever fits the text best. ")
            append("Not a comic panel.")
        },
    )

    /**
     * Appends shared style + no-text rules unless the prompt already carries them.
     * Use this for admin-authored story/phrase prompts so they stay in the same world.
     */
    fun withArtDirection(prompt: String): String {
        val trimmed = prompt.trim()
        require(trimmed.isNotEmpty()) { "prompt is required" }
        val hasStyle = trimmed.contains(ART_STYLE, ignoreCase = true)
        val hasNoText = trimmed.contains("no text", ignoreCase = true)
        if (hasStyle && hasNoText) return trimmed
        return finish(trimmed)
    }

    private fun finish(body: String): String =
        "${body.trim()} Art style: $ART_STYLE. $NO_TEXT High quality square image."

    /** Cycles across consecutive sentence stills so the camera never repeats the same setup. */
    internal val STORY_SHOTS = listOf(
        "wide establishing shot with environment storytelling, characters placed in a real space",
        "medium shot at eye level, clear gesture and interaction, new foreground",
        "intimate close-up of face and hands, shallow depth of field, emotion of this line",
        "over-the-shoulder or three-quarter profile, layered foreground and background",
        "low-angle or high-angle cinematic frame that changes the power of the moment",
        "environmental insert: the beat's objects and setting take half the frame, character still present",
        "two-shot with strong spatial gap or closeness that matches this beat's relationship",
        "moment of motion — walking, reaching, turning — freeze a different body pose",
    )

    /**
     * Film-like shot list so consecutive sentence stills do not collapse into the same
     * medium portrait. Identity stays; camera, pose, and space change.
     */
    private fun sceneVariety(sentenceIndex: Int, sentenceCount: Int): String {
        val index = sentenceIndex.coerceAtLeast(0)
        val total = sentenceCount.coerceAtLeast(1)
        val sceneNumber = index + 1
        val shot = STORY_SHOTS[index % STORY_SHOTS.size]
        val placeInStory = when {
            total <= 1 -> "Story position: a complete cinematic moment."
            sceneNumber == 1 -> "Story position: opening. Establish the world with a distinct location and first impression."
            sceneNumber == total -> "Story position: ending. Different camera and emotional payoff — not a copy of the opening."
            else -> "Story position: progressing beat $sceneNumber. Advance time, action, and space from earlier scenes."
        }
        return buildString {
            append("Cinematic variety for this still: $shot. ")
            append(placeInStory)
            append(" Change the camera (wide vs close, high vs low, front vs side), the characters' poses, and the background arrangement. ")
            append("If the sentence implies a new action or place, show that change. ")
            append("Never generate the same medium shot of the same people standing in the same spot. ")
        }
    }

    private fun characterGuidance(characters: List<CharacterRef>): String = buildString {
        val refs = characters.filter { it.hasReferenceImage }
        if (refs.isNotEmpty()) {
            append("Named character reference images are attached in order. Match their identity exactly: ")
            refs.forEachIndexed { index, character ->
                appendCharacter(index + 1, character, numbered = true)
            }
            append("Keep face, age, hair, skin, body type, and clothing consistent with those references. ")
        } else if (characters.isNotEmpty()) {
            append("Feature these named characters consistently: ")
            characters.forEach { character ->
                appendCharacter(index = 0, character = character, numbered = false)
            }
        } else {
            append("Invent one clear main character and keep them emotionally readable. ")
        }
    }

    private fun StringBuilder.appendCharacter(index: Int, character: CharacterRef, numbered: Boolean) {
        val roleLabel = if (character.role.equals("secondary", ignoreCase = true)) "secondary" else "main"
        if (numbered) {
            append("Image $index is $roleLabel character \"${character.name}\"")
        } else {
            append("$roleLabel character \"${character.name}\"")
        }
        if (character.description.isNotBlank()) {
            append(if (numbered) " (${character.description})" else " — ${character.description}")
        }
        append(". ")
    }
}
