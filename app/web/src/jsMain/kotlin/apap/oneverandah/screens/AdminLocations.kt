package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.LocationApi
import apap.oneverandah.api.LocationApiException
import apap.oneverandah.api.firstFileOrNull
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.i18n.Localization
import apap.oneverandah.locations.AdminLocationView
import apap.oneverandah.locations.AdminLocationsResponse
import apap.oneverandah.locations.LocationContentBlock
import apap.oneverandah.locations.LocationHours
import apap.oneverandah.locations.LocationLogic
import apap.oneverandah.locations.LocationUpsertRequest
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
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
fun AdminLocationsPanel(
    data: AdminLocationsResponse,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AdminLocationView?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AdminLocationView?>(null) }

    if (loading && data.locations.isEmpty()) {
        P { Text(strings.locationsLoading) }
        return
    }

    Div(attrs = { classes(Styles.adminActions) }) {
        PrimaryButton(label = strings.adminAddLocation, icon = "add") {
            editing = null
            creating = true
        }
    }

    if (data.locations.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoLocations) }
    }
    data.locations.forEach { location ->
        Div(attrs = { classes(Styles.adminCoverCard) }) {
            location.photoUrls.firstOrNull()?.let { url ->
                Img(src = url, alt = location.name, attrs = { classes(Styles.meetAdminPhoto) })
            }
            H2 { Text(location.name.ifBlank { strings.locationsUntitled }) }
            P(attrs = { classes(Styles.homePrompt) }) {
                Text("${location.category} · ${locationStatusLabel(locationDisplay(location.status), strings)}")
            }
            if (location.address.isNotBlank()) {
                P { Text(location.address) }
            }
            if (location.commitments.isNotEmpty()) {
                P(attrs = { classes(Styles.homePrompt) }) {
                    Text("${strings.adminLocationCommitments}: ${location.commitments.size}")
                }
            }
            Div(attrs = { classes(Styles.adminRowActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick {
                        creating = false
                        editing = location
                    }
                }) { Text(strings.adminEditLocation) }
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick { pendingDelete = location }
                }) { Text(strings.adminDeleteLocation) }
            }
        }
    }

    LocationEditorDialog(
        visible = creating || editing != null,
        initial = editing,
        onDismiss = {
            creating = false
            editing = null
        },
        onSaved = {
            creating = false
            editing = null
            onRefresh()
        },
    )

    ConfirmLocationDeleteDialog(
        visible = pendingDelete != null,
        title = strings.adminDeleteLocation,
        onDismiss = { pendingDelete = null },
        onConfirm = {
            val target = pendingDelete
            if (target != null) {
                scope.launch {
                    runCatching { LocationApi.delete(target.id) }
                        .onSuccess {
                            pendingDelete = null
                            onRefresh()
                            Toaster.show(strings.adminSaved)
                        }
                        .onFailure { Toaster.show(it.message ?: strings.adminError) }
                }
            }
        },
    )
}

private fun locationDisplay(status: String): String = when (LocationLogic.normalizeStatus(status)) {
    LocationLogic.STATUS_COMING_SOON -> LocationLogic.DISPLAY_COMING_SOON
    LocationLogic.STATUS_OPERATING -> LocationLogic.DISPLAY_OPEN
    else -> LocationLogic.DISPLAY_PLANNING
}

