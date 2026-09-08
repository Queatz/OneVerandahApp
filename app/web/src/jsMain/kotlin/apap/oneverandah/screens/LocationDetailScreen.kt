package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.LocationApi
import apap.oneverandah.api.LocationApiException
import apap.oneverandah.api.sendContactMessage
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.FullscreenPhotoDialog
import apap.oneverandah.components.MarkdownView
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.ShareDialog
import apap.oneverandah.i18n.Localization
import apap.oneverandah.locations.LocationContentBlock
import apap.oneverandah.locations.LocationDetailResponse
import apap.oneverandah.locations.LocationLogic
import apap.oneverandah.state.MeetProfileStore
import apap.oneverandah.state.Toaster
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
fun LocationDetailScreen(
    locationId: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<LocationDetailResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var nowMs by remember { mutableStateOf(currentTimeMs()) }
    var commitOpen by remember { mutableStateOf(false) }
    var contactOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        detail = LocationApi.fetchLocation(locationId)
        loading = false
    }

    LaunchedEffect(locationId) { refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = currentTimeMs()
        }
    }

    val card = detail?.location
    val snapshot = card?.let { LocationLogic.hoursSnapshot(it.status, it.schedule, nowMs) }
    val title = card?.name?.ifBlank { strings.locationsUntitled } ?: strings.locationsTitle
    val dialogOpen = commitOpen || contactOpen || shareOpen || previewUrl != null

    Scaffold(
        title = title,
        onBack = onBack,
        onHome = onHome,
        showPhrasesAction = false,
        scrollKey = "location-$locationId",
        lockScroll = dialogOpen,
        footer = {
            val current = detail ?: return@Scaffold
            Div(attrs = { classes(Styles.locationFooter) }) {
                when {
                    current.canCommit -> PrimaryButton(
                        label = strings.locationsCommitFunds,
                        icon = "savings",
                    ) { commitOpen = true }
                    current.canContact -> {
                        if (current.location.status == LocationLogic.STATUS_OPERATING &&
                            current.location.mapsUrl.isNotBlank()
                        ) {
                            Button(attrs = {
                                classes(Styles.btnText)
                                attr("type", "button")
                                onClick { window.open(current.location.mapsUrl, "_blank") }
                            }) {
                                MaterialSymbol("directions")
                                Text(strings.locationsDirections)
                            }
                        }
                        PrimaryButton(
                            label = strings.locationsContact,
                            icon = "chat",
                        ) { contactOpen = true }
                    }
                }
            }
        },
        overlay = {
            val current = detail
            if (current != null) {
                CommitFundsDialog(
                    visible = commitOpen,
                    locationName = title,
                    onDismiss = { commitOpen = false },
                    onSend = { amount, message, zalo ->
                        detail = LocationApi.commitFunds(locationId, amount, message, zalo)
                        MeetProfileStore.save(MeetProfileStore.peekName(), zalo)
                        Toaster.show(strings.locationsCommitSent)
                        commitOpen = false
                        runCatching {
                            sendContactMessage(
                                name = title,
                                email = "locations@oneverandah.app",
                                service = "Location funds",
                                message = buildString {
                                    appendLine("New location fund commitment")
                                    appendLine("Location: $title")
                                    appendLine("Amount: ${formatVnd(amount)}")
                                    appendLine("Zalo: $zalo")
                                    appendLine()
                                    append(message)
                                },
                            )
                        }
                    },
                )
                ContactHostDialog(
                    visible = contactOpen,
                    locationName = title,
                    onDismiss = { contactOpen = false },
                    onSend = { message, zalo ->
                        detail = LocationApi.contactHost(locationId, message, zalo)
                        MeetProfileStore.save(MeetProfileStore.peekName(), zalo)
                        Toaster.show(strings.locationsContactSent)
                        contactOpen = false
                        runCatching {
                            sendContactMessage(
                                name = title,
                                email = "locations@oneverandah.app",
                                service = "Location contact",
                                message = buildString {
                                    appendLine("New location contact")
                                    appendLine("Location: $title")
                                    appendLine("Zalo: $zalo")
                                    appendLine()
                                    append(message)
                                },
                            )
                        }
                    },
                )
                ShareDialog(
                    visible = shareOpen,
                    serviceTitle = title,
                    link = locationShareUrl(locationId),
                    strings = strings,
                    onCopy = {
                        val clipboard = window.navigator.asDynamic().clipboard
                        if (clipboard != null) {
                            runCatching { clipboard.writeText(locationShareUrl(locationId)) }
                        }
                    },
                    onDismiss = { shareOpen = false },
                )
            }
            FullscreenPhotoDialog(
                visible = previewUrl != null,
                imageUrl = previewUrl,
                alt = title,
                onDismiss = { previewUrl = null },
            )
        },
    ) {
        when {
            loading -> P { Text(strings.locationsLoading) }
            card == null -> P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.locationsEmpty) }
            else -> {
                if (card.photoUrls.isNotEmpty()) {
                    Div(attrs = { classes(Styles.locationDetailPhotos) }) {
                        card.photoUrls.forEach { url ->
                            Img(src = url, alt = title, attrs = {
                                classes(Styles.locationDetailPhoto)
                                onClick { previewUrl = url }
                            })
                        }
                    }
                }
                H2 { Text(title) }
                if (card.type.isNotBlank()) {
                    P(attrs = { classes(Styles.locationCardType) }) { Text(card.type) }
                }
                Span(attrs = {
                    classes(Styles.locationStatus)
                    classes(locationStatusClass(snapshot?.displayStatus ?: card.displayStatus))
                }) { Text(locationStatusLabel(snapshot?.displayStatus ?: card.displayStatus, strings)) }
                if (card.address.isNotBlank()) {
                    P(attrs = { classes(Styles.homePrompt) }) { Text(card.address) }
                }
                locationHoursLabel(snapshot ?: LocationLogic.hoursSnapshot(card.status, card.schedule, nowMs), strings)
                    ?.let { hours -> P(attrs = { classes(Styles.locationCardHours) }) { Text(hours) } }
                card.highlights.take(3).forEach { highlight ->
                    P(attrs = { classes(Styles.locationCardHighlight) }) { Text("• $highlight") }
                }
                Div(attrs = { classes(Styles.locationCardActions) }) {
                    Button(attrs = {
                        classes(Styles.toolbarBtn)
                        if (card.liked) classes(Styles.toolbarBtnActive)
                        attr("type", "button")
                        onClick {
                            scope.launch {
                                runCatching { LocationApi.toggleLike(card.id) }
                                    .onSuccess { updated ->
                                        detail = detail?.copy(location = updated)
                                    }
                                    .onFailure { Toaster.show(it.message ?: strings.locationsLikeError) }
                            }
                        }
                    }) {
                        Span(attrs = {
                            classes(Styles.materialSymbols)
                            if (card.liked) style { property("font-variation-settings", "'FILL' 1") }
                        }) { Text("favorite") }
                        Text("${card.likeCount}")
                    }
                    Button(attrs = {
                        classes(Styles.toolbarBtn)
                        attr("type", "button")
                        onClick { shareOpen = true }
                    }) {
                        MaterialSymbol("ios_share")
                        Text(strings.share)
                    }
                }
                detail?.content.orEmpty().forEach { block ->
                    LocationContentBlockView(block) { previewUrl = it }
                }
            }
        }
    }
}

