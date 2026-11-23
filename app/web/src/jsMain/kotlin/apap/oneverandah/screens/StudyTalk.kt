package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildJournalEntry
import apap.oneverandah.api.StudyBuildJournalOutcome
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTalkAudioKind
import apap.oneverandah.api.StudyTalkWorld
import apap.oneverandah.api.STUDY_TALK_MAX_TURNS_PER_SCENE
import apap.oneverandah.api.STUDY_TALK_MIN_TURNS_PER_SCENE
import apap.oneverandah.api.STUDY_TALK_SCENE_COUNT
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.i18n.StudyTalkStrings
import apap.oneverandah.i18n.UiStrings
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.ref
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.events.Event
import kotlin.js.Date

internal enum class StudyTalkPhase { LISTEN, THINK, REVEAL, COMPLETE }

internal data class StudyTalkProgress(
    val sceneIndex: Int = 0,
    val turnIndex: Int = 0,
    val phase: StudyTalkPhase = StudyTalkPhase.LISTEN,
)

internal fun StudyTalkWorld?.missingTalkBuildElements(): List<StudyBuildElement> {
    if (this == null) return listOf(StudyBuildElement(StudyBuildElementKind.TALK_WORLD))
    return buildList {
        scenes.forEachIndexed { sceneIndex, scene ->
            if (scene.backgroundImageUrl.isNullOrBlank()) {
                add(StudyBuildElement(StudyBuildElementKind.TALK_SCENE_IMAGE, sceneIndex = sceneIndex))
            }
        }
        val usedExpressionIds = scenes.flatMap { scene -> scene.turns.map { it.expressionId } }.toSet()
        expressions.filter { it.id in usedExpressionIds && it.imageUrl.isNullOrBlank() }.forEach { expression ->
            add(StudyBuildElement(StudyBuildElementKind.TALK_EXPRESSION, expressionId = expression.id))
        }
        scenes.forEachIndexed { sceneIndex, scene ->
            scene.turns.forEachIndexed { turnIndex, turn ->
                if (turn.characterAudioUrl.isNullOrBlank()) {
                    add(
                        StudyBuildElement(
                            StudyBuildElementKind.TALK_AUDIO,
                            sceneIndex = sceneIndex,
                            turnIndex = turnIndex,
                            audioKind = StudyTalkAudioKind.CHARACTER_LINE,
                        ),
                    )
                }
                if (turn.replyAudioUrl.isNullOrBlank()) {
                    add(
                        StudyBuildElement(
                            StudyBuildElementKind.TALK_AUDIO,
                            sceneIndex = sceneIndex,
                            turnIndex = turnIndex,
                            audioKind = StudyTalkAudioKind.REPLY,
                        ),
                    )
                }
            }
        }
    }
}

internal fun canRetryStudyTalk(world: StudyTalkWorld?, buildState: StudyBuildState?): Boolean {
    if (buildState?.failed.orEmpty().any { it.kind.isStudyTalkKind() }) return true
    if (world.missingTalkBuildElements().isEmpty()) return false
    val build = buildState ?: return false
    if (build.status == apap.oneverandah.api.StudyBuildStatus.COMPLETE ||
        build.status == apap.oneverandah.api.StudyBuildStatus.PARTIAL
    ) return true
    return build.journal.any { entry ->
        entry.element.kind.isStudyTalkKind() && entry.outcome in setOf(
            StudyBuildJournalOutcome.FAILED,
            StudyBuildJournalOutcome.RETRYING,
            StudyBuildJournalOutcome.REVIVED,
        )
    } || build.inFlight.none { it.kind.isStudyTalkKind() }
}

