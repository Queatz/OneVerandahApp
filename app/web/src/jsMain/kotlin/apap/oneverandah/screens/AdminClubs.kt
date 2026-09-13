package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.ClubApi
import apap.oneverandah.api.ClubApiException
import apap.oneverandah.api.firstFileOrNull
import apap.oneverandah.clubs.AdminClubView
import apap.oneverandah.clubs.AdminClubsResponse
import apap.oneverandah.clubs.ClubContentBlock
import apap.oneverandah.clubs.ClubCreateRequestView
import apap.oneverandah.clubs.ClubJoinView
import apap.oneverandah.clubs.ClubLogic
import apap.oneverandah.clubs.ClubUpsertRequest
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.FullscreenPhotoDialog
import apap.oneverandah.components.PhotoThumbButton
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
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
fun AdminClubsPanel(
    data: AdminClubsResponse,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AdminClubView?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AdminClubView?>(null) }
    var seedFromRequest by remember { mutableStateOf<ClubCreateRequestView?>(null) }
    var previewPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewIndex by remember { mutableStateOf(0) }
    var previewAlt by remember { mutableStateOf("") }

    fun openPhotos(photos: List<String>, index: Int, alt: String) {
        previewPhotos = photos
        previewIndex = index
        previewAlt = alt
    }

    if (loading && data.clubs.isEmpty() && data.createRequests.isEmpty() && data.joins.isEmpty()) {
        P { Text(strings.clubsLoading) }
        return
    }

    Div(attrs = { classes(Styles.adminActions) }) {
        PrimaryButton(label = strings.adminAddClub, icon = "add") {
            editing = null
            seedFromRequest = null
            creating = true
        }
    }

    SectionTitle(strings.adminClubCreateRequests)
    if (data.createRequests.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoClubCreateRequests) }
    }
    data.createRequests.forEach { request ->
        Div(attrs = { classes(Styles.adminCoverCard) }) {
            H2 { Text(request.name.ifBlank { strings.clubsUntitled }) }
            if (request.tagline.isNotBlank()) P { Text(request.tagline) }
            if (request.description.isNotBlank()) {
                P(attrs = { classes(Styles.homePrompt) }) { Text(request.description) }
            }
            P(attrs = { classes(Styles.homePrompt) }) {
                Text("${request.hostName} · ${request.hostZalo}")
            }
            Div(attrs = { classes(Styles.adminRowActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick {
                        seedFromRequest = request
                        editing = null
                        creating = true
                    }
                }) { Text(strings.adminAddClub) }
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick {
                        scope.launch {
                            runCatching { ClubApi.deleteCreateRequest(request.id) }
                                .onSuccess {
                                    onRefresh()
                                    Toaster.show(strings.adminSaved)
                                }
                                .onFailure { Toaster.show(it.message ?: strings.adminError) }
                        }
                    }
                }) { Text(strings.adminDismissClubRequest) }
            }
        }
    }

    SectionTitle(strings.adminClubJoinRequests)
    if (data.joins.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoClubJoinRequests) }
    }
    data.joins.forEach { join ->
        ClubJoinCard(join) {
            scope.launch {
                runCatching { ClubApi.deleteJoin(join.id) }
                    .onSuccess {
                        onRefresh()
                        Toaster.show(strings.adminSaved)
                    }
                    .onFailure { Toaster.show(it.message ?: strings.adminError) }
            }
        }
    }

    SectionTitle(strings.adminClubsTab)
    if (data.clubs.isEmpty()) {
        P(attrs = { classes(Styles.pinnedEmpty) }) { Text(strings.adminNoClubs) }
    }
    data.clubs.forEach { club ->
        Div(attrs = { classes(Styles.adminCoverCard) }) {
            club.photoUrls.firstOrNull()?.let { url ->
                PhotoThumbButton(
                    url = url,
                    alt = club.name,
                    thumbClass = Styles.meetAdminPhoto,
                ) { openPhotos(club.photoUrls, club.photoUrls.indexOf(url).coerceAtLeast(0), club.name) }
            }
            H2 { Text(club.name.ifBlank { strings.clubsUntitled }) }
            P(attrs = { classes(Styles.homePrompt) }) {
                Text(listOf(club.category, club.hostName).filter { it.isNotBlank() }.joinToString(" · "))
            }
            if (club.tagline.isNotBlank()) P { Text(club.tagline) }
            Div(attrs = { classes(Styles.adminRowActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick {
                        creating = false
                        seedFromRequest = null
                        editing = club
                    }
                }) { Text(strings.adminEditClub) }
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick { pendingDelete = club }
                }) { Text(strings.adminDeleteClub) }
            }
        }
    }

    ClubEditorDialog(
        visible = creating || editing != null,
        initial = editing,
        seed = seedFromRequest,
        onDismiss = {
            creating = false
            editing = null
            seedFromRequest = null
        },
        onSaved = {
            creating = false
            editing = null
            seedFromRequest = null
            onRefresh()
        },
        onPhotoClick = { photos, index, alt -> openPhotos(photos, index, alt) },
    )

    ConfirmClubDeleteDialog(
        visible = pendingDelete != null,
        title = strings.adminDeleteClub,
        onDismiss = { pendingDelete = null },
        onConfirm = {
            val target = pendingDelete
            if (target != null) {
                scope.launch {
                    runCatching { ClubApi.delete(target.id) }
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

    FullscreenPhotoDialog(
        visible = previewPhotos.isNotEmpty(),
        imageUrl = previewPhotos.getOrNull(previewIndex),
        alt = previewAlt,
        photos = previewPhotos,
        selectedIndex = previewIndex,
        onSelectedIndexChange = { previewIndex = it },
        onDismiss = { previewPhotos = emptyList() },
    )
}

@Composable
private fun ClubJoinCard(join: ClubJoinView, onDismiss: () -> Unit) {
    val strings = Localization.strings
    Div(attrs = { classes(Styles.adminCoverCard) }) {
        H2 { Text(join.name.ifBlank { strings.clubsUntitled }) }
        P(attrs = { classes(Styles.homePrompt) }) {
            Text(listOf(join.clubName, join.zaloNumber).filter { it.isNotBlank() }.joinToString(" · "))
        }
        if (join.hostZalo.isNotBlank()) {
            P(attrs = { classes(Styles.homePrompt) }) {
                Text("${strings.adminClubHostZalo}: ${join.hostZalo}")
            }
        }
        join.answers.filter { it.question.isNotBlank() || it.answer.isNotBlank() }.forEach { answer ->
            P { Text("${answer.question}: ${answer.answer}") }
        }
        if (join.message.isNotBlank()) {
            P(attrs = { classes(Styles.homePrompt) }) { Text(join.message) }
        }
        Div(attrs = { classes(Styles.adminRowActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDismiss() }
            }) { Text(strings.adminDismissClubJoin) }
        }
    }
}

@Composable
private fun ClubEditorDialog(
    visible: Boolean,
    initial: AdminClubView?,
    seed: ClubCreateRequestView?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onPhotoClick: (photos: List<String>, index: Int, alt: String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var tagline by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var hostName by remember { mutableStateOf("") }
    var hostZalo by remember { mutableStateOf("") }
    var activity1 by remember { mutableStateOf("") }
    var activity2 by remember { mutableStateOf("") }
    var activity3 by remember { mutableStateOf("") }
    var question1 by remember { mutableStateOf("") }
    var question2 by remember { mutableStateOf("") }
    var question3 by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(listOf<String>()) }
    var content by remember { mutableStateOf(listOf<ClubContentBlock>()) }
    var busy by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(visible, initial?.id, seed?.id) {
        if (!visible) return@LaunchedEffect
        name = initial?.name ?: seed?.name.orEmpty()
        tagline = initial?.tagline ?: seed?.tagline.orEmpty()
        description = initial?.description ?: seed?.description.orEmpty()
        category = initial?.category.orEmpty()
        hostName = initial?.hostName ?: seed?.hostName.orEmpty()
        hostZalo = initial?.hostZalo ?: seed?.hostZalo.orEmpty()
        activity1 = initial?.activities?.getOrNull(0).orEmpty()
        activity2 = initial?.activities?.getOrNull(1).orEmpty()
        activity3 = initial?.activities?.getOrNull(2).orEmpty()
        question1 = initial?.joinQuestions?.getOrNull(0).orEmpty()
        question2 = initial?.joinQuestions?.getOrNull(1).orEmpty()
        question3 = initial?.joinQuestions?.getOrNull(2).orEmpty()
        photos = initial?.photoUrls.orEmpty()
        content = initial?.content.orEmpty()
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
                Text(if (initial == null) strings.adminAddClub else strings.adminEditClub)
            }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            AdminClubField(strings.adminClubName, name) { name = it }
            AdminClubField(strings.adminClubTagline, tagline) { tagline = it }
            AdminClubArea(strings.adminClubDescription, description) { description = it }
            AdminClubField(strings.adminClubCategory, category) { category = it }
            AdminClubField(strings.adminClubHostName, hostName) { hostName = it }
            AdminClubField(strings.adminClubHostZalo, hostZalo) { hostZalo = it }

            AdminClubField(strings.adminClubActivities, activity1) { activity1 = it }
            AdminClubField("", activity2) { activity2 = it }
            AdminClubField("", activity3) { activity3 = it }

            AdminClubField(strings.adminClubJoinQuestions, question1) { question1 = it }
            AdminClubField("", question2) { question2 = it }
            AdminClubField("", question3) { question3 = it }

            SectionTitle(strings.adminClubPhotos)
            Div(attrs = { classes(Styles.locationAdminPhotos) }) {
                photos.forEachIndexed { index, url ->
                    Div(attrs = { classes(Styles.locationAdminPhotoWrap) }) {
                        PhotoThumbButton(
                            url = url,
                            alt = name,
                            thumbClass = Styles.locationAdminPhoto,
                        ) { onPhotoClick(photos, index, name) }
                        Button(attrs = {
                            classes(Styles.btnText)
                            attr("type", "button")
                            onClick { photos = photos.filterNot { it == url } }
                        }) { Text(strings.adminDelete) }
                    }
                }
            }
            Label(attrs = { classes(Styles.btnText) }) {
                Text(strings.adminClubAddPhoto)
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
                                runCatching { ClubApi.uploadPhoto(file) }
                                    .onSuccess { url -> photos = photos + url }
                                    .onFailure { err -> Toaster.show(err.message ?: strings.adminError) }
                            }
                        }
                    }
                }
            }

            SectionTitle(strings.adminClubContent)
            content.forEachIndexed { index, block ->
                Div(attrs = { classes(Styles.adminCoverCard) }) {
                    if (block.type == ClubLogic.BLOCK_IMAGE) {
                        if (block.imageUrl.isNotBlank()) {
                            PhotoThumbButton(
                                url = block.imageUrl,
                                alt = name,
                                thumbClass = Styles.meetAdminPhoto,
                            ) { onPhotoClick(listOf(block.imageUrl), 0, name) }
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
                    onClick { content = content + ClubContentBlock(type = ClubLogic.BLOCK_TEXT) }
                }) { Text(strings.adminClubAddText) }
                Label(attrs = { classes(Styles.btnText) }) {
                    Text(strings.adminClubAddImage)
                    Input(type = InputType.File) {
                        attr("accept", "image/*")
                        style { property("display", "none") }
                        onChange {
                            val file = (it.nativeEvent.target as? HTMLInputElement)?.firstFileOrNull()
                            (it.nativeEvent.target as? HTMLInputElement)?.value = ""
                            if (file == null) return@onChange
                            scope.launch {
                                runCatching { ClubApi.uploadPhoto(file) }
                                    .onSuccess { url ->
                                        content = content + ClubContentBlock(
                                            type = ClubLogic.BLOCK_IMAGE,
                                            imageUrl = url,
                                        )
                                    }
                                    .onFailure { err -> Toaster.show(err.message ?: strings.adminError) }
                            }
                        }
                    }
                }
            }

            initial?.joins?.takeIf { it.isNotEmpty() }?.let { joins ->
                SectionTitle(strings.adminClubJoinRequests)
                joins.forEach { item ->
                    P { Text("${item.name} · ${item.zaloNumber}") }
                    if (item.hostZalo.isNotBlank()) {
                        P(attrs = { classes(Styles.homePrompt) }) {
                            Text("${strings.adminClubHostZalo}: ${item.hostZalo}")
                        }
                    }
                    if (item.message.isNotBlank()) P(attrs = { classes(Styles.homePrompt) }) { Text(item.message) }
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
                val request = ClubUpsertRequest(
                    id = initial?.id,
                    name = name,
                    tagline = tagline,
                    description = description,
                    category = category,
                    activities = listOf(activity1, activity2, activity3),
                    photoUrls = photos,
                    content = content,
                    joinQuestions = listOf(question1, question2, question3),
                    hostName = hostName,
                    hostZalo = hostZalo,
                )
                scope.launch {
                    busy = true
                    try {
                        if (initial == null) ClubApi.create(request) else ClubApi.update(initial.id, request)
                        seed?.id?.let { runCatching { ClubApi.deleteCreateRequest(it) } }
                        Toaster.show(strings.adminSaved)
                        onSaved()
                    } catch (e: ClubApiException) {
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
private fun AdminClubField(label: String, value: String, onChange: (String) -> Unit) {
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
private fun AdminClubArea(label: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(label) }
        TextArea(value) {
            classes(Styles.textArea)
            onInput { onChange(it.value) }
        }
    }
}

@Composable
private fun ConfirmClubDeleteDialog(
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

private fun filesOf(input: HTMLInputElement): List<File> {
    val list = input.files ?: return emptyList()
    return (0 until list.length).mapNotNull { list.item(it) }
}
