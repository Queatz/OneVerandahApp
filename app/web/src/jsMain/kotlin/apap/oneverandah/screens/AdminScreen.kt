package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.PhraseApi
import apap.oneverandah.api.PhraseApiException
import apap.oneverandah.api.firstFileOrNull
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.Scaffold
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.i18n.Localization
import apap.oneverandah.model.LocalizedText
import apap.oneverandah.phrases.GeneratePhraseImageRequest
import apap.oneverandah.phrases.Phrase
import apap.oneverandah.phrases.PhraseDefaults
import apap.oneverandah.phrases.PhraseLocations
import apap.oneverandah.phrases.PhraseUpsertRequest
import apap.oneverandah.phrases.ReferencePhoto
import apap.oneverandah.state.AdminAuth
import apap.oneverandah.state.Toaster
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
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
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
fun AdminScreen(onBack: () -> Unit) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var showPassword by remember { mutableStateOf(!AdminAuth.isSignedIn) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(AdminTab.PHRASES) }
    var phrases by remember { mutableStateOf<List<Phrase>>(emptyList()) }
    var photos by remember { mutableStateOf<List<ReferencePhoto>>(emptyList()) }
    var coverImageUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Phrase?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDeletePhrase by remember { mutableStateOf<Phrase?>(null) }
    var pendingDeletePhoto by remember { mutableStateOf<ReferencePhoto?>(null) }
    var generateTarget by remember { mutableStateOf<GenerateTarget?>(null) }
    /** Latest generated image URL to inject into the open phrase editor. */
    var editorImageOverride by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (!AdminAuth.isSignedIn) return
        scope.launch {
            loading = true
            try {
                val response = PhraseApi.fetchAdminPhrases()
                phrases = response.phrases
                coverImageUrl = response.coverImageUrl
                photos = PhraseApi.fetchReferencePhotos()
            } catch (e: PhraseApiException) {
                if (e.message?.contains("authentication", ignoreCase = true) == true) {
                    AdminAuth.clear()
                    showPassword = true
                }
                Toaster.show(e.message ?: strings.adminError)
            } catch (e: Throwable) {
                Toaster.show(e.message ?: strings.adminError)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(AdminAuth.isSignedIn) {
        if (AdminAuth.isSignedIn) {
            showPassword = false
            refresh()
        }
    }

    val anySheetOpen = showPassword || showEditor || pendingDeletePhrase != null ||
        pendingDeletePhoto != null || generateTarget != null

    Scaffold(
        title = strings.adminTitle,
        onBack = onBack,
        showChatAction = false,
        showPhrasesAction = false,
        lockScroll = anySheetOpen,
        overlay = {
            // Always mount sheets and toggle `visible` so enter/exit CSS animates.
            AdminPasswordDialog(
                visible = showPassword,
                password = password,
                error = passwordError,
                busy = signingIn,
                onPasswordChange = {
                    password = it
                    passwordError = null
                },
                onDismiss = {
                    if (!AdminAuth.isSignedIn) onBack()
                    else showPassword = false
                },
                onSubmit = {
                    scope.launch {
                        signingIn = true
                        passwordError = null
                        try {
                            val token = PhraseApi.signIn(password)
                            AdminAuth.setToken(token)
                            password = ""
                            showPassword = false
                            refresh()
                        } catch (e: Throwable) {
                            passwordError = strings.adminWrongPassword
                        } finally {
                            signingIn = false
                        }
                    }
                },
            )

            PhraseEditorDialog(
                visible = showEditor,
                initial = editing,
                injectedImageUrl = editorImageOverride,
                knownLocations = knownLocations(phrases),
                onDismiss = {
                    showEditor = false
                    editing = null
                    editorImageOverride = null
                },
                onSave = { request ->
                    scope.launch {
                        try {
                            if (editing == null) {
                                PhraseApi.createPhrase(request)
                            } else {
                                PhraseApi.updatePhrase(editing!!.id, request)
                            }
                            showEditor = false
                            editing = null
                            editorImageOverride = null
                            refresh()
                            Toaster.show(strings.adminSaved)
                        } catch (e: Throwable) {
                            Toaster.show(e.message ?: strings.adminError)
                        }
                    }
                },
                onGeneratePhoto = { phraseDraftId, currentImageUrl ->
                    generateTarget = GenerateTarget.Phrase(
                        phraseId = phraseDraftId ?: editing?.id,
                        seedImageUrl = currentImageUrl,
                        onApplied = { url ->
                            // Push into the open editor immediately (works for edit + create).
                            editing = editing?.copy(imageUrl = url)
                                ?: editing
                            editorImageOverride = url
                        },
                    )
                },
            )

            ConfirmDeleteDialog(
                visible = pendingDeletePhrase != null,
                title = strings.adminDeletePhraseConfirm,
                onDismiss = { pendingDeletePhrase = null },
                onConfirm = {
                    val phrase = pendingDeletePhrase ?: return@ConfirmDeleteDialog
                    pendingDeletePhrase = null
                    scope.launch {
                        try {
                            PhraseApi.deletePhrase(phrase.id)
                            refresh()
                            Toaster.show(strings.adminSaved)
                        } catch (e: Throwable) {
                            Toaster.show(e.message ?: strings.adminError)
                        }
                    }
                },
            )

            ConfirmDeleteDialog(
                visible = pendingDeletePhoto != null,
                title = strings.adminDeletePhotoConfirm,
                onDismiss = { pendingDeletePhoto = null },
                onConfirm = {
                    val photo = pendingDeletePhoto ?: return@ConfirmDeleteDialog
                    pendingDeletePhoto = null
                    scope.launch {
                        try {
                            PhraseApi.deleteReferencePhoto(photo.id)
                            refresh()
                            Toaster.show(strings.adminSaved)
                        } catch (e: Throwable) {
                            Toaster.show(e.message ?: strings.adminError)
                        }
                    }
                },
            )

            GeneratePhotoDialog(
                visible = generateTarget != null,
                title = when (generateTarget) {
                    is GenerateTarget.Cover -> strings.adminGenerateCover
                    is GenerateTarget.Phrase -> strings.adminGeneratePhoto
                    null -> strings.adminGeneratePhoto
                },
                photos = photos,
                defaultPrompt = when (val target = generateTarget) {
                    is GenerateTarget.Cover -> strings.adminCoverPromptDefault
                    is GenerateTarget.Phrase -> {
                        val phrase = phrases.firstOrNull { it.id == target.phraseId }
                        phrase?.let {
                            "Dreamlike cinematic photo at One Verandah ${it.location.en}: ${it.spoken.en}"
                        } ?: strings.adminPhrasePromptDefault
                    }
                    null -> ""
                },
                onDismiss = { if (generateTarget != null) generateTarget = null },
                onGenerate = { prompt, selectedIds ->
                    val target = generateTarget
                        ?: error("Generate sheet closed before generation started")
                    val result = PhraseApi.generateImage(
                        GeneratePhraseImageRequest(
                            prompt = prompt,
                            referencePhotoIds = selectedIds,
                            phraseId = (target as? GenerateTarget.Phrase)?.phraseId,
                            asCover = target is GenerateTarget.Cover,
                        ),
                    )
                    val imageUrl = result.imageUrl
                    when (target) {
                        is GenerateTarget.Cover -> {
                            coverImageUrl = result.coverImageUrl ?: imageUrl
                        }
                        is GenerateTarget.Phrase -> {
                            result.phrase?.let { updated ->
                                phrases = phrases.map { if (it.id == updated.id) updated else it }
                            } ?: run {
                                // Generated without attaching (e.g. draft) — still reflect URL if list has id later.
                                target.phraseId?.let { id ->
                                    phrases = phrases.map {
                                        if (it.id == id) it.copy(imageUrl = imageUrl) else it
                                    }
                                }
                            }
                            if (showEditor) {
                                val matchesEdit = target.phraseId == null || editing?.id == target.phraseId
                                if (matchesEdit) {
                                    editing = result.phrase
                                        ?: editing?.copy(imageUrl = imageUrl)
                                    editorImageOverride = imageUrl
                                }
                            }
                            target.onApplied?.invoke(imageUrl)
                        }
                    }
                    // Close sheet first so the user sees the updated cover/list underneath.
                    generateTarget = null
                    refresh()
                    Toaster.show(strings.adminSaved)
                },
            )
        },
    ) {
        if (!AdminAuth.isSignedIn) {
            P(attrs = { classes(Styles.homePrompt) }) {
                Text(strings.adminLockedHint)
            }
            return@Scaffold
        }

        Div(attrs = { classes(Styles.adminToolbar) }) {
            Div(attrs = { classes(Styles.chipGroup) }) {
                Button(attrs = {
                    classes(Styles.chip)
                    if (tab == AdminTab.PHRASES) classes(Styles.chipActive)
                    attr("type", "button")
                    onClick { tab = AdminTab.PHRASES }
                }) { Text(strings.adminPhrasesTab) }
                Button(attrs = {
                    classes(Styles.chip)
                    if (tab == AdminTab.PHOTOS) classes(Styles.chipActive)
                    attr("type", "button")
                    onClick { tab = AdminTab.PHOTOS }
                }) { Text(strings.adminPhotosTab) }
            }
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick {
                    AdminAuth.clear()
                    showPassword = true
                    phrases = emptyList()
                    photos = emptyList()
                    coverImageUrl = null
                }
            }) { Text(strings.adminLogout) }
        }

        when (tab) {
            AdminTab.PHRASES -> {
                Div(attrs = { classes(Styles.adminCoverCard) }) {
                    SectionTitle(strings.adminCoverTitle)
                    P(attrs = { classes(Styles.homePrompt) }) {
                        Text(strings.adminCoverHint)
                    }
                    coverImageUrl?.let { url ->
                        Img(src = url, alt = strings.adminCoverTitle, attrs = {
                            classes(Styles.adminCoverPreview)
                        })
                    }
                    PrimaryButton(
                        label = strings.adminGenerateCover,
                        icon = "auto_awesome",
                    ) {
                        generateTarget = GenerateTarget.Cover
                    }
                }

                Div(attrs = { classes(Styles.adminActions) }) {
                    PrimaryButton(label = strings.adminAddPhrase, icon = "add") {
                        editing = null
                        editorImageOverride = null
                        showEditor = true
                    }
                }
                if (loading && phrases.isEmpty()) {
                    P { Text(strings.phrasesLoading) }
                }
                phrases.forEach { phrase ->
                    Div(attrs = { classes(Styles.listCard) }) {
                        val thumb = phrase.imageUrl
                        if (!thumb.isNullOrBlank()) {
                            Img(src = thumb, alt = phrase.spoken.en, attrs = {
                                classes(Styles.adminPhraseThumb)
                            })
                        } else {
                            MaterialSymbol(phrase.icon, extraClass = Styles.listIcon)
                        }
                        Div(attrs = { classes(Styles.listDetails) }) {
                            H2 { Text(phrase.spoken.en) }
                            P {
                                Text(
                                    buildString {
                                        append(phrase.location.en)
                                        if (!phrase.approved) append(" · ${strings.adminUnapproved}")
                                    },
                                )
                            }
                        }
                        Div(attrs = { classes(Styles.adminRowActions) }) {
                            Button(attrs = {
                                classes(Styles.btnText)
                                attr("type", "button")
                                onClick {
                                    generateTarget = GenerateTarget.Phrase(phraseId = phrase.id)
                                }
                            }) { Text(strings.adminGeneratePhoto) }
                            Button(attrs = {
                                classes(Styles.btnText)
                                attr("type", "button")
                                onClick {
                                    editing = phrase
                                    editorImageOverride = null
                                    showEditor = true
                                }
                            }) { Text(strings.adminEdit) }
                            Button(attrs = {
                                classes(Styles.btnText)
                                attr("type", "button")
                                onClick { pendingDeletePhrase = phrase }
                            }) { Text(strings.adminDelete) }
                        }
                    }
                }
            }

            AdminTab.PHOTOS -> {
                ReferencePhotosPanel(
                    photos = photos,
                    loading = loading,
                    onUploaded = { refresh() },
                    onDeleteRequest = { pendingDeletePhoto = it },
                )
            }
        }
    }
}

