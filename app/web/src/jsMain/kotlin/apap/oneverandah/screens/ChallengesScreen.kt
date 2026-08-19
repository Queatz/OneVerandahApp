package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.ChallengeApi
import apap.oneverandah.api.ChallengeApiException
import apap.oneverandah.api.firstFileOrNull
import apap.oneverandah.api.prepareChallengeVideo
import apap.oneverandah.challenges.ChallengeCard
import apap.oneverandah.challenges.ChallengeLogic
import apap.oneverandah.challenges.ChallengeSubmissionView
import apap.oneverandah.challenges.ChallengesFeedResponse
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.CoverImage
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import apap.oneverandah.state.Toaster
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
fun ChallengesScreen(
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    onOpenChallenge: (String) -> Unit,
    enterChallengeId: String? = null,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf(ChallengesFeedResponse()) }
    var loading by remember { mutableStateOf(true) }
    var composerOpen by remember { mutableStateOf(false) }
    var enterFor by remember { mutableStateOf<String?>(enterChallengeId) }

    suspend fun refresh() {
        feed = ChallengeApi.fetchFeed()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        title = strings.challengesTitle,
        onBack = onBack,
        onHome = onHome,
        scrollKey = "challenges",
        lockScroll = composerOpen,
        overlay = {
            ChallengeUploadDialog(
                visible = composerOpen || enterFor != null,
                challengeId = enterFor,
                onDismiss = {
                    composerOpen = false
                    enterFor = null
                },
                onUploaded = {
                    composerOpen = false
                    enterFor = null
                    scope.launch { refresh() }
                },
            )
        },
    ) {
        P(attrs = { classes(Styles.homePrompt, Styles.homePromptLarge) }) {
            Text(strings.challengesPrompt)
        }

        when {
            loading -> P { Text(strings.challengesLoading) }
            else -> {
                if (feed.inReview.isNotEmpty()) {
                    P(attrs = { classes(Styles.sectionTitle) }) { Text(strings.challengesInReview) }
                    Div(attrs = { classes(Styles.challengesReviewList) }) {
                        feed.inReview.forEach { item ->
                            InReviewCard(item)
                        }
                    }
                }

                Div(attrs = { classes(Styles.storiesGrid) }) {
                    Button(attrs = {
                        classes(Styles.challengeStartCard)
                        attr("type", "button")
                        onClick { composerOpen = true }
                    }) {
                        MaterialSymbol("add")
                        Span(attrs = { classes(Styles.storyCoverTitle) }) { Text(strings.challengesStartNew) }
                    }
                    feed.challenges.forEach { card ->
                        ChallengeCardButton(card) { onOpenChallenge(card.id) }
                    }
                }

                if (feed.challenges.isEmpty() && feed.inReview.isEmpty()) {
                    P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.challengesEmpty) }
                }
            }
        }
    }
}

@Composable
private fun ChallengeCardButton(card: ChallengeCard, onClick: () -> Unit) {
    val strings = Localization.strings
    val title = card.name.ifBlank { strings.challengesUntitled }
    Button(attrs = {
        classes(Styles.storyCoverCard)
        attr("type", "button")
        onClick { onClick() }
    }) {
        if (!card.coverImageUrl.isNullOrBlank()) {
            Div(attrs = { classes(Styles.challengeCover) }) {
                CoverImage(card.coverImageUrl!!)
            }
        } else {
            Div(attrs = { classes(Styles.storyCoverPlaceholder) }) {
                MaterialSymbol("emoji_events")
            }
        }
        Span(attrs = { classes(Styles.storyCoverTitle) }) { Text(title) }
        Span(attrs = { classes(Styles.storyCoverMeta) }) {
            Text("${card.topVoteCount} · ${strings.challengesVotes}")
        }
    }
}

@Composable
private fun InReviewCard(item: ChallengeSubmissionView) {
    val strings = Localization.strings
    val status = when (item.status) {
        ChallengeLogic.STATUS_PROCESSING -> strings.challengesProcessing
        ChallengeLogic.STATUS_FAILED -> item.error ?: strings.challengesFailed
        else -> strings.challengesInReview
    }
    Div(attrs = { classes(Styles.challengeReviewCard) }) {
        Div(attrs = { classes(Styles.challengeReviewThumb) }) {
            if (!item.thumbnailUrl.isNullOrBlank()) {
                CoverImage(item.thumbnailUrl!!)
            } else {
                MaterialSymbol("hourglass_top")
            }
        }
        Div {
            Span(attrs = { classes(Styles.storyCoverTitle) }) {
                Text(item.challengeName.ifBlank { strings.challengesUntitled })
            }
            P(attrs = { classes(Styles.homePrompt) }) { Text(status) }
        }
    }
}

@Composable
fun ChallengeUploadDialog(
    visible: Boolean,
    challengeId: String?,
    onDismiss: () -> Unit,
    onUploaded: () -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = { if (!busy) onDismiss() },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) {
                Text(if (challengeId == null) strings.challengesStartNew else strings.challengesEnter)
            }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            P(attrs = { classes(Styles.homePrompt) }) { Text(strings.challengesRulesTitle) }
            P { Text("• ${strings.challengesRulePlace}") }
            P { Text("• ${strings.challengesRuleFamily}") }
            P { Text("• ${strings.challengesRuleLength}") }

            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.challengesYourName) }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    attr("placeholder", strings.challengesYourNamePlaceholder)
                    value(name)
                    onInput { name = it.value }
                }
            }
            Label(attrs = { classes(Styles.btnText) }) {
                Text(file?.name ?: strings.challengesPickVideo)
                Input(type = InputType.File) {
                    attr("accept", "video/*")
                    style { property("display", "none") }
                    onChange {
                        file = (it.nativeEvent.target as? HTMLInputElement)?.firstFileOrNull()
                    }
                }
            }
            if (busy) {
                Div(attrs = { classes(Styles.challengeProgressTrack) }) {
                    Div(attrs = {
                        classes(Styles.challengeProgressFill)
                        style { property("width", "${progress.coerceIn(0, 100)}%") }
                    })
                }
                P(attrs = { classes(Styles.homePrompt) }) { Text("${strings.challengesUploading} $progress%") }
            }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                if (busy) attr("disabled", "")
                onClick { if (!busy) onDismiss() }
            }) { Text(strings.close) }
            PrimaryButton(
                label = strings.challengesSubmit,
                icon = "upload",
                enabled = !busy && name.isNotBlank() && file != null,
            ) {
                val picked = file ?: return@PrimaryButton
                scope.launch {
                    busy = true
                    progress = 2
                    try {
                        val ready = prepareChallengeVideo(picked) { progress = it }
                        ChallengeApi.upload(
                            name = name.trim(),
                            file = ready,
                            challengeId = challengeId,
                            onProgress = { progress = it },
                        )
                        Toaster.show(strings.challengesInReview)
                        name = ""
                        file = null
                        onUploaded()
                    } catch (e: ChallengeApiException) {
                        Toaster.show(e.message ?: strings.challengesSubmitError)
                    } catch (t: Throwable) {
                        Toaster.show(t.message ?: strings.challengesSubmitError)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}
