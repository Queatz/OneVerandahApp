package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.isTenRoundExam
import apap.oneverandah.api.StudyTalkAudioKind

enum class StudyBuildTaskPool { TEXT, IMAGE }

enum class StudyBuildTaskPriority { HIGH, LOW }

data class StudyBuildTask(
    val sessionId: String,
    val element: StudyBuildElement,
    val priorityOverride: StudyBuildTaskPriority? = null,
) {
    val key: String get() = "$sessionId:${element.key}"
    val pool: StudyBuildTaskPool
        get() = when (element.kind) {
            StudyBuildElementKind.PORTRAIT,
            StudyBuildElementKind.WORD_IMAGE,
            StudyBuildElementKind.SENTENCE_IMAGE,
            StudyBuildElementKind.PLAY_COVER,
            StudyBuildElementKind.TALK_SCENE_IMAGE,
            StudyBuildElementKind.TALK_EXPRESSION,
                -> StudyBuildTaskPool.IMAGE

            else -> StudyBuildTaskPool.TEXT
        }
    val priority: StudyBuildTaskPriority
        get() = priorityOverride ?: if (element.kind == StudyBuildElementKind.WORD_IMAGE) {
            StudyBuildTaskPriority.LOW
        } else {
            StudyBuildTaskPriority.HIGH
        }
}