private sealed class GenerateTarget {
    data object Cover : GenerateTarget()
    data class Phrase(
        val phraseId: String?,
        val seedImageUrl: String? = null,
        val onApplied: ((String) -> Unit)? = null,
    ) : GenerateTarget()
}

private fun knownLocations(phrases: List<Phrase>): List<PhraseLocations.Entry> {
    val fromCatalog = PhraseLocations.all
    val fromPhrases = phrases
        .groupBy { it.locationKey }
        .map { (key, items) ->
            val first = items.first()
            PhraseLocations.Entry(key, first.location, first.icon)
        }
    return (fromCatalog + fromPhrases).distinctBy { it.key }
}

@Composable
private fun ReferencePhotosPanel(
    photos: List<ReferencePhoto>,
    loading: Boolean,
    onUploaded: () -> Unit,
    onDeleteRequest: (ReferencePhoto) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var uploading by remember { mutableStateOf(false) }

    SectionTitle(strings.adminPhotosTab)
    P(attrs = { classes(Styles.homePrompt) }) {
        Text(strings.adminPhotosHint)
    }

    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(strings.adminPhotoName) }
        Input(type = InputType.Text) {
            classes(Styles.textInput)
            value(name)
            placeholder(strings.adminPhotoNamePlaceholder)
            onInput { name = it.value }
        }
    }

    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(strings.adminPhotoFile) }
        Input(type = InputType.File) {
            classes(Styles.textInput)
            attr("accept", "image/*")
            onChange {
                val el = it.nativeEvent.target as? HTMLInputElement
                selectedFile = el?.firstFileOrNull()
            }
        }
    }

    Div(attrs = { classes(Styles.adminUploadBlock) }) {
        PrimaryButton(
            label = if (uploading) strings.adminUploading else strings.adminUploadPhoto,
            icon = "upload",
            enabled = !uploading && name.isNotBlank() && selectedFile != null,
        ) {
            val file = selectedFile ?: return@PrimaryButton
            scope.launch {
                uploading = true
                try {
                    PhraseApi.uploadReferencePhoto(name.trim(), file)
                    name = ""
                    selectedFile = null
                    Toaster.show(strings.adminSaved)
                    onUploaded()
                } catch (e: Throwable) {
                    Toaster.show(e.message ?: strings.adminError)
                } finally {
                    uploading = false
                }
            }
        }
    }

    if (loading && photos.isEmpty()) {
        P { Text(strings.phrasesLoading) }
    }

    photos.forEach { photo ->
        Div(attrs = { classes(Styles.adminPhotoCard) }) {
            Img(src = photo.imageUrl, alt = photo.name, attrs = {
                classes(Styles.adminPhotoThumb)
            })
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(photo.name) }
                P { Text(photo.imageUrl) }
            }
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { onDeleteRequest(photo) }
            }) { Text(strings.adminDelete) }
        }
    }
}

