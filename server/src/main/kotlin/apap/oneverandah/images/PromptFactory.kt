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

    const val ENGLISH_ONLY =
        "Write every part of this visual direction in English. " +
            "Do not include any other written language in the prompt or the picture."

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
     * Word / phrase visual. [intendedScene] is an English movie-still description;
     * the vocabulary string itself must never appear in this prompt.
     */
    fun forWord(
        intendedScene: String,
        textLanguageTag: String = "",
        characterDescriptions: List<String> = emptyList(),
    ): String {
        val scene = intendedScene.trim()
        return finish(
            buildString {
                append("Create a single beautiful, realistic, expressive cinematic still that illustrates a vocabulary idea through a movie-like everyday scene. ")
                if (textLanguageTag.isNotBlank()) {
                    append("Source language tag: ${textLanguageTag.trim()}. When people appear, depict people with the ethnic appearance and cultural cues native to this language community; do not default to Western appearance. ")
                }
                val appearanceCues = characterDescriptions.map(String::trim).filter(String::isNotEmpty).distinct()
                if (appearanceCues.isNotEmpty()) {
                    append("Appearance cues from the session's character designs (not identity references; this example may feature different people): ")
                    append(appearanceCues.joinToString("; "))
                    append(". ")
                }
                if (scene.isNotEmpty()) {
                    append("Intended movie-like scene to depict: $scene. ")
                } else {
                    append("Depict a clear everyday moment through objects, people, and action only. ")
                }
                append("No title card, no chalkboard, no textbook page, no subtitle. ")
                append(ENGLISH_ONLY)
            },
        )
    }

    fun forPlayground(intendedScene: String, natural: Boolean): String {
        val sceneGuidance = if (natural) {
            "Depict a vivid, natural everyday moment."
        } else {
            "Depict the intended meaning as a corrected, natural everyday moment."
        }
        val scene = intendedScene.trim()
        return finish(
            buildString {
                append("Beautiful realistic expressive still for language learning. ")
                if (scene.isNotEmpty()) {
                    append("Intended movie-like scene to depict: $scene. ")
                } else {
                    append("Show a clear everyday moment through people, action, and environment. ")
                }
                append(sceneGuidance)
                append(" Warm natural scene, clear character emotion, cinematic composition — not a comic panel. ")
                append(ENGLISH_ONLY)
            },
        )
    }

    fun forTalkSceneBackground(setting: String, title: String, description: String): String {
        val scene = listOf(setting, title, description)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(". ")
        return buildString {
            append("Create a wide cinematic empty location plate for a language-learning scene. ")
            if (scene.isNotEmpty()) append("Depict exactly one specific unoccupied location: $scene. ")
            append("Make this location visibly distinct from the other scene locations; do not combine or montage places. ")
            append("Show only unoccupied architecture, landscape, props, weather, and light; no people, human figures, characters, or portraits. ")
            append("No speech bubbles, dialogue balloons, captions, panels, or overlays. ")
            append("Use a wide landscape composition with clear depth and open foreground space. ")
            append("Use cinematic natural lighting, fine environmental detail, and a warm painterly realism; not cartoon, comic, manga, anime, storybook, or clipart. ")
            append(NO_TEXT)
        }
    }

    fun forCharacterPortrait(name: String, role: String, description: String): String {
        val roleLabel = when (role.lowercase()) {
            "secondary" -> "secondary supporting character"
            else -> "main protagonist"
        }
        val body = finish(
            buildString {
                append("Character reference portrait for a continuous short story. ")
                append("This image is ONLY a clean identity reference of one isolated person. ")
                append("Name: $name. Role: $roleLabel. ")
                append("Appearance (face, body, hair, clothing only — ignore any scene, place, activity, or background in this text): $description. ")
                append("Show a clear front three-quarter view, head-to-waist or full body, ")
                append("face and clothing highly readable, natural standing or gentle pose, no props that hide the face. ")
                append("No collage, no name tag.")
            },
        )
        return "$body $PORTRAIT_ISOLATION"
    }

    /** Isolation wins over a freeform appearance prompt that might mention a scene. */
    internal const val PORTRAIT_ISOLATION =
        "Isolation override: generate this person alone on a blank empty background. " +
            "Solid seamless studio void, plain white or light gray, no environment. " +
            "One person only. No other people, no furniture, no room, no landscape, no street, no props, no scenery. " +
            "Do not illustrate any setting or action implied by the appearance text."

    fun forSentence(
        intendedScene: String,
        sentenceIndex: Int = 0,
        sentenceCount: Int = 1,
        characters: List<CharacterRef> = emptyList(),
    ): String {
        val sceneNumber = sentenceIndex.coerceAtLeast(0) + 1
        val totalScenes = sentenceCount.coerceAtLeast(1)
        val scene = intendedScene.trim()
        return finish(
            buildString {
                append("Beautiful realistic expressive still for language learners. ")
                append("This is scene $sceneNumber of $totalScenes in one continuous short story. ")
                append("Keep the same people, wardrobe, and identity — but this is a NEW story beat, not a reprint of another scene. ")
                append("Do not illustrate the whole story; only this beat. ")
                if (scene.isNotEmpty()) {
                    append("Intended movie-like scene to depict: $scene. ")
                }
                append(characterGuidance(characters))
                append(sceneVariety(sentenceIndex, sentenceCount))
                append("Show a vivid cinematic moment with clear emotion and natural body language. ")
                append("Setting: warm everyday life that fits this beat — vary the room, street, weather, and props. ")
                append(ENGLISH_ONLY)
                append(" Not a comic panel.")
            },
        )
    }

    fun forPlayScenario(
        intendedScene: String,
        characters: List<CharacterRef> = emptyList(),
    ): String {
        val scene = intendedScene.trim()
        return finish(
            buildString {
                append("Beautiful realistic expressive still for an interactive language role-play. ")
                append("This is a continuous short story world — keep the same people, wardrobe, and look. ")
                if (scene.isNotEmpty()) {
                    append("Intended movie-like scene to depict: $scene. ")
                } else {
                    append("Show a vivid conversational moment in a richly detailed everyday setting. ")
                }
                append(characterGuidance(characters))
                append("Show clear emotion and natural body language. ")
                append("Setting: warm everyday life in Vietnam or Russia, whichever fits the scene best. ")
                append("Not a comic panel. ")
                append(ENGLISH_ONLY)
            }
        )
    }

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
            append("If this beat implies a new action or place, show that change. ")
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