@Composable
private fun LocationEditorDialog(
    visible: Boolean,
    initial: AdminLocationView?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(LocationLogic.STATUS_PLANNING) }
    var hostZalo by remember { mutableStateOf("") }
    var mapsUrl by remember { mutableStateOf("") }
    var highlight1 by remember { mutableStateOf("") }
    var highlight2 by remember { mutableStateOf("") }
    var highlight3 by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(listOf<String>()) }
    var content by remember { mutableStateOf(listOf<LocationContentBlock>()) }
    var days by remember { mutableStateOf(defaultDays()) }
    var busy by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(visible, initial?.id) {
        if (!visible) return@LaunchedEffect
        name = initial?.name.orEmpty()
        type = initial?.type.orEmpty()
        category = initial?.category.orEmpty()
        address = initial?.address.orEmpty()
        status = LocationLogic.normalizeStatus(initial?.status)
        hostZalo = initial?.hostZalo.orEmpty()
        mapsUrl = initial?.mapsUrl.orEmpty()
        highlight1 = initial?.highlights?.getOrNull(0).orEmpty()
        highlight2 = initial?.highlights?.getOrNull(1).orEmpty()
        highlight3 = initial?.highlights?.getOrNull(2).orEmpty()
        photos = initial?.photoUrls.orEmpty()
        content = initial?.content.orEmpty()
        days = daysFromSchedule(initial?.schedule.orEmpty())
        busy = false
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = { if (!busy) onDismiss() },
        expanded = true,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) {
                Text(if (initial == null) strings.adminAddLocation else strings.adminEditLocation)
            }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            AdminLocationField(strings.adminLocationName, name) { name = it }
            AdminLocationField(strings.adminLocationType, type) { type = it }
            AdminLocationField(strings.adminLocationCategory, category) { category = it }
            AdminLocationField(strings.adminLocationAddress, address) { address = it }
            AdminLocationField(strings.adminLocationHostZalo, hostZalo) { hostZalo = it }
            AdminLocationField(strings.adminLocationMapsUrl, mapsUrl) { mapsUrl = it }

            P(attrs = { classes(Styles.textLabel) }) { Text(strings.adminLocationStatus) }
            Div(attrs = { classes(Styles.chipGroup) }) {
                StatusChip(strings.locationsStatusPlanning, LocationLogic.STATUS_PLANNING, status) { status = it }
                StatusChip(strings.locationsStatusComingSoon, LocationLogic.STATUS_COMING_SOON, status) { status = it }
                StatusChip(strings.locationsStatusOpen, LocationLogic.STATUS_OPERATING, status) { status = it }
            }

            AdminLocationField(strings.adminLocationHighlights, highlight1) { highlight1 = it }
            AdminLocationField("", highlight2) { highlight2 = it }
            AdminLocationField("", highlight3) { highlight3 = it }

            SectionTitle(strings.adminLocationPhotos)
            Div(attrs = { classes(Styles.locationAdminPhotos) }) {
                photos.forEach { url ->
                    Div(attrs = { classes(Styles.locationAdminPhotoWrap) }) {
                        Img(src = url, alt = "", attrs = { classes(Styles.locationAdminPhoto) })
                        Button(attrs = {
                            classes(Styles.btnText)
                            attr("type", "button")
                            onClick { photos = photos.filterNot { it == url } }
                        }) { Text(strings.adminDelete) }
                    }
                }
            }
            Label(attrs = { classes(Styles.btnText) }) {
                Text(strings.adminLocationAddPhoto)
                Input(type = InputType.File) {
                    attr("accept", "image/*")
                    attr("multiple", "")
                    style { property("display", "none") }
                    onChange {
                        val input = it.nativeEvent.target as? HTMLInputElement ?: return@onChange
                        val files = filesOf(input)
                        input.value = ""
                        if (files.isEmpty()) return@onChange
                        scope.launch {
                            files.forEach { file ->
                                runCatching { LocationApi.uploadPhoto(file) }
                                    .onSuccess { url -> photos = photos + url }
                                    .onFailure { err -> Toaster.show(err.message ?: strings.adminError) }
                            }
                        }
                    }
                }
            }

            SectionTitle(strings.adminLocationSchedule)
            days.forEachIndexed { index, day ->
                val dayName = locationDayName(index + 1, strings)
                Div(attrs = { classes(Styles.locationScheduleRow) }) {
                    Button(attrs = {
                        classes(Styles.chip)
                        if (day.enabled) classes(Styles.chipActive)
                        attr("type", "button")
                        onClick {
                            days = days.toMutableList().also { it[index] = day.copy(enabled = !day.enabled) }
                        }
                    }) { Text(if (day.enabled) dayName else "${dayName} · ${strings.adminLocationClosedDay}") }
                    if (day.enabled) {
                        Input(type = InputType.Time) {
                            classes(Styles.textInput)
                            value(day.open)
                            onInput { value ->
                                days = days.toMutableList().also { it[index] = day.copy(open = value.value) }
                            }
                        }
                        Input(type = InputType.Time) {
                            classes(Styles.textInput)
                            value(day.close)
                            onInput { value ->
                                days = days.toMutableList().also { it[index] = day.copy(close = value.value) }
                            }
                        }
                    }
                }
            }

            SectionTitle(strings.adminLocationContent)
            content.forEachIndexed { index, block ->
                Div(attrs = { classes(Styles.adminCoverCard) }) {
                    if (block.type == LocationLogic.BLOCK_IMAGE) {
                        if (block.imageUrl.isNotBlank()) {
                            Img(src = block.imageUrl, alt = "", attrs = { classes(Styles.meetAdminPhoto) })
                        }
                    } else {
                        TextArea(block.text) {
                            classes(Styles.textArea)
                            onInput { event ->
                                content = content.toMutableList().also {
                                    it[index] = block.copy(text = event.value)
                                }
                            }
                        }
                    }
                    Div(attrs = { classes(Styles.adminRowActions) }) {
                        Button(attrs = {
                            classes(Styles.btnText)
                            attr("type", "button")
                            onClick {
                                if (index == 0) return@onClick
                                content = content.toMutableList().also {
                                    val item = it.removeAt(index)
                                    it.add(index - 1, item)
                                }
                            }
                        }) { Text("↑") }
                        Button(attrs = {
                            classes(Styles.btnText)
                            attr("type", "button")
                            onClick {
                                if (index >= content.lastIndex) return@onClick
                                content = content.toMutableList().also {
                                    val item = it.removeAt(index)
                                    it.add(index + 1, item)
                                }
                            }
                        }) { Text("↓") }
                        Button(attrs = {
                            classes(Styles.btnText)
                            attr("type", "button")
                            onClick { content = content.filterIndexed { i, _ -> i != index } }
                        }) { Text(strings.adminDelete) }
                    }
                }
            }
            Div(attrs = { classes(Styles.adminRowActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick { content = content + LocationContentBlock(type = LocationLogic.BLOCK_TEXT) }
                }) { Text(strings.adminLocationAddText) }
                Label(attrs = { classes(Styles.btnText) }) {
                    Text(strings.adminLocationAddImage)
                    Input(type = InputType.File) {
                        attr("accept", "image/*")
                        style { property("display", "none") }
                        onChange {
                            val file = (it.nativeEvent.target as? HTMLInputElement)?.firstFileOrNull()
                            (it.nativeEvent.target as? HTMLInputElement)?.value = ""
                            if (file == null) return@onChange
                            scope.launch {
                                runCatching { LocationApi.uploadPhoto(file) }
                                    .onSuccess { url ->
                                        content = content + LocationContentBlock(
                                            type = LocationLogic.BLOCK_IMAGE,
                                            imageUrl = url,
                                        )
                                    }
                                    .onFailure { err -> Toaster.show(err.message ?: strings.adminError) }
                            }
                        }
                    }
                }
            }

            initial?.commitments?.takeIf { it.isNotEmpty() }?.let { commitments ->
                SectionTitle(strings.adminLocationCommitments)
                commitments.forEach { item ->
                    P {
                        Text("${formatVnd(item.amountVnd)} · ${item.zaloNumber}")
                    }
                    if (item.message.isNotBlank()) P(attrs = { classes(Styles.homePrompt) }) { Text(item.message) }
                }
            }
            initial?.contacts?.takeIf { it.isNotEmpty() }?.let { contacts ->
                SectionTitle(strings.adminLocationContacts)
                contacts.forEach { item ->
                    P { Text(item.zaloNumber) }
                    P(attrs = { classes(Styles.homePrompt) }) { Text(item.message) }
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
                label = strings.adminSave,
                icon = "save",
                enabled = !busy && name.isNotBlank() && category.isNotBlank(),
            ) {
                val request = LocationUpsertRequest(
                    id = initial?.id,
                    name = name,
                    type = type,
                    category = category,
                    status = status,
                    address = address,
                    highlights = listOf(highlight1, highlight2, highlight3),
                    photoUrls = photos,
                    content = content,
                    schedule = days.toSchedule(),
                    hostZalo = hostZalo,
                    mapsUrl = mapsUrl,
                )
                scope.launch {
                    busy = true
                    try {
                        if (initial == null) LocationApi.create(request) else LocationApi.update(initial.id, request)
                        Toaster.show(strings.adminSaved)
                        onSaved()
                    } catch (e: LocationApiException) {
                        Toaster.show(e.message ?: strings.adminError)
                    } catch (t: Throwable) {
                        Toaster.show(t.message ?: strings.adminError)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    Button(attrs = {
        classes(Styles.chip)
        if (selected == value) classes(Styles.chipActive)
        attr("type", "button")
        onClick { onSelect(value) }
    }) { Text(label) }
}

@Composable
private fun AdminLocationField(label: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { classes(Styles.textField) }) {
        if (label.isNotBlank()) Label(attrs = { classes(Styles.textLabel) }) { Text(label) }
        Input(type = InputType.Text) {
            classes(Styles.textInput)
            value(value)
            onInput { onChange(it.value) }
        }
    }
}

@Composable
private fun ConfirmLocationDeleteDialog(
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

private data class DayEdit(val enabled: Boolean, val open: String, val close: String)

private fun defaultDays(): List<DayEdit> = List(7) { DayEdit(enabled = false, open = "09:00", close = "18:00") }

private fun daysFromSchedule(schedule: List<LocationHours>): List<DayEdit> {
    val byDay = schedule.groupBy { it.dayOfWeek }
    return (1..7).map { day ->
        val slot = byDay[day]?.minByOrNull { it.openMinutes }
        if (slot == null) DayEdit(false, "09:00", "18:00")
        else DayEdit(true, LocationLogic.formatMinutes(slot.openMinutes), LocationLogic.formatMinutes(slot.closeMinutes))
    }
}

private fun List<DayEdit>.toSchedule(): List<LocationHours> =
    mapIndexedNotNull { index, day ->
        if (!day.enabled) return@mapIndexedNotNull null
        val open = LocationLogic.parseMinutes(day.open) ?: return@mapIndexedNotNull null
        val close = LocationLogic.parseMinutes(day.close) ?: return@mapIndexedNotNull null
        LocationHours(dayOfWeek = index + 1, openMinutes = open, closeMinutes = close)
    }

private fun filesOf(input: HTMLInputElement): List<File> {
    val list = input.files ?: return emptyList()
    return (0 until list.length).mapNotNull { list.item(it) }
}