@Composable
private fun AdminPasswordDialog(
    visible: Boolean,
    password: String,
    error: String?,
    busy: Boolean,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val strings = Localization.strings
    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.adminPasswordTitle) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.textField) }) {
                Input(type = InputType.Password) {
                    classes(Styles.textInput)
                    value(password)
                    placeholder(strings.adminPasswordPlaceholder)
                    onInput { onPasswordChange(it.value) }
                    onKeyUp {
                        if (it.key == "Enter" && password.isNotBlank() && !busy) onSubmit()
                    }
                }
                if (error != null) {
                    Span(attrs = { classes(Styles.textError) }) {
                        Text(error)
                    }
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
                label = strings.adminSignIn,
                icon = "lock_open",
                enabled = password.isNotBlank() && !busy,
                onClick = onSubmit,
            )
        }
    }
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
private fun GeneratePhotoDialog(
    visible: Boolean,
    title: String,
    photos: List<ReferencePhoto>,
    defaultPrompt: String,
    onDismiss: () -> Unit,
    /** Suspends until generation finishes; throw to keep the sheet open for retry. */
    onGenerate: suspend (prompt: String, selectedIds: List<String>) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var prompt by remember(visible, defaultPrompt) { mutableStateOf(defaultPrompt) }
    var selected by remember(visible) { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember(visible) { mutableStateOf(false) }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = { if (!busy) onDismiss() },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(title) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            P(attrs = { classes(Styles.homePrompt) }) {
                Text(strings.adminGenerateHint)
            }
            AdminArea(strings.adminGeneratePrompt, prompt) { prompt = it }

            if (photos.isEmpty()) {
                P(attrs = { classes(Styles.pinnedEmpty) }) {
                    Text(strings.adminGenerateNoPhotos)
                }
            } else {
                Label(attrs = { classes(Styles.phraseExpandHint) }) {
                    Text(strings.adminSelectReferences)
                }
                Div(attrs = { classes(Styles.adminRefGrid) }) {
                    photos.forEach { photo ->
                        val isOn = photo.id in selected
                        Button(attrs = {
                            classes(Styles.adminRefTile)
                            if (isOn) classes(Styles.adminRefTileSelected)
                            attr("type", "button")
                            onClick {
                                selected = if (isOn) selected - photo.id else selected + photo.id
                            }
                        }) {
                            Img(src = photo.imageUrl, alt = photo.name, attrs = {
                                classes(Styles.adminRefThumb)
                            })
                            Span { Text(photo.name) }
                        }
                    }
                }
            }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                if (busy) attr("disabled", "true")
                onClick { if (!busy) onDismiss() }
            }) { Text(strings.close) }
            PrimaryButton(
                label = if (busy) strings.adminGenerating else strings.adminGenerate,
                icon = "auto_awesome",
                enabled = !busy && prompt.isNotBlank(),
            ) {
                if (busy) return@PrimaryButton
                val promptValue = prompt.trim()
                val selectedIds = selected.toList()
                scope.launch {
                    busy = true
                    try {
                        onGenerate(promptValue, selectedIds)
                        // Parent closes the sheet on success (visible → false).
                    } catch (e: Throwable) {
                        Toaster.show(e.message ?: strings.adminError)
                    } finally {
                        // If still visible (failure), unlock the button for retry.
                        busy = false
                    }
                }
            }
        }
    }

    LaunchedEffect(visible) {
        if (!visible) busy = false
    }
}

