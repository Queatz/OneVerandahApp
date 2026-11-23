package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTalkWorld
import apap.oneverandah.api.STUDY_TALK_MAX_TURNS_PER_SCENE
import apap.oneverandah.api.STUDY_TALK_MIN_TURNS_PER_SCENE
import apap.oneverandah.api.STUDY_TALK_SCENE_COUNT
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.i18n.StudyTalkStrings
import apap.oneverandah.i18n.UiStrings
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

internal enum class StudyTalkPhase { LISTEN, INTENTION, SPEAK, REVEAL, COMPLETE }

internal data class StudyTalkProgress(
    val sceneIndex: Int = 0,
    val turnIndex: Int = 0,
    val phase: StudyTalkPhase = StudyTalkPhase.LISTEN,
)

internal fun retryStudyTalkTurn(progress: StudyTalkProgress): StudyTalkProgress =
    progress.copy(phase = StudyTalkPhase.INTENTION)

internal fun advanceStudyTalkTurn(progress: StudyTalkProgress, world: StudyTalkWorld): StudyTalkProgress {
    val scene = world.scenes.getOrNull(progress.sceneIndex) ?: return progress.copy(phase = StudyTalkPhase.COMPLETE)
    return when {
        progress.turnIndex < scene.turns.lastIndex -> progress.copy(
            turnIndex = progress.turnIndex + 1,
            phase = StudyTalkPhase.LISTEN,
        )
        progress.sceneIndex < world.scenes.lastIndex -> progress.copy(
            sceneIndex = progress.sceneIndex + 1,
            turnIndex = 0,
            phase = StudyTalkPhase.LISTEN,
        )
        else -> progress.copy(phase = StudyTalkPhase.COMPLETE)
    }
}