class StudyBuildPlanner(
    private val maxAttempts: Int = 3,
    private val supportsTalkImages: Boolean = true,
    private val supportsTalkAudio: Boolean = true,
) {
    fun plan(record: SavedStudySessionRecord): List<StudyBuildTask> =
        pendingElements(record)
            .filter { element ->
                element.key !in failedKeys(record) &&
                        (record.build?.attempts?.get(element.key) ?: 0) < maxAttempts
            }
            .map { StudyBuildTask(record.id, it) }

    fun exhausted(record: SavedStudySessionRecord): List<StudyBuildElement> =
        pendingElements(record).filter { element ->
            element.key !in failedKeys(record) &&
                    (record.build?.attempts?.get(element.key) ?: 0) >= maxAttempts
        }

    private fun pendingElements(record: SavedStudySessionRecord): List<StudyBuildElement> {
        val state = record.build ?: return emptyList()
        if (state.status == StudyBuildStatus.COMPLETE || state.status == StudyBuildStatus.PARTIAL) {
            return pendingChunkBuilds(record) + pendingExampleDissections(record) +
                    pendingWordRecallScenarios(record) + pendingTalkBuilds(record)
        }

        if (record.session.sentences.isEmpty()) {
            if (state.status == StudyBuildStatus.PREPARING && record.buildRequest != null) {
                return listOf(StudyBuildElement(StudyBuildElementKind.PREPARE))
            }
            return listOf(StudyBuildElement(StudyBuildElementKind.SKELETON))
        }

        val elements = mutableListOf<StudyBuildElement>()
        if (record.name.isBlank()) elements += StudyBuildElement(StudyBuildElementKind.NAME)
        elements += pendingChunkBuilds(record)

        val dissectionsSettled = record.session.sentences.indices.all { index ->
            val sentence = record.session.sentences[index]
            sentence.tokens.none { it.status == StudyTokenStatus.PENDING } ||
                    isFailed(record, StudyBuildElement(StudyBuildElementKind.DISSECT, sentenceIndex = index))
        }
        record.session.sentences.forEachIndexed { sentenceIndex, sentence ->
            val dissection = StudyBuildElement(StudyBuildElementKind.DISSECT, sentenceIndex = sentenceIndex)
            val hasPendingTokens = sentence.tokens.any { it.status == StudyTokenStatus.PENDING }
            if (hasPendingTokens && !isFailed(record, dissection)) {
                elements += dissection
            }
        }

        val characters = record.session.characters
        val charactersDesigned = characters.isNotEmpty() ||
                isFailed(record, StudyBuildElement(StudyBuildElementKind.CHARACTERS))
        if (dissectionsSettled && charactersDesigned) {
            record.session.sentences.forEachIndexed { sentenceIndex, sentence ->
                sentence.tokens.forEachIndexed { tokenIndex, token ->
                    if (token.isDissectable && token.imageUrl.isNullOrBlank()) {
                        elements += StudyBuildElement(
                            StudyBuildElementKind.WORD_IMAGE,
                            sentenceIndex = sentenceIndex,
                            tokenIndex = tokenIndex,
                        )
                    }
                }
            }
        }

        if (dissectionsSettled) {
            elements += pendingExampleDissections(record)
            elements += pendingWordRecallScenarios(record)
        }

        if (characters.isEmpty()) {
            elements += StudyBuildElement(StudyBuildElementKind.CHARACTERS)
        } else {
            characters.forEach { character ->
                if (character.imageUrl.isNullOrBlank()) {
                    elements += StudyBuildElement(
                        StudyBuildElementKind.PORTRAIT,
                        characterId = character.id,
                    )
                }
            }
        }

        val mainCharacter = characters.firstOrNull { it.role.equals("main", ignoreCase = true) }
            ?: characters.firstOrNull()
        val mainPortrait = mainCharacter?.let {
            StudyBuildElement(StudyBuildElementKind.PORTRAIT, characterId = it.id)
        }
        val mainPortraitSettled = when {
            mainCharacter == null -> characters.isEmpty() && isFailed(
                record,
                StudyBuildElement(StudyBuildElementKind.CHARACTERS),
            )

            !mainCharacter.imageUrl.isNullOrBlank() -> true
            mainPortrait != null && isFailed(record, mainPortrait) -> true
            else -> false
        }

        if (mainPortraitSettled) {
            record.session.sentences.forEachIndexed { index, sentence ->
                if (sentence.imageUrl.isNullOrBlank()) {
                    elements += StudyBuildElement(StudyBuildElementKind.SENTENCE_IMAGE, sentenceIndex = index)
                }
            }
            if (record.session.playScenarios.isEmpty()) {
                elements += StudyBuildElement(StudyBuildElementKind.PLAY_SCENARIOS)
            } else {
                record.session.playScenarios.forEach { scenario ->
                    if (scenario.imageUrl.isNullOrBlank()) {
                        elements += StudyBuildElement(
                            StudyBuildElementKind.PLAY_COVER,
                            scenarioId = scenario.id,
                        )
                    }
                }
            }
        }

        val castSettled = when {
            characters.isEmpty() -> isFailed(record, StudyBuildElement(StudyBuildElementKind.CHARACTERS))
            else -> characters.all { character ->
                !character.imageUrl.isNullOrBlank() ||
                        isFailed(record, StudyBuildElement(StudyBuildElementKind.PORTRAIT, characterId = character.id))
            }
        }
        if (dissectionsSettled && castSettled && record.session.examScene?.isTenRoundExam() != true) {
            elements += StudyBuildElement(StudyBuildElementKind.EXAM)
        }
        if (dissectionsSettled && charactersDesigned) elements += pendingTalkBuilds(record)

        return elements
    }

    private fun pendingTalkBuilds(record: SavedStudySessionRecord): List<StudyBuildElement> {
        val session = record.session
        if (session.sentences.isEmpty() || session.characters.isEmpty()) return emptyList()
        val world = session.talkWorld ?: return listOf(StudyBuildElement(StudyBuildElementKind.TALK_WORLD))
        val elements = mutableListOf<StudyBuildElement>()
        if (supportsTalkImages) {
            world.scenes.forEachIndexed { sceneIndex, scene ->
                val element = StudyBuildElement(
                    StudyBuildElementKind.TALK_SCENE_IMAGE,
                    sceneIndex = sceneIndex,
                )
                if (scene.backgroundImageUrl.isNullOrBlank() && !isFailed(record, element)) {
                    elements += element
                }
            }
            val usedExpressionIds = world.scenes.flatMap { scene ->
                scene.turns.map { it.expressionId }
            }.toSet()
            world.expressions.filter { it.id in usedExpressionIds }.forEach { expression ->
                val element = StudyBuildElement(
                    StudyBuildElementKind.TALK_EXPRESSION,
                    expressionId = expression.id,
                )
                if (expression.imageUrl.isNullOrBlank() && !isFailed(record, element)) {
                    elements += element
                }
            }
        }
        if (supportsTalkAudio) {
            world.scenes.forEachIndexed { sceneIndex, scene ->
                scene.turns.forEachIndexed { turnIndex, turn ->
                    val characterAudio = StudyBuildElement(
                        kind = StudyBuildElementKind.TALK_AUDIO,
                        sceneIndex = sceneIndex,
                        turnIndex = turnIndex,
                        audioKind = StudyTalkAudioKind.CHARACTER_LINE,
                    )
                    if (turn.characterAudioUrl.isNullOrBlank() && !isFailed(record, characterAudio)) {
                        elements += characterAudio
                    }
                    val replyAudio = StudyBuildElement(
                        kind = StudyBuildElementKind.TALK_AUDIO,
                        sceneIndex = sceneIndex,
                        turnIndex = turnIndex,
                        audioKind = StudyTalkAudioKind.REPLY,
                    )
                    if (turn.replyAudioUrl.isNullOrBlank() && !isFailed(record, replyAudio)) {
                        elements += replyAudio
                    }
                }
            }
        }
        return elements
    }

    private fun pendingExampleDissections(record: SavedStudySessionRecord): List<StudyBuildElement> =
        record.session.sentences.flatMapIndexed { sentenceIndex, sentence ->
            sentence.tokens.mapIndexedNotNull { tokenIndex, token ->
                val element = StudyBuildElement(
                    StudyBuildElementKind.EXAMPLE_DISSECT,
                    sentenceIndex = sentenceIndex,
                    tokenIndex = tokenIndex,
                )
                if (
                    token.isDissectable &&
                    token.exampleSentences.isNotEmpty() &&
                    token.exampleSentences.any { it.tokens.isEmpty() } &&
                    !isFailed(record, element)
                ) {
                    element
                } else {
                    null
                }
            }
        }

    private fun pendingChunkBuilds(record: SavedStudySessionRecord): List<StudyBuildElement> =
        record.session.sentences.mapIndexedNotNull { sentenceIndex, sentence ->
            if (sentence.chunks.isNotEmpty()) {
                null
            } else {
                StudyBuildElement(StudyBuildElementKind.CHUNKS, sentenceIndex = sentenceIndex)
            }
        }

    private fun pendingWordRecallScenarios(record: SavedStudySessionRecord): List<StudyBuildElement> =
        record.session.sentences.mapIndexedNotNull { sentenceIndex, sentence ->
            if (sentence.tokens.any { it.isDissectable && it.needConversation.isEmpty() }) {
                StudyBuildElement(StudyBuildElementKind.WORD_RECALL_SCENARIOS, sentenceIndex = sentenceIndex)
            } else {
                null
            }
        }

    private fun isFailed(record: SavedStudySessionRecord, element: StudyBuildElement): Boolean =
        element.key in failedKeys(record)

    private fun failedKeys(record: SavedStudySessionRecord): Set<String> =
        record.build?.failed.orEmpty().mapTo(mutableSetOf()) { it.key }
}