internal fun retryStudyTalkTurn(progress: StudyTalkProgress): StudyTalkProgress =
    progress.copy(phase = StudyTalkPhase.THINK)

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
    var talkElement by remember { mutableStateOf<HTMLElement?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    val latestFullscreen by rememberUpdatedState(isFullscreen)
    val latestElement by rememberUpdatedState(talkElement)

    DisposableEffect(Unit) {
        val onKey: (Event) -> Unit = { event ->
            val key = event.asDynamic().key as? String
            if ((key == "Escape" || key == "Esc") && latestFullscreen) {
                isFullscreen = false
                exitWatchFullscreen()
            }
        }
        val onFullscreen: (Event) -> Unit = {
            val current = currentFullscreenElement()
            val element = latestElement
            when {
                element != null && current === element -> isFullscreen = true
                latestFullscreen && current == null -> isFullscreen = false
            }
        }
        window.addEventListener("keydown", onKey)
        document.addEventListener("fullscreenchange", onFullscreen)
        document.addEventListener("webkitfullscreenchange", onFullscreen)
        onDispose {
            window.removeEventListener("keydown", onKey)
            document.removeEventListener("fullscreenchange", onFullscreen)
            document.removeEventListener("webkitfullscreenchange", onFullscreen)
            if (latestElement != null && currentFullscreenElement() === latestElement) exitWatchFullscreen()
            stopActiveStudyAudio()
        }
    }

    Div(attrs = {
        classes(Styles.studyTalkBlock, Styles.studyTalkShell)
        if (isFullscreen) classes(Styles.studyTalkFullscreen)
        ref { element ->
            talkElement = element
            onDispose { if (talkElement === element) talkElement = null }
        }
    }) {
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
            Button(attrs = {
                classes(Styles.studyTalkIconButton)
                attr("type", "button")
                attr("aria-label", if (isFullscreen) strings.chatWatchExitFullscreen else strings.chatWatchFullscreen)
                attr("aria-pressed", isFullscreen.toString())
                onClick {
                    if (isFullscreen) {
                        isFullscreen = false
                        exitWatchFullscreen()
                    } else {
                        talkElement?.let(::requestWatchFullscreen)
                    }
                }
            }) { Text(if (isFullscreen) "⤢" else "⛶") }
        }

        if (world == null || !world.hasAllTalkAssets()) {
            val failureReason = buildState.talkFailureReason()
            StudyTalkBuildStatus(world, buildState, failed, canRetry, failureReason, copy, onRetry)
            return@Div
        }

        val scene = world.scenes.getOrNull(sceneIndex) ?: return@Div
        val turn = scene.turns.getOrNull(turnIndex) ?: return@Div
        val speaker = session.characters.firstOrNull { it.id == turn.speakerCharacterId }
        val expression = world.expressions.firstOrNull { it.id == turn.expressionId }
        val backgroundImageUrl = scene.backgroundImageUrl
        val characterImageUrl = expression?.imageUrl
        var backgroundLoaded by remember(backgroundImageUrl) { mutableStateOf(false) }
        var characterLoaded by remember(characterImageUrl) { mutableStateOf(false) }
        var characterEntered by remember(characterImageUrl) { mutableStateOf(false) }
        var characterTapAnimation by remember(characterImageUrl) { mutableStateOf(false) }

        LaunchedEffect(backgroundImageUrl) {
            backgroundLoaded = false
            val imageUrl = backgroundImageUrl ?: return@LaunchedEffect
            val preloader = document.createElement("img") as HTMLImageElement
            preloader.onload = { _: Event -> backgroundLoaded = true }
            preloader.src = imageUrl
            if (preloader.complete) backgroundLoaded = true
        }

        LaunchedEffect(characterImageUrl) {
            characterLoaded = false
            characterEntered = false
            characterTapAnimation = false
            val imageUrl = characterImageUrl ?: return@LaunchedEffect
            val preloader = document.createElement("img") as HTMLImageElement
            preloader.onload = { _: Event -> characterLoaded = true }
            preloader.src = imageUrl
            if (preloader.complete) characterLoaded = true
        }

        LaunchedEffect(phase, turn.id, turn.characterAudioUrl) {
            if (phase == StudyTalkPhase.LISTEN) {
                val audioUrl = turn.characterAudioUrl.orEmpty()
                if (audioUrl.isNotBlank()) {
                    playAudio(audioUrl, restart = true, onStarted = { listenedTurnId = turn.id })
                }
            }
        }

        Div(attrs = {
            classes(Styles.studyTalkStage)
            attr("aria-label", "${scene.title}: ${speaker?.name.orEmpty()}")
        }) {
            if (backgroundLoaded && backgroundImageUrl != null) {
                Img(src = backgroundImageUrl, alt = scene.title, attrs = {
                    classes(Styles.studyTalkBackground)
                    classes(Styles.studyTalkBackgroundVisible)
                    attr("aria-hidden", "true")
                })
            }
            Div(attrs = { classes(Styles.studyTalkVignette) })
            if (characterLoaded && characterImageUrl != null) {
                Button(attrs = {
                    classes(Styles.studyTalkCharacterButton)
                    if (characterTapAnimation) classes(Styles.studyTalkCharacterTapped)
                    attr("type", "button")
                    attr("aria-label", copy.listen)
                    onClick {
                        characterEntered = true
                        characterTapAnimation = true
                        window.setTimeout({ characterTapAnimation = false }, 380)
                        val audioUrl = turn.characterAudioUrl.orEmpty()
                        listenedTurnId = turn.id
                        playAudio(audioUrl, restart = true, onStarted = { listenedTurnId = turn.id })
                    }
                }) {
                    Img(src = characterImageUrl, alt = speaker?.name.orEmpty(), attrs = {
                        classes(Styles.studyTalkCharacter)
                        classes(Styles.studyTalkCharacterVisible)
                        if (!characterEntered) classes(Styles.studyTalkCharacterEntering)
                        ref { element ->
                            val onEnterEnd: (Event) -> Unit = { characterEntered = true }
                            element.addEventListener("animationend", onEnterEnd)
                            onDispose { element.removeEventListener("animationend", onEnterEnd) }
                        }
                    })
                }
            }

            Div(attrs = { classes(Styles.studyTalkHud) }) {
                when (phase) {
                    StudyTalkPhase.LISTEN -> {
                        Div(attrs = { classes(Styles.studyTalkActions) }) {
                            Button(attrs = {
                                classes(Styles.btnPrimary)
                                attr("type", "button")
                                if (listenedTurnId != turn.id) attr("disabled", "")
                                onClick { phase = StudyTalkPhase.THINK }
                            }) { Text(copy.continueToIntention) }
                        }
                    }

                    StudyTalkPhase.THINK -> {
                        Span { Text(copy.intentionLabel) }
                        P(attrs = { classes(Styles.studyTalkIntent) }) { Text(turn.intention) }
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
                        Button(attrs = {
                            classes(Styles.studyTalkAnswer)
                            attr("type", "button")
                            onClick { playAudio(turn.replyAudioUrl.orEmpty(), restart = true) }
                        }) { Text(turn.reply) }
                        Div(attrs = { classes(Styles.studyTalkActions) }) {
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
    }
}

@Composable
private fun StudyTalkBuildStatus(
    world: StudyTalkWorld?,
    buildState: StudyBuildState?,
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
        } else {
            StudyTalkBuildHeader(
                "progress_activity",
                if (progress.scriptReady) copy.loading else copy.scriptLoading,
            )
        }
        if (canRetry) StudyTalkRetryButton(copy.retry, onRetry)
        StudyTalkProgressBar(progress, copy)
        StudyTalkPreparationSteps(world.studyTalkPreparationSteps(buildState), copy)
        StudyTalkBuildJournal(buildState?.journal.orEmpty(), copy)
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

internal enum class StudyTalkPreparationStatus { COMPLETE, RUNNING, FAILED, UPCOMING }

internal enum class StudyTalkPreparationKind { SCRIPT, SCENE_BACKGROUND, EXPRESSIONS, SCENE_AUDIO }

internal data class StudyTalkPreparationStep(
    val kind: StudyTalkPreparationKind,
    val sceneIndex: Int? = null,
    val status: StudyTalkPreparationStatus,
)

internal fun StudyTalkWorld?.studyTalkPreparationSteps(buildState: StudyBuildState?): List<StudyTalkPreparationStep> {
    fun isRunning(predicate: (StudyBuildElement) -> Boolean) = buildState?.inFlight.orEmpty().any(predicate)
    fun hasFailed(predicate: (StudyBuildElement) -> Boolean) = buildState?.failed.orEmpty().any(predicate)
    fun status(ready: Boolean, running: Boolean, failed: Boolean) = when {
        ready -> StudyTalkPreparationStatus.COMPLETE
        failed -> StudyTalkPreparationStatus.FAILED
        running -> StudyTalkPreparationStatus.RUNNING
        else -> StudyTalkPreparationStatus.UPCOMING
    }

    val scriptReady = this?.isValidTalkScript() == true
    val steps = mutableListOf(
        StudyTalkPreparationStep(
            StudyTalkPreparationKind.SCRIPT,
            status = status(
                ready = scriptReady,
                running = isRunning { it.kind == StudyBuildElementKind.TALK_WORLD },
                failed = hasFailed { it.kind == StudyBuildElementKind.TALK_WORLD },
            ),
        ),
    )
    repeat(STUDY_TALK_SCENE_COUNT) { sceneIndex ->
        val scene = this?.scenes?.getOrNull(sceneIndex)
        steps += StudyTalkPreparationStep(
            StudyTalkPreparationKind.SCENE_BACKGROUND,
            sceneIndex,
            status(
                ready = !scene?.backgroundImageUrl.isNullOrBlank(),
                running = isRunning { it.kind == StudyBuildElementKind.TALK_SCENE_IMAGE && it.sceneIndex == sceneIndex },
                failed = hasFailed { it.kind == StudyBuildElementKind.TALK_SCENE_IMAGE && it.sceneIndex == sceneIndex },
            ),
        )
    }
    val usedExpressionIds = this?.scenes.orEmpty().flatMap { scene -> scene.turns.map { it.expressionId } }.toSet()
    val expressionById = this?.expressions.orEmpty().associateBy { it.id }
    steps += StudyTalkPreparationStep(
        StudyTalkPreparationKind.EXPRESSIONS,
        status = status(
            ready = scriptReady && usedExpressionIds.isNotEmpty() &&
                    usedExpressionIds.all { !expressionById[it]?.imageUrl.isNullOrBlank() },
            running = isRunning { it.kind == StudyBuildElementKind.TALK_EXPRESSION },
            failed = hasFailed { it.kind == StudyBuildElementKind.TALK_EXPRESSION },
        ),
    )
    repeat(STUDY_TALK_SCENE_COUNT) { sceneIndex ->
        val scene = this?.scenes?.getOrNull(sceneIndex)
        steps += StudyTalkPreparationStep(
            StudyTalkPreparationKind.SCENE_AUDIO,
            sceneIndex,
            status(
                ready = scene != null && scene.turns.isNotEmpty() && scene.turns.all { turn ->
                    !turn.characterAudioUrl.isNullOrBlank() && !turn.replyAudioUrl.isNullOrBlank()
                },
                running = isRunning { it.kind == StudyBuildElementKind.TALK_AUDIO && it.sceneIndex == sceneIndex },
                failed = hasFailed { it.kind == StudyBuildElementKind.TALK_AUDIO && it.sceneIndex == sceneIndex },
            ),
        )
    }
    return steps
}

@Composable
private fun StudyTalkPreparationSteps(steps: List<StudyTalkPreparationStep>, copy: StudyTalkStrings) {
    Div(attrs = { classes(Styles.studyTalkPreparationSteps) }) {
        steps.forEach { step ->
            val labelText = when (step.kind) {
                StudyTalkPreparationKind.SCRIPT -> copy.scriptStep
                StudyTalkPreparationKind.SCENE_BACKGROUND -> "${copy.sceneBackgroundStep} ${(step.sceneIndex ?: 0) + 1}"
                StudyTalkPreparationKind.EXPRESSIONS -> copy.expressionStep
                StudyTalkPreparationKind.SCENE_AUDIO -> "${copy.sceneAudioStep} ${(step.sceneIndex ?: 0) + 1}"
            }
            val label = when (step.status) {
                StudyTalkPreparationStatus.COMPLETE -> copy.stepCompleted
                StudyTalkPreparationStatus.RUNNING -> copy.stepRunning
                StudyTalkPreparationStatus.FAILED -> copy.journalFailed
                StudyTalkPreparationStatus.UPCOMING -> copy.stepUpcoming
            }
            val icon = when (step.status) {
                StudyTalkPreparationStatus.COMPLETE -> "check_circle"
                StudyTalkPreparationStatus.RUNNING -> "progress_activity"
                StudyTalkPreparationStatus.FAILED -> "error"
                StudyTalkPreparationStatus.UPCOMING -> "radio_button_unchecked"
            }
            Div(attrs = {
                classes(Styles.studyTalkPreparationStep)
                classes(when (step.status) {
                    StudyTalkPreparationStatus.COMPLETE -> Styles.studyTalkPreparationComplete
                    StudyTalkPreparationStatus.RUNNING -> Styles.studyTalkPreparationRunning
                    StudyTalkPreparationStatus.FAILED -> Styles.studyTalkPreparationFailed
                    StudyTalkPreparationStatus.UPCOMING -> Styles.studyTalkPreparationUpcoming
                })
            }) {
                MaterialSymbol(icon)
                Span { Text(labelText) }
                Span(attrs = { classes(Styles.studyTalkPreparationStepStatus) }) { Text(label) }
            }
        }
    }
}

@Composable
private fun StudyTalkBuildJournal(entries: List<StudyBuildJournalEntry>, copy: StudyTalkStrings) {
    Div(attrs = { classes(Styles.studyTalkJournal) }) {
        P(attrs = { classes(Styles.studyTalkJournalTitle) }) { Text(copy.journalTitle) }
        if (entries.isEmpty()) {
            P(attrs = { classes(Styles.studyTalkJournalEmpty) }) { Text(copy.journalEmpty) }
        } else {
            Div(attrs = { classes(Styles.studyTalkJournalEntries) }) {
                entries.asReversed().forEach { entry ->
                    val timestamp = remember(entry.timestamp) { Date(entry.timestamp.toDouble()).toLocaleTimeString() }
                    Div(attrs = { classes(Styles.studyTalkJournalEntry) }) {
                        Div(attrs = { classes(Styles.studyTalkJournalEntryHeading) }) {
                            Span { Text(entry.element.studyTalkJournalLabel(copy)) }
                            Span(attrs = { classes(Styles.studyTalkJournalOutcome) }) {
                                Text(entry.outcome.studyTalkJournalLabel(copy))
                            }
                        }
                        Div(attrs = { classes(Styles.studyTalkJournalMetadata) }) {
                            Span { Text(timestamp) }
                            if (entry.attempt > 0) Span { Text(copy.journalAttempt(entry.attempt)) }
                            entry.elapsedMs?.let { elapsed -> Span { Text(copy.journalElapsed(elapsed)) } }
                        }
                        entry.detail?.takeIf(String::isNotBlank)?.let { detail ->
                            P(attrs = { classes(Styles.studyTalkJournalDetail) }) { Text(detail) }
                        }
                    }
                }
            }
        }
    }
}

private fun StudyBuildElement.studyTalkJournalLabel(copy: StudyTalkStrings): String = when (kind) {
    StudyBuildElementKind.TALK_WORLD -> copy.scriptStep
    StudyBuildElementKind.TALK_SCENE_IMAGE -> "${copy.sceneBackgroundStep} ${(sceneIndex ?: 0) + 1}"
    StudyBuildElementKind.TALK_EXPRESSION -> "${copy.expressionStep}: ${expressionId.orEmpty()}"
    StudyBuildElementKind.TALK_AUDIO -> {
        val audioLabel = when (audioKind) {
            StudyTalkAudioKind.CHARACTER_LINE -> copy.characterLineAudio
            StudyTalkAudioKind.REPLY -> copy.learnerReplyAudio
            null -> copy.sceneAudioStep
        }
        "${copy.sceneAudioStep} ${(sceneIndex ?: 0) + 1} · ${turnIndex?.plus(1) ?: "?"} · $audioLabel"
    }
    else -> kind.name.replace('_', ' ').lowercase()
}

private fun StudyBuildJournalOutcome.studyTalkJournalLabel(copy: StudyTalkStrings): String = when (this) {
    StudyBuildJournalOutcome.STARTED -> copy.journalStarted
    StudyBuildJournalOutcome.SUCCEEDED -> copy.journalSucceeded
    StudyBuildJournalOutcome.FAILED -> copy.journalFailed
    StudyBuildJournalOutcome.RETRYING -> copy.journalRetrying
    StudyBuildJournalOutcome.REVIVED -> copy.journalRevived
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