@Composable
private fun LocationContentBlockView(
    block: LocationContentBlock,
    onPhoto: (String) -> Unit,
) {
    when (block.type) {
        LocationLogic.BLOCK_IMAGE -> if (block.imageUrl.isNotBlank()) {
            Img(src = block.imageUrl, alt = "", attrs = {
                classes(Styles.locationContentPhoto)
                onClick { onPhoto(block.imageUrl) }
            })
        }
        else -> if (block.text.isNotBlank()) MarkdownView(block.text)
    }
}

@Composable
private fun CommitFundsDialog(
    visible: Boolean,
    locationName: String,
    onDismiss: () -> Unit,
    onSend: suspend (amountVnd: Long, message: String, zalo: String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var zalo by remember { mutableStateOf(MeetProfileStore.peekZalo()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            zalo = MeetProfileStore.peekZalo()
            busy = false
        }
    }

    val parsedAmount = LocationLogic.parseAmountVnd(amount)

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = { if (!busy) onDismiss() },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.locationsCommitFunds) }
            P(attrs = { classes(Styles.dialogService) }) { Text(locationName) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.locationsAmountLabel) }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(amount)
                    attr("inputmode", "numeric")
                    placeholder(strings.locationsAmountPlaceholder)
                    onInput { amount = it.value }
                }
            }
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.locationsMessageLabel) }
                TextArea(message) {
                    classes(Styles.textArea)
                    placeholder(strings.locationsMessagePlaceholder)
                    onInput { message = it.value }
                }
            }
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.zaloLabel) }
                Input(type = InputType.Tel) {
                    classes(Styles.textInput)
                    value(zalo)
                    placeholder(strings.zaloPlaceholder)
                    onInput { zalo = it.value }
                }
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
                label = strings.locationsCommitSubmit,
                icon = "savings",
                enabled = !busy && parsedAmount != null && zalo.isNotBlank(),
            ) {
                val vnd = parsedAmount ?: return@PrimaryButton
                scope.launch {
                    busy = true
                    try {
                        onSend(vnd, message.trim(), zalo.trim())
                        amount = ""
                        message = ""
                    } catch (e: LocationApiException) {
                        Toaster.show(e.message ?: strings.locationsCommitError)
                    } catch (t: Throwable) {
                        Toaster.show(t.message ?: strings.locationsCommitError)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactHostDialog(
    visible: Boolean,
    locationName: String,
    onDismiss: () -> Unit,
    onSend: suspend (message: String, zalo: String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var zalo by remember { mutableStateOf(MeetProfileStore.peekZalo()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            zalo = MeetProfileStore.peekZalo()
            busy = false
        }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = { if (!busy) onDismiss() },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.locationsContact) }
            P(attrs = { classes(Styles.dialogService) }) { Text(locationName) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.locationsMessageLabel) }
                TextArea(message) {
                    classes(Styles.textArea)
                    placeholder(strings.locationsContactPlaceholder)
                    onInput { message = it.value }
                }
            }
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.zaloLabel) }
                Input(type = InputType.Tel) {
                    classes(Styles.textInput)
                    value(zalo)
                    placeholder(strings.zaloPlaceholder)
                    onInput { zalo = it.value }
                }
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
                label = strings.locationsContactSubmit,
                icon = "send",
                enabled = !busy && message.isNotBlank() && zalo.isNotBlank(),
            ) {
                scope.launch {
                    busy = true
                    try {
                        onSend(message.trim(), zalo.trim())
                        message = ""
                    } catch (e: LocationApiException) {
                        Toaster.show(e.message ?: strings.locationsContactError)
                    } catch (t: Throwable) {
                        Toaster.show(t.message ?: strings.locationsContactError)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}