@Composable
private fun PhraseEditorDialog(
    visible: Boolean,
    initial: Phrase?,
    injectedImageUrl: String? = null,
    knownLocations: List<PhraseLocations.Entry>,
    onDismiss: () -> Unit,
    onSave: (PhraseUpsertRequest) -> Unit,
    onGeneratePhoto: (phraseId: String?, currentImageUrl: String?) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var locationKey by remember(initial?.id, visible) { mutableStateOf(initial?.locationKey.orEmpty()) }
    var locationEn by remember(initial?.id, visible) { mutableStateOf(initial?.location?.en.orEmpty()) }
    var locationVi by remember(initial?.id, visible) { mutableStateOf(initial?.location?.vi.orEmpty()) }
    var icon by remember(initial?.id, visible) { mutableStateOf(initial?.icon ?: "auto_awesome") }
    var spokenEn by remember(initial?.id, visible) { mutableStateOf(initial?.spoken?.en.orEmpty()) }
    var spokenVi by remember(initial?.id, visible) { mutableStateOf(initial?.spoken?.vi.orEmpty()) }
    var verseEn by remember(initial?.id, visible) { mutableStateOf(initial?.verse?.en.orEmpty()) }
    var verseVi by remember(initial?.id, visible) { mutableStateOf(initial?.verse?.vi.orEmpty()) }
    var rawAskEn by remember(initial?.id, visible) { mutableStateOf(initial?.rawAsk?.en.orEmpty()) }
    var rawAskVi by remember(initial?.id, visible) { mutableStateOf(initial?.rawAsk?.vi.orEmpty()) }
    var instructionsEn by remember(initial?.id, visible) {
        mutableStateOf(initial?.instructions?.en ?: PhraseDefaults.instructions.en)
    }
    var instructionsVi by remember(initial?.id, visible) {
        mutableStateOf(initial?.instructions?.vi ?: PhraseDefaults.instructions.vi)
    }
    var imageUrl by remember(initial?.id, visible) {
        mutableStateOf(initial?.imageUrl.orEmpty())
    }
    var approved by remember(initial?.id, visible) { mutableStateOf(initial?.approved ?: true) }
    var locationFocused by remember { mutableStateOf(false) }

    // Keep imageUrl in sync when parent refreshes the edited phrase after generation.
    LaunchedEffect(initial?.imageUrl, visible) {
        if (visible) {
            val fromInitial = initial?.imageUrl.orEmpty()
            if (fromInitial.isNotBlank()) imageUrl = fromInitial
        }
    }

    // Immediate inject from generate-photo sheet (covers create-mode where initial is null).
    LaunchedEffect(injectedImageUrl, visible) {
        if (visible && !injectedImageUrl.isNullOrBlank()) {
            imageUrl = injectedImageUrl
        }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) {
                Text(if (initial == null) strings.adminAddPhrase else strings.adminEditPhrase)
            }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.adminLocationKey) }
                Input(type = InputType.Text) {
                    classes(Styles.textInput)
                    value(locationKey)
                    onInput { locationKey = it.value }
                    onFocusIn { locationFocused = true }
                    onFocusOut {
                        scope.launch {
                            kotlinx.coroutines.delay(180)
                            locationFocused = false
                        }
                    }
                }
            }
            if (locationFocused || locationKey.isBlank()) {
                Div(attrs = { classes(Styles.adminLocationSuggestions) }) {
                    knownLocations.forEach { entry ->
                        Button(attrs = {
                            classes(Styles.chip)
                            if (locationKey == entry.key) classes(Styles.chipActive)
                            attr("type", "button")
                            onMouseDown {
                                // Prevent input blur from racing the click.
                                it.preventDefault()
                            }
                            onClick {
                                locationKey = entry.key
                                locationEn = entry.location.en
                                locationVi = entry.location.vi
                                icon = entry.icon
                                locationFocused = false
                            }
                        }) {
                            MaterialSymbol(entry.icon, extraClass = Styles.phraseLocationIcon)
                            Text(entry.location.en)
                        }
                    }
                }
            }

            AdminField(strings.adminLocationEn, locationEn) { locationEn = it }
            AdminField(strings.adminLocationVi, locationVi) { locationVi = it }

            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.adminIcon) }
                Div(attrs = { classes(Styles.adminIconField) }) {
                    Input(type = InputType.Text) {
                        classes(Styles.textInput, Styles.adminIconInput)
                        value(icon)
                        onInput { icon = it.value }
                    }
                    Span(attrs = { classes(Styles.adminIconPreview) }) {
                        MaterialSymbol(icon.ifBlank { "auto_awesome" })
                    }
                }
            }

            AdminField(strings.adminSpokenEn, spokenEn) { spokenEn = it }
            AdminField(strings.adminSpokenVi, spokenVi) { spokenVi = it }
            AdminArea(strings.adminVerseEn, verseEn) { verseEn = it }
            AdminArea(strings.adminVerseVi, verseVi) { verseVi = it }
            AdminField(strings.adminRawAskEn, rawAskEn) { rawAskEn = it }
            AdminField(strings.adminRawAskVi, rawAskVi) { rawAskVi = it }
            AdminArea(strings.adminInstructionsEn, instructionsEn) { instructionsEn = it }
            AdminArea(strings.adminInstructionsVi, instructionsVi) { instructionsVi = it }

            AdminField(strings.adminImageUrl, imageUrl) { imageUrl = it }
            if (imageUrl.isNotBlank()) {
                Img(src = imageUrl, alt = "", attrs = {
                    classes(Styles.adminCoverPreview)
                })
            }
            PrimaryButton(
                label = strings.adminGeneratePhoto,
                icon = "auto_awesome",
            ) {
                onGeneratePhoto(initial?.id, imageUrl.takeIf { it.isNotBlank() })
            }

            Button(attrs = {
                classes(Styles.chip)
                if (approved) classes(Styles.chipActive)
                attr("type", "button")
                onClick { approved = !approved }
            }) {
                Text(if (approved) strings.adminApproved else strings.adminUnapproved)
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
                enabled = locationKey.isNotBlank() && spokenEn.isNotBlank(),
            ) {
                onSave(
                    PhraseUpsertRequest(
                        id = initial?.id,
                        locationKey = locationKey.trim(),
                        location = LocalizedText(
                            en = locationEn.ifBlank { locationKey },
                            vi = locationVi.ifBlank { locationEn.ifBlank { locationKey } },
                        ),
                        icon = icon.ifBlank { "auto_awesome" },
                        spoken = LocalizedText(
                            en = spokenEn.trim(),
                            vi = spokenVi.ifBlank { spokenEn }.trim(),
                        ),
                        verse = LocalizedText(
                            en = verseEn.trim(),
                            vi = verseVi.ifBlank { verseEn }.trim(),
                        ),
                        rawAsk = LocalizedText(
                            en = rawAskEn.trim(),
                            vi = rawAskVi.ifBlank { rawAskEn }.trim(),
                        ),
                        instructions = LocalizedText(
                            en = instructionsEn.trim().ifBlank { PhraseDefaults.instructions.en },
                            vi = instructionsVi.trim().ifBlank { instructionsEn }.ifBlank {
                                PhraseDefaults.instructions.vi
                            },
                        ),
                        imageUrl = imageUrl.trim().takeIf { it.isNotEmpty() },
                        approved = approved,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AdminField(label: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(label) }
        Input(type = InputType.Text) {
            classes(Styles.textInput)
            value(value)
            onInput { onChange(it.value) }
        }
    }
}

@Composable
private fun AdminArea(label: String, value: String, onChange: (String) -> Unit) {
    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(label) }
        TextArea(attrs = {
            classes(Styles.textArea)
            value(value)
            onInput { onChange(it.value) }
        })
    }
}

private enum class AdminTab { PHRASES, PHOTOS }
