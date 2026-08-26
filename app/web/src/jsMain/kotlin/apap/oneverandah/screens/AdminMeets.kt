package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.MeetApi
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.i18n.Localization
import apap.oneverandah.meets.AdminMeetView
import apap.oneverandah.meets.AdminMeetsResponse
import apap.oneverandah.meets.MeetLogic
import apap.oneverandah.meets.MeetPitchView
import apap.oneverandah.state.Toaster
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun AdminMeetsPanel(
    data: AdminMeetsResponse,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var pendingDeleteMeet by remember { mutableStateOf<AdminMeetView?>(null) }
    var pendingDeletePitch by remember { mutableStateOf<MeetPitchView?>(null) }
    var resetMeet by remember { mutableStateOf<AdminMeetView?>(null) }
    var resetRemainingValue by remember { mutableStateOf("") }

    if (loading) {
        P { Text(strings.meetsLoading) }
        return
    }

    SectionTitle(strings.adminPendingMeets)
    if (data.pendingMeets.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoPendingMeets) }
    } else {
        data.pendingMeets.forEach { meet ->
            AdminMeetRow(
                meet = meet,
                onApprove = {
                    scope.launch {
                        runCatching { MeetApi.approve(meet.id) }
                            .onSuccess { onRefresh(); Toaster.show(strings.adminSaved) }
                            .onFailure { Toaster.show(it.message ?: strings.meetsSubmitError) }
                    }
                },
                onDelete = { pendingDeleteMeet = meet },
            )
        }
    }

    SectionTitle(strings.adminPendingPitches)
    if (data.pendingPitches.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoPendingPitches) }
    } else {
        data.pendingPitches.forEach { pitch ->
            AdminPitchRow(
                pitch = pitch,
                onComplete = {
                    scope.launch {
                        runCatching { MeetApi.completePitch(pitch.id) }
                            .onSuccess { onRefresh(); Toaster.show(strings.adminSaved) }
                            .onFailure { Toaster.show(it.message ?: strings.meetsPitchError) }
                    }
                },
                onDelete = { pendingDeletePitch = pitch },
            )
        }
    }

    SectionTitle(strings.adminMeetsTab)
    if (data.meets.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoMeets) }
    }
    data.meets.forEach { meet ->
        Div(attrs = { classes(Styles.adminCoverCard) }) {
            if (meet.photoUrl.isNotBlank()) {
                Img(src = meet.photoUrl, alt = meet.name, attrs = { classes(Styles.meetAdminPhoto) })
            }
            H2 { Text(meet.name.ifBlank { strings.meetsUntitled }) }
            P(attrs = { classes(Styles.homePrompt) }) {
                Text("${strings.meetsRemaining(meet.remainingPitches)} · ${meet.maxPitches}")
            }
            P { Text(meet.pitch) }
            P(attrs = { classes(Styles.homePrompt) }) {
                Text("${strings.adminMeetZalo}: ${meet.zaloNumber}")
            }
            Div(attrs = { classes(Styles.adminRowActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick {
                        resetMeet = meet
                        resetRemainingValue = meet.remainingPitches.toString()
                    }
                }) { Text(strings.adminResetRemaining) }
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick { pendingDeleteMeet = meet }
                }) { Text(strings.adminDeleteMeet) }
            }
        }
    }

    ResetRemainingDialog(
        visible = resetMeet != null,
        value = resetRemainingValue,
        onValueChange = { resetRemainingValue = it },
        onDismiss = { resetMeet = null },
        onConfirm = {
            val id = resetMeet?.id ?: return@ResetRemainingDialog
            val remaining = resetRemainingValue.trim().toIntOrNull()
                ?.coerceIn(0, MeetLogic.MAX_PITCH_LIMIT)
                ?: return@ResetRemainingDialog
            resetMeet = null
            scope.launch {
                runCatching { MeetApi.resetRemaining(id, remaining) }
                    .onSuccess { onRefresh(); Toaster.show(strings.adminSaved) }
                    .onFailure { Toaster.show(it.message ?: strings.meetsSubmitError) }
            }
        },
    )
    ConfirmMeetDeleteDialog(
        visible = pendingDeleteMeet != null,
        title = strings.adminDeleteMeet,
        onDismiss = { pendingDeleteMeet = null },
        onConfirm = {
            val id = pendingDeleteMeet?.id ?: return@ConfirmMeetDeleteDialog
            pendingDeleteMeet = null
            scope.launch {
                runCatching { MeetApi.deleteMeet(id) }
                    .onSuccess { onRefresh() }
                    .onFailure { Toaster.show(it.message ?: strings.meetsSubmitError) }
            }
        },
    )
    ConfirmMeetDeleteDialog(
        visible = pendingDeletePitch != null,
        title = strings.adminDeletePitch,
        onDismiss = { pendingDeletePitch = null },
        onConfirm = {
            val id = pendingDeletePitch?.id ?: return@ConfirmMeetDeleteDialog
            pendingDeletePitch = null
            scope.launch {
                runCatching { MeetApi.deletePitch(id) }
                    .onSuccess { onRefresh() }
                    .onFailure { Toaster.show(it.message ?: strings.meetsPitchError) }
            }
        },
    )
}

@Composable
private fun ResetRemainingDialog(
    visible: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val strings = Localization.strings
    val remainingValid = value.trim().toIntOrNull() != null
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.adminResetRemaining) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.adminResetRemainingHint) }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(value)
                    attr("inputmode", "numeric")
                    onInput { onValueChange(it.value) }
                }
            }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDismiss() }
            }) { Text(strings.close) }
            PrimaryButton(
                label = strings.adminSave,
                icon = "save",
                enabled = remainingValid,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun ConfirmMeetDeleteDialog(
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
private fun AdminMeetRow(
    meet: AdminMeetView,
    onApprove: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = Localization.strings
    Div(attrs = { classes(Styles.adminPhotoCard, Styles.adminChallengeRow) }) {
        if (meet.photoUrl.isNotBlank()) {
            Img(src = meet.photoUrl, alt = meet.name, attrs = { classes(Styles.meetAdminPhoto) })
        }
        Span { Text(meet.name.ifBlank { strings.meetsUntitled }) }
        P { Text(meet.pitch) }
        P(attrs = { classes(Styles.homePrompt) }) {
            Text("${strings.adminMeetZalo}: ${meet.zaloNumber} · ${strings.meetsRemaining(meet.remainingPitches)}")
        }
        Div(attrs = { classes(Styles.adminRowActions) }) {
            PrimaryButton(label = strings.adminApproveMeet, icon = "check", onClick = onApprove)
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDelete() }
            }) { Text(strings.adminDeleteMeet) }
        }
    }
}

@Composable
private fun AdminPitchRow(
    pitch: MeetPitchView,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = Localization.strings
    Div(attrs = { classes(Styles.adminPhotoCard, Styles.adminChallengeRow) }) {
        Span { Text(pitch.meetName.ifBlank { strings.meetsUntitled }) }
        P { Text(pitch.message) }
        P(attrs = { classes(Styles.homePrompt) }) {
            Text("${strings.adminMeetPosterZalo}: ${pitch.meetZaloNumber}")
        }
        P(attrs = { classes(Styles.homePrompt) }) {
            Text("${strings.adminPitchReplyZalo}: ${pitch.zaloNumber}")
        }
        Div(attrs = { classes(Styles.adminRowActions) }) {
            PrimaryButton(label = strings.adminCompletePitch, icon = "done_all", onClick = onComplete)
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDelete() }
            }) { Text(strings.adminDeletePitch) }
        }
    }
}