@Composable
internal fun StudyTalkTab(
    session: StudySessionResponse,
    buildState: StudyBuildState?,
    failed: Boolean,
    canRetry: Boolean,
    strings: UiStrings,
    onRetry: () -> Unit,
) {
    val copy = strings.chatTalk
    val world = session.talkWorld
    var sceneIndex by remember(session.text, session.textLanguageTag) { mutableStateOf(0) }
    var turnIndex by remember(session.text, session.textLanguageTag) { mutableStateOf(0) }
    var phase by remember(session.text, session.textLanguageTag) { mutableStateOf(StudyTalkPhase.LISTEN) }
    var listenedTurnId by remember(session.text, session.textLanguageTag) { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { stopActiveStudyAudio() }
    }

    Div(attrs = { classes(Styles.studyTalkBlock) }) {
        Div(attrs = { classes(Styles.studyTalkHeader) }) {
            P(attrs = { classes(Styles.studyTalkTitle) }) { Text(copy.title) }
            world?.scenes?.getOrNull(sceneIndex)?.let { scene ->
                P(attrs = { classes(Styles.studyTalkProgress) }) {
                    Text(scene.title)
                    Text(" · ")
                    Text(copy.sceneProgress(sceneIndex + 1, world.scenes.size))
                    Text(" · ")
                    Text(copy.turnProgress(turnIndex + 1, scene.turns.size))
                }
            }
        }

        if (world == null || !world.hasAllTalkAssets()) {
            val failureReason = buildState.talkFailureReason()
            StudyTalkBuildStatus(world, failed, canRetry, failureReason, copy, onRetry)
            return@Div
        }

        val scene = world.scenes.getOrNull(sceneIndex) ?: return@Div
        val turn = scene.turns.getOrNull(turnIndex) ?: return@Div
        val speaker = session.characters.firstOrNull { it.id == turn.speakerCharacterId }
        val expression = world.expressions.firstOrNull { it.id == turn.expressionId }

        Div(attrs = {
            classes(Styles.studyTalkStage)
            attr("aria-label", "${scene.title}: ${speaker?.name.orEmpty()}")
        }) {
            Img(src = requireNotNull(scene.backgroundImageUrl), alt = scene.title, attrs = {
                classes(Styles.studyTalkBackground)
                attr("aria-hidden", "true")
            })
            Div(attrs = { classes(Styles.studyTalkVignette) })
            expression?.imageUrl?.let { imageUrl ->
                Img(src = imageUrl, alt = speaker?.name.orEmpty(), attrs = {
                    classes(Styles.studyTalkCharacter)
                })
            }
            if (phase == StudyTalkPhase.LISTEN) {
                Div(attrs = { classes(Styles.studyTalkIndicator) }) {
                    MaterialSymbol("graphic_eq")
                    Span { Text(copy.characterSpeaking) }
                }
            }
        }

        when (phase) {
            StudyTalkPhase.LISTEN -> {
                Div(attrs = { classes(Styles.studyTalkActions) }) {
                    Button(attrs = {
                        classes(Styles.savedItemAction)
                        attr("type", "button")
                        onClick {
                            val audioUrl = turn.characterAudioUrl.orEmpty()
                            if (isStudyAudioPlaying(audioUrl)) stopActiveStudyAudio()
                            else playAudio(audioUrl, restart = true)
                            listenedTurnId = turn.id
                        }
                    }) {
                        MaterialSymbol(if (isStudyAudioPlaying(turn.characterAudioUrl)) "pause" else "volume_up")
                        Text(copy.listen)
                    }
                    Button(attrs = {
                        classes(Styles.btnPrimary)
                        attr("type", "button")
                        if (listenedTurnId != turn.id) attr("disabled", "")
                        onClick { phase = StudyTalkPhase.INTENTION }
                    }) { Text(copy.continueToIntention) }
                }
            }

            StudyTalkPhase.INTENTION -> {
                Span { Text(copy.intentionLabel) }
                P(attrs = { classes(Styles.studyTalkIntent) }) { Text(turn.intention) }
                Div(attrs = { classes(Styles.studyTalkActions) }) {
                    Button(attrs = {
                        classes(Styles.btnPrimary)
                        attr("type", "button")
                        onClick { phase = StudyTalkPhase.SPEAK }
                    }) { Text(copy.continueToIntention) }
                }
            }

            StudyTalkPhase.SPEAK -> {
                P(attrs = { classes(Styles.studyTalkPrompt) }) { Text(copy.speakPrompt) }
                Div(attrs = { classes(Styles.studyTalkActions) }) {
                    Button(attrs = {
                        classes(Styles.btnPrimary)
                        attr("type", "button")
                        onClick {
                            phase = StudyTalkPhase.REVEAL
                            playAudio(turn.replyAudioUrl.orEmpty(), restart = true)
                        }
                    }) {
                        MaterialSymbol("visibility")
                        Text(copy.reveal)
                    }
                }
            }

            StudyTalkPhase.REVEAL -> {
                Span { Text(copy.answerLabel) }
                P(attrs = { classes(Styles.studyTalkAnswer) }) { Text(turn.reply) }
                Div(attrs = { classes(Styles.studyTalkActions) }) {
                    Button(attrs = {
                        classes(Styles.savedItemAction)
                        attr("type", "button")
                        onClick {
                            val audioUrl = turn.replyAudioUrl.orEmpty()
                            if (isStudyAudioPlaying(audioUrl)) stopActiveStudyAudio()
                            else playAudio(audioUrl, restart = true)
                        }
                    }) {
                        MaterialSymbol("volume_up")
                        Text(copy.replayAnswer)
                    }
                    Button(attrs = {
                        classes(Styles.savedItemAction)
                        attr("type", "button")
                        onClick {
                            val retry = retryStudyTalkTurn(StudyTalkProgress(sceneIndex, turnIndex, phase))
                            phase = retry.phase
                        }
                    }) { Text(copy.tryAgain) }
                    Button(attrs = {
                        classes(Styles.btnPrimary)
                        attr("type", "button")
                        onClick {
                            val next = advanceStudyTalkTurn(StudyTalkProgress(sceneIndex, turnIndex, phase), world)
                            sceneIndex = next.sceneIndex
                            turnIndex = next.turnIndex
                            phase = next.phase
                        }
                    }) { Text(copy.good) }
                }
            }

            StudyTalkPhase.COMPLETE -> {
                Div(attrs = { classes(Styles.studyTalkComplete) }) {
                    MaterialSymbol("auto_awesome")
                    P { Text(copy.complete) }
                    Button(attrs = {
                        classes(Styles.btnPrimary)
                        attr("type", "button")
                        onClick {
                            sceneIndex = 0
                            turnIndex = 0
                            phase = StudyTalkPhase.LISTEN
                        }
                    }) {
                        MaterialSymbol("replay")
                        Text(copy.restart)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyTalkBuildStatus(
    world: StudyTalkWorld?,
    failed: Boolean,
    canRetry: Boolean,
    failureReason: String?,
    copy: StudyTalkStrings,
    onRetry: () -> Unit,
) {
    val progress = world.studyTalkBuildProgress()
    Div(attrs = {
        classes(Styles.studyTalkStatus)
        attr("role", if (failed) "alert" else "status")
        attr("aria-live", "polite")
    }) {
        if (failed) {
            StudyTalkBuildHeader("error", copy.error)
            Span { Text(copy.failureDetails(failureReason ?: copy.incompleteFailure)) }
            if (canRetry) StudyTalkRetryButton(copy.retry, onRetry)
        } else {
            StudyTalkBuildHeader(
                "progress_activity",
                if (progress.scriptReady) copy.loading else copy.scriptLoading,
            )
        }
        StudyTalkProgressBar(progress, copy)
    }
}

@Composable
private fun StudyTalkBuildHeader(icon: String, label: String) {
    Div(attrs = { classes(Styles.studyTalkBuildHeader) }) {
        MaterialSymbol(icon)
        Span { Text(label) }
    }
}

@Composable
private fun StudyTalkRetryButton(label: String, onRetry: () -> Unit) {
    Button(attrs = {
        classes(Styles.savedItemAction)
        attr("type", "button")
        onClick { onRetry() }
    }) {
        MaterialSymbol("refresh")
        Text(label)
    }
}

@Composable
private fun StudyTalkProgressBar(progress: StudyTalkBuildProgress, copy: StudyTalkStrings) {
    Div(attrs = { classes(Styles.studyTalkBuildProgress) }) {
        Div(attrs = {
            classes(Styles.studyTalkProgressBar)
            attr("role", "progressbar")
            attr("aria-label", copy.progressBarLabel)
            attr("aria-valuemin", "0")
            attr("aria-valuemax", "100")
            attr("aria-valuenow", progress.percent.toString())
        }) {
            Div(attrs = {
                classes(Styles.studyTalkProgressFill)
                attr("style", "width: ${progress.percent}%")
            })
        }
        Span { Text(copy.stepCount(progress.completedSteps, progress.totalSteps)) }
        if (progress.hasWorld) StudyTalkProgressBreakdown(progress.assets, copy)
    }
}

@Composable
private fun StudyTalkProgressBreakdown(progress: StudyTalkAssetProgress, copy: StudyTalkStrings) {
    Div(attrs = { classes(Styles.studyTalkProgressBreakdown) }) {
        Span { Text(copy.sceneBackgroundProgress(progress.backgrounds.ready, progress.backgrounds.total)) }
        Span { Text(copy.expressionProgress(progress.expressions.ready, progress.expressions.total)) }
        Span { Text(copy.audioProgress(progress.audio.ready, progress.audio.total)) }
    }
}

internal fun StudyBuildState?.talkFailureReason(): String? =
    this?.let { state ->
        state.failed.firstOrNull { it.kind.isStudyTalkKind() }?.let { state.failureReasons[it.key] }
    }

internal fun apap.oneverandah.api.StudyBuildElementKind.isStudyTalkKind(): Boolean = when (this) {
    apap.oneverandah.api.StudyBuildElementKind.CHARACTERS,
    apap.oneverandah.api.StudyBuildElementKind.PORTRAIT,
    apap.oneverandah.api.StudyBuildElementKind.TALK_WORLD,
    apap.oneverandah.api.StudyBuildElementKind.TALK_SCENE_IMAGE,
    apap.oneverandah.api.StudyBuildElementKind.TALK_EXPRESSION,
    apap.oneverandah.api.StudyBuildElementKind.TALK_AUDIO -> true

    else -> false
}

internal fun StudyTalkWorld.hasAllTalkAssets(): Boolean =
    studyTalkAssetProgress()?.let { it.ready == it.total } == true

internal data class StudyTalkAssetGroupProgress(val ready: Int, val total: Int)

internal data class StudyTalkAssetProgress(
    val backgrounds: StudyTalkAssetGroupProgress,
    val expressions: StudyTalkAssetGroupProgress,
    val audio: StudyTalkAssetGroupProgress,
) {
    val ready: Int get() = backgrounds.ready + expressions.ready + audio.ready
    val total: Int get() = backgrounds.total + expressions.total + audio.total
    val percent: Int get() = (ready * 100 / total).coerceIn(0, 100)
}

internal const val STUDY_TALK_PREPARATION_STEP_COUNT = 1 + STUDY_TALK_SCENE_COUNT + 1 + STUDY_TALK_SCENE_COUNT

internal data class StudyTalkBuildProgress(
    val completedSteps: Int,
    val totalSteps: Int = STUDY_TALK_PREPARATION_STEP_COUNT,
    val scriptReady: Boolean,
    val hasWorld: Boolean,
    val assets: StudyTalkAssetProgress,
    val audioScenesReady: Int,
) {
    val percent: Int get() = (completedSteps * 100 / totalSteps).coerceIn(0, 100)
}

internal fun StudyTalkWorld?.studyTalkBuildProgress(): StudyTalkBuildProgress {
    val scriptReady = this?.isValidTalkScript() == true
    val assetProgress = this?.let { world ->
        StudyTalkAssetProgress(
            backgrounds = StudyTalkAssetGroupProgress(
                ready = world.scenes.take(STUDY_TALK_SCENE_COUNT).count { !it.backgroundImageUrl.isNullOrBlank() },
                total = STUDY_TALK_SCENE_COUNT,
            ),
            expressions = run {
                val usedIds = world.scenes.flatMap { it.turns }.map { it.expressionId }.toSet()
                val byId = world.expressions.associateBy { it.id }
                StudyTalkAssetGroupProgress(
                    ready = usedIds.count { !byId[it]?.imageUrl.isNullOrBlank() },
                    total = usedIds.size,
                )
            },
            audio = world.scenes.flatMap { it.turns }.let { turns ->
                StudyTalkAssetGroupProgress(
                    ready = turns.sumOf { turn ->
                        listOf(turn.characterAudioUrl, turn.replyAudioUrl).count { !it.isNullOrBlank() }
                    },
                    total = turns.size * 2,
                )
            },
        )
    } ?: StudyTalkAssetProgress(
        backgrounds = StudyTalkAssetGroupProgress(0, STUDY_TALK_SCENE_COUNT),
        expressions = StudyTalkAssetGroupProgress(0, 0),
        audio = StudyTalkAssetGroupProgress(0, 0),
    )
    val audioScenesReady = this?.scenes.orEmpty().take(STUDY_TALK_SCENE_COUNT).count { scene ->
        scene.turns.isNotEmpty() && scene.turns.all { turn ->
            !turn.characterAudioUrl.isNullOrBlank() && !turn.replyAudioUrl.isNullOrBlank()
        }
    }
    val completedSteps = (if (scriptReady) 1 else 0) + assetProgress.backgrounds.ready +
        (if (scriptReady && assetProgress.expressions.ready == assetProgress.expressions.total) 1 else 0) +
        audioScenesReady
    return StudyTalkBuildProgress(
        completedSteps = completedSteps.coerceAtMost(STUDY_TALK_PREPARATION_STEP_COUNT),
        scriptReady = scriptReady,
        hasWorld = this != null,
        assets = assetProgress,
        audioScenesReady = audioScenesReady,
    )
}

internal fun StudyTalkWorld.studyTalkAssetProgress(): StudyTalkAssetProgress? {
    if (!isValidTalkScript()) return null
    val usedExpressionIds = scenes.flatMap { it.turns }.map { it.expressionId }.toSet()
    val requiredExpressions = expressions.filter { it.id in usedExpressionIds }
    val turns = scenes.flatMap { it.turns }

    return StudyTalkAssetProgress(
        backgrounds = StudyTalkAssetGroupProgress(
            ready = scenes.count { !it.backgroundImageUrl.isNullOrBlank() },
            total = scenes.size,
        ),
        expressions = StudyTalkAssetGroupProgress(
            ready = requiredExpressions.count { !it.imageUrl.isNullOrBlank() },
            total = requiredExpressions.size,
        ),
        audio = StudyTalkAssetGroupProgress(
            ready = turns.sumOf { turn ->
                listOf(turn.characterAudioUrl, turn.replyAudioUrl).count { !it.isNullOrBlank() }
            },
            total = turns.size * 2,
        ),
    )
}

private fun StudyTalkWorld.isValidTalkScript(): Boolean {
    if (scenes.size != STUDY_TALK_SCENE_COUNT) return false
    if (scenes.any { it.id.isBlank() || it.title.isBlank() || it.description.isBlank() }) return false
    if (scenes.map { it.id }.distinct().size != scenes.size) return false
    if (scenes.any { it.turns.size !in STUDY_TALK_MIN_TURNS_PER_SCENE..STUDY_TALK_MAX_TURNS_PER_SCENE }) return false
    val turns = scenes.flatMap { it.turns }
    if (turns.map { it.id }.any(String::isBlank) || turns.map { it.id }.distinct().size != turns.size) return false
    val usedExpressionIds = turns.map { it.expressionId }.toSet()
    if (usedExpressionIds.any(String::isBlank)) return false
    val usedExpressions = expressions.filter { it.id in usedExpressionIds }
    if (usedExpressionIds.isEmpty() || usedExpressions.size != usedExpressionIds.size) return false
    val expressionsById = usedExpressions.associateBy { it.id }
    return turns.all { turn ->
        turn.speakerCharacterId.isNotBlank() && turn.expressionId.isNotBlank() &&
            turn.characterLine.isNotBlank() && turn.intention.isNotBlank() && turn.reply.isNotBlank() &&
            expressionsById[turn.expressionId]?.characterId == turn.speakerCharacterId
    }
}