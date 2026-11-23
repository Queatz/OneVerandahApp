package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyTalkWorld
import apap.oneverandah.components.MaterialSymbol
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
            Div(attrs = {
                classes(Styles.studyTalkStatus)
                attr("role", if (failed) "alert" else "status")
                attr("aria-live", "polite")
            }) {
                if (failed) {
                    MaterialSymbol("error")
                    Span { Text(copy.error) }
                    if (canRetry) {
                        Button(attrs = {
                            classes(Styles.savedItemAction)
                            attr("type", "button")
                            onClick { onRetry() }
                        }) {
                            MaterialSymbol("refresh")
                            Text(copy.retry)
                        }
                    }
                } else {
                    MaterialSymbol("progress_activity")
                    Span { Text(copy.loading) }
                }
            }
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

internal fun StudyTalkWorld.hasAllTalkAssets(): Boolean =
    scenes.size == 6 &&
        scenes.all { scene ->
            !scene.backgroundImageUrl.isNullOrBlank() && scene.turns.isNotEmpty() && scene.turns.all { turn ->
                !turn.characterAudioUrl.isNullOrBlank() && !turn.replyAudioUrl.isNullOrBlank()
            }
        } && expressions.isNotEmpty() && expressions.all { !it.imageUrl.isNullOrBlank() }