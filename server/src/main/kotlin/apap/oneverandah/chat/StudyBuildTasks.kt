package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildEvent
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildMode
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyDissectSentenceRequest
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.StudyExamSceneRequest
import apap.oneverandah.api.StudyPlayScenariosRequest
import apap.oneverandah.api.StudySentenceImageRequest
import apap.oneverandah.api.StudySessionRequest
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.api.isTenRoundExam
import apap.oneverandah.model.Language
import org.slf4j.LoggerFactory

fun interface StudyBuildTaskExecutor {
    suspend fun execute(task: StudyBuildTask)
}

class StudyBuildTasks(
    private val store: StudySessionStore,
    private val studyService: StudyService,
    private val studySessionsService: StudySessionsService,
    private val speechService: SpeechService? = null,
) : StudyBuildTaskExecutor {
    private val log = LoggerFactory.getLogger(StudyBuildTasks::class.java)
    override suspend fun execute(task: StudyBuildTask) {
        val record = store.findById(task.sessionId) ?: return
        if (isComplete(record, task)) return

        when (task.element.kind) {
            StudyBuildElementKind.PREPARE -> prepare(record)
            StudyBuildElementKind.SKELETON -> buildSkeleton(record)
            StudyBuildElementKind.NAME -> generateName(record)
            StudyBuildElementKind.DISSECT -> dissect(record, task)
            StudyBuildElementKind.CHUNKS -> buildChunks(record, task)
            StudyBuildElementKind.WORD_RECALL_SCENARIOS -> buildWordRecallScenarios(record, task)
            StudyBuildElementKind.EXAMPLE_DISSECT -> dissectExamples(record, task)
            StudyBuildElementKind.WORD_IMAGE -> renderWordImage(record, task)
            StudyBuildElementKind.CHARACTERS -> designCharacters(record)
            StudyBuildElementKind.PORTRAIT -> renderPortrait(record, task)
            StudyBuildElementKind.SENTENCE_IMAGE -> renderSentenceImage(record, task)
            StudyBuildElementKind.PLAY_SCENARIOS -> designPlayScenarios(record)
            StudyBuildElementKind.PLAY_COVER -> renderPlayCover(record, task)
            StudyBuildElementKind.EXAM -> generateExam(record)
        }
    }

    private suspend fun prepare(record: SavedStudySessionRecord) {
        val request = record.buildRequest ?: return
        val preparedText = studyService.prepareText(request)
        val textLanguageTag = if (request.mode == StudyBuildMode.TRANSLATE) {
            Language.normalizeContentTag(requireNotNull(request.targetLanguageTag))
        } else {
            record.textLanguageTag
        }
        val updated = store.update(record.id) { current ->
            if (current.buildRequest == null) return@update current
            current.copy(
                text = preparedText,
                textLanguageTag = textLanguageTag,
                session = current.session.copy(text = preparedText, textLanguageTag = textLanguageTag),
                buildRequest = null,
                build = current.build?.copy(status = StudyBuildStatus.BUILDING),
            )
        } ?: return
        updated.build?.let { store.publish(record.id, StudyBuildEvent.Build(it)) }
    }

    private suspend fun buildSkeleton(record: SavedStudySessionRecord) {
        val session = studyService.skeleton(
            StudySessionRequest(
                text = record.text,
                languageTag = record.languageTag,
                sourceMessageId = record.sourceMessageId,
            ),
        )
        store.update(record.id, reason = StudyBuildEvent.Skeleton(session)) { current ->
            if (current.session.sentences.isNotEmpty()) return@update current
            current.copy(
                text = session.text,
                textLanguageTag = session.textLanguageTag,
                session = session,
                build = current.build?.copy(status = StudyBuildStatus.BUILDING),
            )
        }
    }

    private suspend fun generateName(record: SavedStudySessionRecord) {
        val name = studySessionsService.generateName(record.text, record.languageTag)
        store.update(record.id, reason = StudyBuildEvent.Renamed(name)) { current ->
            if (current.name.isNotBlank()) return@update current
            current.copy(name = name)
        }
    }

    private suspend fun dissect(record: SavedStudySessionRecord, task: StudyBuildTask) {
        val sentenceIndex = requireNotNull(task.element.sentenceIndex)
        val sentence = record.session.sentences.getOrNull(sentenceIndex) ?: return
        val result = studyService.dissectOnly(
            StudyDissectSentenceRequest(
                text = record.text,
                sentenceText = sentence.text,
                tokens = sentence.tokens.map { it.text },
                textLanguageTag = record.textLanguageTag,
                languageTag = record.languageTag,
            ),
        )
        val mergedTokens = result.tokens.mapIndexed { index, token ->
            val previous = sentence.tokens.getOrNull(index) ?: return@mapIndexed token
            if (previous.status != StudyTokenStatus.PENDING) {
                previous.copy(
                    imageId = previous.imageId ?: token.imageId,
                    imageUrl = previous.imageUrl ?: token.imageUrl,
                )
            } else {
                token.copy(imageId = previous.imageId, imageUrl = previous.imageUrl)
            }
        }
        val dissectedSentence = sentence.copy(translation = result.translation, tokens = mergedTokens)
        store.update(
            record.id,
            reason = StudyBuildEvent.SentenceDissected(sentenceIndex, dissectedSentence)
        ) {
            if (it.session.sentences.getOrNull(sentenceIndex) == null) return@update it
            it.copy(
                session = it.session.copy(
                    sentences = it.session.sentences.mapIndexed { index, current ->
                        if (index == sentenceIndex) current.copy(
                            translation = result.translation,
                            tokens = mergedTokens,
                        ) else current
                    },
                ),
            )
        }
    }

    private suspend fun buildChunks(record: SavedStudySessionRecord, task: StudyBuildTask) {
        val sentenceIndex = requireNotNull(task.element.sentenceIndex)
        val sentence = record.session.sentences.getOrNull(sentenceIndex) ?: return
        if (sentence.breakdownAudioReady) return
        val (chunks, suffixes) = if (sentence.chunks.isNotEmpty()) {
            sentence.chunks to sentence.breakdownSuffixes.ifEmpty {
                apap.oneverandah.api.studyBreakdownSuffixes(
                    sentence.text,
                    sentence.tokens.map { it.text },
                    sentence.chunks,
                )
            }
        } else {
            studyService.chunkSentence(
                sentenceText = sentence.text,
                tokens = sentence.tokens,
                textLanguageTag = record.textLanguageTag,
                languageTag = record.languageTag,
            )
        }
        val chunksSaved = store.update(record.id) { current ->
            if (current.session.sentences.getOrNull(sentenceIndex) == null) return@update current
            current.copy(
                session = current.session.copy(
                    sentences = current.session.sentences.mapIndexed { index, item ->
                        if (index == sentenceIndex) item.copy(
                            chunks = chunks,
                            breakdownSuffixes = suffixes,
                            breakdownAudioReady = false,
                        ) else item
                    },
                ),
            )
        } ?: return
        chunksSaved.session.sentences.getOrNull(sentenceIndex)?.let { latest ->
            store.publish(record.id, StudyBuildEvent.SentenceDissected(sentenceIndex, latest))
        }

        val audioReady = runCatching {
            speechService?.let { speech ->
                (chunks.map { it.text } + suffixes).filter(String::isNotBlank).distinct().forEach { text ->
                    speech.synthesize(SpeechRequest(text = text, languageTag = record.textLanguageTag))
                }
            }
        }.onFailure { error ->
            log.warn(
                "Breakdown audio warmup failed for session {} sentence {}: {}",
                record.id,
                sentenceIndex,
                error.message,
            )
        }.isSuccess
        if (!audioReady) return
        val audioSaved = store.update(record.id) { current ->
            current.copy(
                session = current.session.copy(
                    sentences = current.session.sentences.mapIndexed { index, item ->
                        if (index == sentenceIndex) item.copy(breakdownAudioReady = true) else item
                    },
                ),
            )
        } ?: return
        audioSaved.session.sentences.getOrNull(sentenceIndex)?.let { latest ->
            store.publish(record.id, StudyBuildEvent.SentenceDissected(sentenceIndex, latest))
        }
    }

    private suspend fun buildWordRecallScenarios(record: SavedStudySessionRecord, task: StudyBuildTask) {
        val sentenceIndex = requireNotNull(task.element.sentenceIndex)
        val sentence = record.session.sentences.getOrNull(sentenceIndex) ?: return
        val scenarios = studyService.wordRecallScenarios(
            sentenceText = sentence.text,
            tokens = sentence.tokens,
            textLanguageTag = record.textLanguageTag,
            languageTag = record.languageTag,
        )
        if (scenarios.isEmpty()) return
        val updated = store.update(record.id) { current ->
            val existing = current.session.sentences.getOrNull(sentenceIndex) ?: return@update current
            val tokens = existing.tokens.mapIndexed { tokenIndex, token ->
                val conversation = scenarios[tokenIndex]?.takeIf { it.isNotEmpty() }
                if (token.isDissectable && token.needConversation.isEmpty() && conversation != null) {
                    token.copy(needConversation = conversation)
                } else {
                    token
                }
            }
            val sentences = current.session.sentences.toMutableList()
            sentences[sentenceIndex] = existing.copy(tokens = tokens)
            current.copy(session = current.session.copy(sentences = sentences))
        } ?: return
        updated.session.sentences.getOrNull(sentenceIndex)?.let { latest ->
            store.publish(record.id, StudyBuildEvent.SentenceDissected(sentenceIndex, latest))
        }
    }

    private suspend fun dissectExamples(record: SavedStudySessionRecord, task: StudyBuildTask) {
        val sentenceIndex = requireNotNull(task.element.sentenceIndex)
        val tokenIndex = requireNotNull(task.element.tokenIndex)
        val token = record.session.sentences.getOrNull(sentenceIndex)?.tokens?.getOrNull(tokenIndex) ?: return
        val examples = studyService.dissectExampleSentences(
            word = token.text,
            contextualMeaning = token.contextualMeaning,
            exampleSentences = token.exampleSentences,
            textLanguageTag = record.textLanguageTag,
            languageTag = record.languageTag,
        )
        val updated = store.update(record.id) { current ->
            val sentence = current.session.sentences.getOrNull(sentenceIndex) ?: return@update current
            if (sentence.tokens.getOrNull(tokenIndex) == null) return@update current
            current.copy(
                session = current.session.copy(
                    sentences = current.session.sentences.mapIndexed { index, currentSentence ->
                        if (index != sentenceIndex) currentSentence else currentSentence.copy(
                            tokens = currentSentence.tokens.mapIndexed { index, currentItem ->
                                if (index == tokenIndex) currentItem.copy(exampleSentences = examples) else currentItem
                            },
                        )
                    },
                ),
            )
        } ?: return
        updated.session.sentences.getOrNull(sentenceIndex)?.let { sentence ->
            store.publish(record.id, StudyBuildEvent.SentenceDissected(sentenceIndex, sentence))
        }
    }

    private suspend fun renderWordImage(record: SavedStudySessionRecord, task: StudyBuildTask) {
        val sentenceIndex = requireNotNull(task.element.sentenceIndex)
        val tokenIndex = requireNotNull(task.element.tokenIndex)
        val token = record.session.sentences.getOrNull(sentenceIndex)?.tokens?.getOrNull(tokenIndex) ?: return
        if (!token.imageUrl.isNullOrBlank()) return
        val url = studyService.renderWordImage(
            token = token,
            textLanguageTag = record.textLanguageTag,
            characters = record.session.characters,
        )
        store.update(
            record.id,
            reason = StudyBuildEvent.WordImage(sentenceIndex, tokenIndex, url),
        ) { current ->
            val sentence = current.session.sentences.getOrNull(sentenceIndex) ?: return@update current
            val currentToken = sentence.tokens.getOrNull(tokenIndex) ?: return@update current
            if (!currentToken.imageUrl.isNullOrBlank()) return@update current
            current.copy(
                session = current.session.copy(
                    sentences = current.session.sentences.mapIndexed { index, item ->
                        if (index != sentenceIndex) item else item.copy(
                            tokens = item.tokens.mapIndexed { tokenIndexInSentence, value ->
                                if (tokenIndexInSentence == tokenIndex) value.copy(imageUrl = url) else value
                            },
                        )
                    },
                ),
            )
        }
    }

    private suspend fun designCharacters(record: SavedStudySessionRecord) {
        val characters = studyService.designCast(record.text, record.textLanguageTag)
        store.update(record.id, reason = StudyBuildEvent.Characters(characters)) { current ->
            if (current.session.characters.isNotEmpty()) return@update current
            current.copy(session = current.session.copy(characters = characters))
        }
    }

    private suspend fun renderPortrait(record: SavedStudySessionRecord, task: StudyBuildTask) {
        val characterId = requireNotNull(task.element.characterId)
        val character = record.session.characters.firstOrNull { it.id == characterId } ?: return
        if (!character.imageUrl.isNullOrBlank()) return
        val url = studyService.renderPortrait(character)
        val characters =
            record.session.characters.map { item -> if (item.id == characterId) item.copy(imageUrl = url) else item }
        store.update(record.id, reason = StudyBuildEvent.Characters(characters)) { current ->
            val existing = current.session.characters.firstOrNull { it.id == characterId }
            if (existing == null || !existing.imageUrl.isNullOrBlank()) return@update current
            current.copy(session = current.session.copy(characters = current.session.characters.map { item ->
                if (item.id == characterId) item.copy(imageUrl = url) else item
            }))
        }
    }

    private suspend fun renderSentenceImage(record: SavedStudySessionRecord, task: StudyBuildTask) {
        val index = requireNotNull(task.element.sentenceIndex)
        val sentence = record.session.sentences.getOrNull(index) ?: return
        if (!sentence.imageUrl.isNullOrBlank()) return
        val url = studyService.renderSentenceImage(
            StudySentenceImageRequest(
                sentenceText = sentence.text,
                fullText = record.text,
                sentenceIndex = index,
                sentenceCount = record.session.sentences.size,
                characters = record.session.characters,
            ),
        )
        store.update(record.id, reason = StudyBuildEvent.SentenceImage(index, url)) { current ->
            val existing = current.session.sentences.getOrNull(index) ?: return@update current
            if (!existing.imageUrl.isNullOrBlank()) return@update current
            current.copy(
                session = current.session.copy(
                    sentences = current.session.sentences.mapIndexed { sentenceIndex, item ->
                        if (sentenceIndex == index) item.copy(imageUrl = url) else item
                    },
                ),
            )
        }
    }

    private suspend fun designPlayScenarios(record: SavedStudySessionRecord) {
        val scenarios = studyService.designPlayScenarios(
            StudyPlayScenariosRequest(
                text = record.text,
                textLanguageTag = record.textLanguageTag,
                languageTag = record.languageTag,
                characters = record.session.characters,
            ),
        )
        store.update(record.id, reason = StudyBuildEvent.PlayScenarios(scenarios)) { current ->
            if (current.session.playScenarios.isNotEmpty()) return@update current
            current.copy(session = current.session.copy(playScenarios = scenarios))
        }
    }

    private suspend fun renderPlayCover(record: SavedStudySessionRecord, task: StudyBuildTask) {
        val scenarioId = requireNotNull(task.element.scenarioId)
        val scenario = record.session.playScenarios.firstOrNull { it.id == scenarioId } ?: return
        if (!scenario.imageUrl.isNullOrBlank()) return
        val url = studyService.renderPlayCover(
            StudyPlayScenariosRequest(
                text = record.text,
                textLanguageTag = record.textLanguageTag,
                languageTag = record.languageTag,
                characters = record.session.characters,
            ),
            scenario,
        )
        val scenarios =
            record.session.playScenarios.map { item -> if (item.id == scenarioId) item.copy(imageUrl = url) else item }
        store.update(record.id, reason = StudyBuildEvent.PlayScenarios(scenarios)) { current ->
            val existing = current.session.playScenarios.firstOrNull { it.id == scenarioId }
            if (existing == null || !existing.imageUrl.isNullOrBlank()) return@update current
            current.copy(session = current.session.copy(playScenarios = current.session.playScenarios.map { item ->
                if (item.id == scenarioId) item.copy(imageUrl = url) else item
            }))
        }
    }

    private suspend fun generateExam(record: SavedStudySessionRecord) {
        val scene = studyService.examScene(
            StudyExamSceneRequest(session = record.session, languageTag = record.languageTag),
        )
        store.update(record.id, reason = StudyBuildEvent.ExamScene(scene)) { current ->
            if (current.session.examScene?.isTenRoundExam() == true) return@update current
            current.copy(session = current.session.copy(examScene = scene))
        }
    }

    private fun isComplete(record: SavedStudySessionRecord, task: StudyBuildTask): Boolean = when (task.element.kind) {
        StudyBuildElementKind.PREPARE -> record.buildRequest == null
        StudyBuildElementKind.SKELETON -> record.session.sentences.isNotEmpty()
        StudyBuildElementKind.NAME -> record.name.isNotBlank()
        StudyBuildElementKind.DISSECT -> {
            val sentence = task.element.sentenceIndex?.let(record.session.sentences::getOrNull)
            sentence != null && sentence.tokens.none { it.status == StudyTokenStatus.PENDING }
        }

        StudyBuildElementKind.CHUNKS -> record.session.sentences
            .getOrNull(task.element.sentenceIndex ?: -1)?.chunks?.isNotEmpty() == true

        StudyBuildElementKind.WORD_RECALL_SCENARIOS -> record.session.sentences
            .getOrNull(task.element.sentenceIndex ?: -1)?.tokens
            ?.filter { it.isDissectable }?.all { it.needConversation.isNotEmpty() } == true

        StudyBuildElementKind.EXAMPLE_DISSECT -> {
            val token = record.session.sentences.getOrNull(task.element.sentenceIndex ?: -1)
                ?.tokens?.getOrNull(task.element.tokenIndex ?: -1)
            token != null && token.exampleSentences.all { it.tokens.isNotEmpty() }
        }

        StudyBuildElementKind.WORD_IMAGE -> {
            val token = record.session.sentences.getOrNull(task.element.sentenceIndex ?: -1)
                ?.tokens?.getOrNull(task.element.tokenIndex ?: -1)
            !token?.imageUrl.isNullOrBlank()
        }

        StudyBuildElementKind.CHARACTERS -> record.session.characters.isNotEmpty()
        StudyBuildElementKind.PORTRAIT -> record.session.characters
            .firstOrNull { it.id == task.element.characterId }?.imageUrl.isNullOrBlank().not()

        StudyBuildElementKind.SENTENCE_IMAGE -> !record.session.sentences
            .getOrNull(task.element.sentenceIndex ?: -1)?.imageUrl.isNullOrBlank()

        StudyBuildElementKind.PLAY_SCENARIOS -> record.session.playScenarios.isNotEmpty()
        StudyBuildElementKind.PLAY_COVER -> !record.session.playScenarios
            .firstOrNull { it.id == task.element.scenarioId }?.imageUrl.isNullOrBlank()

        StudyBuildElementKind.EXAM -> record.session.examScene?.isTenRoundExam() == true
    }
}
