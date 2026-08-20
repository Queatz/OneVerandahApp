package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.ChallengeApi
import apap.oneverandah.challenges.AdminChallengeView
import apap.oneverandah.challenges.AdminChallengesResponse
import apap.oneverandah.challenges.ChallengeLogic
import apap.oneverandah.challenges.ChallengeSubmissionView
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.i18n.Localization
import apap.oneverandah.state.Toaster
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement

@Composable
fun AdminChallengesPanel(
    data: AdminChallengesResponse,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<AdminChallengeView?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var pendingDeleteChallenge by remember { mutableStateOf<AdminChallengeView?>(null) }
    var pendingDeleteSubmission by remember { mutableStateOf<ChallengeSubmissionView?>(null) }

    if (loading) {
        P { Text(strings.challengesLoading) }
        return
    }

    SectionTitle(strings.adminPendingSubmissions)
    if (data.pending.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoPending) }
    } else {
        data.pending.forEach { submission ->
            AdminSubmissionRow(
                submission = submission,
                onPublish = {
                    scope.launch {
                        runCatching { ChallengeApi.publish(submission.id) }
                            .onSuccess { onRefresh(); Toaster.show(strings.adminSaved) }
                            .onFailure { Toaster.show(it.message ?: strings.challengesSubmitError) }
                    }
                },
                onDelete = { pendingDeleteSubmission = submission },
            )
        }
    }

    SectionTitle(strings.adminChallengesTab)
    if (data.challenges.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoChallenges) }
    }
    data.challenges.forEach { challenge ->
        Div(attrs = { classes(Styles.adminCoverCard) }) {
            H2 { Text(challenge.name.ifBlank { strings.challengesUntitled }) }
            P(attrs = { classes(Styles.homePrompt) }) {
                Text("${challenge.submissions.size} · ${strings.challengesVotes}")
            }
            if (renaming?.id == challenge.id) {
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(renameValue)
                    onInput { renameValue = it.value }
                }
                PrimaryButton(label = strings.adminSave, icon = "save") {
                    scope.launch {
                        runCatching { ChallengeApi.rename(challenge.id, renameValue) }
                            .onSuccess {
                                renaming = null
                                onRefresh()
                                Toaster.show(strings.adminSaved)
                            }
                            .onFailure { Toaster.show(it.message ?: strings.challengesSubmitError) }
                    }
                }
            } else {
                Div(attrs = { classes(Styles.adminRowActions) }) {
                    Button(attrs = {
                        classes(Styles.btnText)
                        attr("type", "button")
                        onClick {
                            renaming = challenge
                            renameValue = challenge.name
                        }
                    }) { Text(strings.adminRenameChallenge) }
                    Button(attrs = {
                        classes(Styles.btnText)
                        attr("type", "button")
                        onClick { pendingDeleteChallenge = challenge }
                    }) { Text(strings.adminDeleteChallenge) }
                }
            }
            challenge.submissions.forEach { submission ->
                AdminSubmissionRow(
                    submission = submission,
                    onPublish = if (submission.status == ChallengeLogic.STATUS_IN_REVIEW) {
                        {
                            scope.launch {
                                runCatching { ChallengeApi.publish(submission.id) }
                                    .onSuccess { onRefresh(); Toaster.show(strings.adminSaved) }
                                    .onFailure { Toaster.show(it.message ?: strings.challengesSubmitError) }
                            }
                        }
                    } else null,
                    onDelete = { pendingDeleteSubmission = submission },
                )
            }
        }
    }

    ConfirmDeleteDialog(
        visible = pendingDeleteChallenge != null,
        title = strings.adminDeleteChallenge,
        onDismiss = { pendingDeleteChallenge = null },
        onConfirm = {
            val id = pendingDeleteChallenge?.id ?: return@ConfirmDeleteDialog
            pendingDeleteChallenge = null
            scope.launch {
                runCatching { ChallengeApi.deleteChallenge(id) }
                    .onSuccess { onRefresh() }
                    .onFailure { Toaster.show(it.message ?: strings.challengesSubmitError) }
            }
        },
    )
    ConfirmDeleteDialog(
        visible = pendingDeleteSubmission != null,
        title = strings.adminDeleteSubmission,
        onDismiss = { pendingDeleteSubmission = null },
        onConfirm = {
            val id = pendingDeleteSubmission?.id ?: return@ConfirmDeleteDialog
            pendingDeleteSubmission = null
            scope.launch {
                runCatching { ChallengeApi.deleteSubmission(id) }
                    .onSuccess { onRefresh() }
                    .onFailure { Toaster.show(it.message ?: strings.challengesSubmitError) }
            }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val strings = Localization.strings
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(title) }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDismiss() }
            }) { Text(strings.close) }
            Button(attrs = {
                classes(Styles.btnPrimary)
                attr("type", "button")
                onClick { onConfirm() }
            }) { Text(strings.adminDelete) }
        }
    }
}

@Composable
private fun AdminSubmissionRow(
    submission: ChallengeSubmissionView,
    onPublish: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val strings = Localization.strings
    Div(attrs = { classes(Styles.adminPhotoCard, Styles.adminChallengeRow) }) {
        if (submission.videoUrl.isNotBlank()) {
            AdminChallengeVideo(submission.videoUrl, submission.thumbnailUrl)
        }
        Span { Text(submission.challengerName) }
        P(attrs = { classes(Styles.homePrompt) }) {
            Text("${ChallengeLogic.statusLabel(submission.status)} · ${submission.challengeName.ifBlank { strings.challengesUntitled }}")
        }
        Div(attrs = { classes(Styles.adminRowActions) }) {
            if (onPublish != null) {
                PrimaryButton(label = strings.adminPublishSubmission, icon = "publish", onClick = onPublish)
            }
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDelete() }
            }) { Text(strings.adminDeleteSubmission) }
        }
    }
}

@Composable
private fun AdminChallengeVideo(url: String, poster: String?) {
    Div(attrs = {
        ref { element ->
            val video = document.createElement("video") as HTMLVideoElement
            video.src = url
            video.controls = true
            video.preload = "metadata"
            video.setAttribute("playsinline", "true")
            if (!poster.isNullOrBlank()) video.poster = poster
            video.className = Styles.adminChallengeVideo
            element.appendChild(video)
            onDispose {
                video.pause()
                video.src = ""
                element.removeChild(video)
            }
        }
    })
}
