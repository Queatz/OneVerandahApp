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
import apap.oneverandah.api.StoryApi
import apap.oneverandah.api.StoryApiException
import apap.oneverandah.api.firstFileOrNull
import apap.oneverandah.components.BottomDialog
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.PhotoThumbButton
import apap.oneverandah.components.PrimaryButton
import apap.oneverandah.components.SectionTitle
import apap.oneverandah.components.Spacer
import apap.oneverandah.i18n.Localization
import apap.oneverandah.i18n.flagFor
import apap.oneverandah.model.Language
import apap.oneverandah.stories.StoryLogic
import apap.oneverandah.phrases.ReferencePhoto
import apap.oneverandah.state.Toaster
import apap.oneverandah.stories.GenerateStoryImageRequest
import apap.oneverandah.stories.Story
import apap.oneverandah.stories.StoryCharacter
import apap.oneverandah.stories.StoryPage
import apap.oneverandah.stories.StoryPageUpsert
import apap.oneverandah.stories.StoryUpsertRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.files.File

@Composable
fun AdminStoriesPanel(
    stories: List<Story>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onPhotoClick: (url: String, alt: String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Story?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Story?>(null) }
    var publishTarget by remember { mutableStateOf<Story?>(null) }
    var publishingIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(stories.any { it.publishing } || publishingIds.isNotEmpty()) {
        if (stories.none { it.publishing } && publishingIds.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(2_000)
            onRefresh()
        }
    }

    PrimaryButton(label = strings.adminAddBook, icon = "add") {
        editing = null
        showEditor = true
    }
    Spacer()
    if (loading && stories.isEmpty()) {
        P { Text(strings.storiesLoading) }
    } else if (stories.isEmpty()) {
        P(attrs = { classes(Styles.homePrompt) }) { Text(strings.adminNoBooks) }
    }
    stories.forEach { story ->
        Div(attrs = { classes(Styles.listCard) }) {
            if (!story.coverImageUrl.isNullOrBlank()) {
                PhotoThumbButton(
                    url = story.coverImageUrl!!,
                    alt = story.name,
                    thumbClass = Styles.adminPhraseThumb,
                ) { onPhotoClick(story.coverImageUrl!!, story.name) }
            } else {
                MaterialSymbol("auto_stories", extraClass = Styles.listIcon)
            }
            Div(attrs = { classes(Styles.listDetails) }) {
                H2 { Text(story.name) }
                P {
                    Text(
                        buildString {
                            if (story.category.isNotBlank()) append(story.category).append(" · ")
                            append(
                                when (StoryLogic.adminListStatus(story.copy(publishing = story.publishing || story.id in publishingIds))) {
                                    StoryLogic.LIST_STATUS_PUBLISHING -> strings.adminPublishing
                                    StoryLogic.LIST_STATUS_PUBLISHED -> strings.storiesPublished
                                    else -> strings.storiesDraft
                                },
                            )
                            append(" · ")
                            append(strings.storiesOpens(story.openCount))
                        },
                    )
                }
            }
            Div(attrs = { classes(Styles.adminRowActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick {
                        editing = story
                        showEditor = true
                    }
                }) { Text(strings.adminEdit) }
                val isPublishing = story.publishing || story.id in publishingIds
                if (story.published && !isPublishing) {
                    Button(attrs = {
                        classes(Styles.btnText)
                        attr("type", "button")
                        onClick {
                            scope.launch {
                                try {
                                    StoryApi.unpublish(story.id)
                                    onRefresh()
                                    Toaster.show(strings.adminSaved)
                                } catch (e: Throwable) {
                                    Toaster.show(e.message ?: strings.adminError)
                                }
                            }
                        }
                    }) { Text(strings.adminUnpublish) }
                } else if (!isPublishing) {
                    Button(attrs = {
                        classes(Styles.btnText)
                        attr("type", "button")
                        onClick { publishTarget = story }
                    }) { Text(strings.adminPublish) }
                }
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick { pendingDelete = story }
                }) { Text(strings.adminDelete) }
            }
        }
    }

    StoryEditorDialog(
        visible = showEditor,
        initial = editing,
        onDismiss = {
            showEditor = false
            editing = null
        },
        onSaved = {
            showEditor = false
            editing = null
            onRefresh()
        },
        onPhotoClick = onPhotoClick,
    )
    PublishStoryDialog(
        story = publishTarget,
        onDismiss = { publishTarget = null },
        onStarted = { storyId, jobId ->
            publishTarget = null
            publishingIds = publishingIds + storyId
            onRefresh()
            scope.launch {
                try {
                    watchPublishJob(jobId)
                    Toaster.show(strings.adminSaved)
                } catch (e: Throwable) {
                    Toaster.show(e.message ?: strings.adminError)
                } finally {
                    publishingIds = publishingIds - storyId
                    onRefresh()
                }
            }
        },
    )
    if (pendingDelete != null) {
        ConfirmStoryDelete(
            title = strings.adminDelete,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                val story = pendingDelete ?: return@ConfirmStoryDelete
                pendingDelete = null
                scope.launch {
                    try {
                        StoryApi.deleteStory(story.id)
                        onRefresh()
                        Toaster.show(strings.adminSaved)
                    } catch (e: Throwable) {
                        Toaster.show(e.message ?: strings.adminError)
                    }
                }
            },
        )
    }
}

@Composable
private fun ConfirmStoryDelete(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val strings = Localization.strings
    BottomDialog(visible = true, closeLabel = strings.close, onDismiss = onDismiss) {
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
private fun PublishStoryDialog(
    story: Story?,
    onDismiss: () -> Unit,
    onStarted: (storyId: String, jobId: String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var languageTag by remember(story?.id) { mutableStateOf(story?.languageTag ?: Language.ENGLISH.tag) }
    var busy by remember(story?.id) { mutableStateOf(false) }

    LaunchedEffect(story?.id) {
        val id = story?.id ?: return@LaunchedEffect
        languageTag = runCatching { StoryApi.detectLanguage(id) }.getOrDefault(story.languageTag)
    }

    BottomDialog(visible = story != null, closeLabel = strings.close, onDismiss = { if (!busy) onDismiss() }) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.adminConfirmLanguage) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Select(attrs = {
                classes(Styles.textInput)
                onChange {
                    val el = it.nativeEvent.target as? HTMLSelectElement
                    languageTag = el?.value ?: Language.ENGLISH.tag
                }
            }) {
                Language.entries.forEach { language ->
                    Option(language.tag, attrs = {
                        if (language.tag == languageTag) selected()
                    }) { Text("${flagFor(language)} ${language.displayName}") }
                }
            }
        }
        Div(attrs = { classes(Styles.dialogActions) }) {
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { if (!busy) onDismiss() }
            }) { Text(strings.close) }
            PrimaryButton(
                label = strings.adminPublish,
                icon = "publish",
                enabled = !busy && story != null,
            ) {
                val target = story ?: return@PrimaryButton
                scope.launch {
                    busy = true
                    try {
                        val started = StoryApi.startPublish(target.id, languageTag)
                        val jobId = started.jobId?.trim().orEmpty()
                        if (jobId.isEmpty()) throw StoryApiException(started.error ?: strings.adminError)
                        if (started.status == StoryLogic.PUBLISH_JOB_FAILED) {
                            throw StoryApiException(started.error ?: strings.adminError)
                        }
                        onStarted(target.id, jobId)
                    } catch (e: Throwable) {
                        Toaster.show(e.message ?: strings.adminError)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

private suspend fun watchPublishJob(jobId: String) {
    repeat(180) {
        val job = StoryApi.fetchPublishJob(jobId)
        when (job.status) {
            StoryLogic.PUBLISH_JOB_READY -> return
            StoryLogic.PUBLISH_JOB_FAILED ->
                throw StoryApiException(job.error ?: Localization.strings.adminError)
        }
        delay(2_000)
    }
    throw StoryApiException("Publishing timed out")
}

@Composable
private fun StoryEditorDialog(
    visible: Boolean,
    initial: Story?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onPhotoClick: (url: String, alt: String) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var name by remember(initial?.id, visible) { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember(initial?.id, visible) { mutableStateOf(initial?.category.orEmpty()) }
    var coverUrl by remember(initial?.id, visible) { mutableStateOf(initial?.coverImageUrl.orEmpty()) }
    var pages by remember(initial?.id, visible) { mutableStateOf(initial?.pages ?: emptyList()) }
    var generateFor by remember { mutableStateOf<GenerateStoryTarget?>(null) }
    var generatingCover by remember { mutableStateOf(false) }
    var generatingPages by remember { mutableStateOf(setOf<String>()) }
    var saving by remember { mutableStateOf(false) }

    fun applyGeneratedUrl(target: GenerateStoryTarget, url: String) {
        when (target) {
            GenerateStoryTarget.Cover -> coverUrl = url
            is GenerateStoryTarget.Page -> {
                pages = pages.map { page ->
                    if (page.id == target.pageId) page.copy(photoUrl = url) else page
                }
            }
        }
    }

    fun startGeneration(target: GenerateStoryTarget, request: GenerateStoryImageRequest) {
        generateFor = null
        when (target) {
            GenerateStoryTarget.Cover -> generatingCover = true
            is GenerateStoryTarget.Page -> generatingPages = generatingPages + target.pageId
        }
        scope.launch {
            try {
                val result = StoryApi.generateImage(request)
                applyGeneratedUrl(target, result.imageUrl)
                Toaster.show(strings.adminSaved)
            } catch (e: Throwable) {
                Toaster.show(e.message ?: strings.adminError)
            } finally {
                when (target) {
                    GenerateStoryTarget.Cover -> generatingCover = false
                    is GenerateStoryTarget.Page -> generatingPages = generatingPages - target.pageId
                }
            }
        }
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
        footer = {
            Div(attrs = { classes(Styles.dialogActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick { onDismiss() }
                }) { Text(strings.close) }
                PrimaryButton(
                    label = strings.adminSave,
                    icon = "save",
                    enabled = name.isNotBlank() && !saving,
                ) {
                    scope.launch {
                        saving = true
                        try {
                            val request = StoryUpsertRequest(
                                name = name.trim(),
                                category = category.trim(),
                                coverImageUrl = coverUrl.trim().takeIf { it.isNotEmpty() },
                                pages = pages.map { page ->
                                    StoryPageUpsert(
                                        id = page.id.takeUnless { it.startsWith("page-new-") },
                                        photoUrl = page.photoUrl,
                                        text = page.text,
                                    )
                                },
                            )
                            if (initial == null) StoryApi.createStory(request) else StoryApi.updateStory(initial.id, request)
                            Toaster.show(strings.adminSaved)
                            onSaved()
                        } catch (e: Throwable) {
                            Toaster.show(e.message ?: strings.adminError)
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) {
                Text(if (initial == null) strings.adminAddBook else strings.adminEditBook)
            }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            AdminStoryField(strings.adminBookName, name) { name = it }
            AdminStoryField(strings.adminBookCategory, category, strings.adminBookCategoryPlaceholder) { category = it }
            if (coverUrl.isNotBlank()) {
                PhotoThumbButton(
                    url = coverUrl,
                    alt = name.ifBlank { strings.adminGenerateCover },
                    thumbClass = Styles.adminCoverPreview,
                ) { onPhotoClick(coverUrl, name.ifBlank { strings.adminGenerateCover }) }
                Spacer()
            }
            if (generatingCover) {
                P(attrs = { classes(Styles.homePrompt) }) { Text(strings.adminGenerating) }
                Spacer()
            }
            Div(attrs = { classes(Styles.adminRowActions) }) {
                PrimaryButton(label = strings.adminGenerateCover, icon = "auto_awesome") {
                    generateFor = GenerateStoryTarget.Cover
                }
                Label(attrs = { classes(Styles.btnText) }) {
                    Text(strings.adminUploadPhoto)
                    Input(type = InputType.File) {
                        attr("accept", "image/*")
                        style { property("display", "none") }
                        onChange {
                            val file = (it.nativeEvent.target as? HTMLInputElement)?.firstFileOrNull()
                                ?: return@onChange
                            scope.launch {
                                try {
                                    coverUrl = StoryApi.uploadImage(file, storyId = initial?.id, asCover = true)
                                    Toaster.show(strings.adminSaved)
                                } catch (e: Throwable) {
                                    Toaster.show(e.message ?: strings.adminError)
                                }
                            }
                        }
                    }
                }
            }

            SectionTitle(strings.adminAddPage)
            pages.forEachIndexed { index, page ->
                Div(attrs = { classes(Styles.adminCoverCard) }) {
                    if (!page.photoUrl.isNullOrBlank()) {
                        PhotoThumbButton(
                            url = page.photoUrl!!,
                            alt = page.text.take(80).ifBlank { strings.adminGeneratePhoto },
                            thumbClass = Styles.adminCoverPreview,
                        ) {
                            onPhotoClick(
                                page.photoUrl!!,
                                page.text.take(80).ifBlank { strings.adminGeneratePhoto },
                            )
                        }
                    }
                    if (page.id in generatingPages) {
                        P(attrs = { classes(Styles.homePrompt) }) { Text(strings.adminGenerating) }
                    }
                    TextArea(page.text) {
                        classes(Styles.textArea)
                        onInput { value ->
                            pages = pages.mapIndexed { i, existing ->
                                if (i == index) existing.copy(text = value.value) else existing
                            }
                        }
                    }
                    Div(attrs = { classes(Styles.adminRowActions) }) {
                        Button(attrs = {
                            classes(Styles.btnText)
                            attr("type", "button")
                            onClick { generateFor = GenerateStoryTarget.Page(page.id) }
                        }) { Text(strings.adminGeneratePhoto) }
                        Label(attrs = { classes(Styles.btnText) }) {
                            Text(strings.adminUploadPhoto)
                            Input(type = InputType.File) {
                                attr("accept", "image/*")
                                style { property("display", "none") }
                                onChange {
                                    val file = (it.nativeEvent.target as? HTMLInputElement)?.firstFileOrNull()
                                        ?: return@onChange
                                    scope.launch {
                                        try {
                                            val url = StoryApi.uploadImage(
                                                file,
                                                storyId = initial?.id,
                                                pageId = page.id,
                                            )
                                            pages = pages.mapIndexed { i, existing ->
                                                if (i == index) existing.copy(photoUrl = url) else existing
                                            }
                                        } catch (e: Throwable) {
                                            Toaster.show(e.message ?: strings.adminError)
                                        }
                                    }
                                }
                            }
                        }
                        Button(attrs = {
                            classes(Styles.btnText)
                            attr("type", "button")
                            onClick { pages = pages.filterIndexed { i, _ -> i != index } }
                        }) { Text(strings.adminDelete) }
                    }
                }
            }
            PrimaryButton(label = strings.adminAddPage, icon = "note_add") {
                pages = pages + StoryPage(id = "page-new-${pages.size}", text = "")
            }
        }
    }

    StoryGeneratePhotoDialog(
        visible = generateFor != null,
        storyId = initial?.id,
        pageId = (generateFor as? GenerateStoryTarget.Page)?.pageId,
        asCover = generateFor is GenerateStoryTarget.Cover,
        onDismiss = { generateFor = null },
        onStart = { request ->
            val target = generateFor ?: return@StoryGeneratePhotoDialog
            startGeneration(target, request)
        },
    )
}

private sealed class GenerateStoryTarget {
    data object Cover : GenerateStoryTarget()
    data class Page(val pageId: String) : GenerateStoryTarget()
}

@Composable
internal fun StoryGeneratePhotoDialog(
    visible: Boolean,
    storyId: String?,
    pageId: String?,
    asCover: Boolean,
    onDismiss: () -> Unit,
    onStart: (GenerateStoryImageRequest) -> Unit,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var prompt by remember(visible) { mutableStateOf("") }
    var scenes by remember { mutableStateOf<List<ReferencePhoto>>(emptyList()) }
    var characters by remember { mutableStateOf<List<StoryCharacter>>(emptyList()) }
    var selectedScenes by remember(visible) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCharacters by remember(visible) { mutableStateOf<Set<String>>(emptySet()) }
    var sceneName by remember { mutableStateOf("") }
    var sceneFile by remember { mutableStateOf<File?>(null) }
    var characterName by remember { mutableStateOf("") }
    var characterPrompt by remember { mutableStateOf("") }
    var characterFile by remember { mutableStateOf<File?>(null) }
    var generatingCharacter by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        scenes = runCatching { PhraseApi.fetchReferencePhotos() }.getOrDefault(emptyList())
        characters = runCatching { StoryApi.fetchCharacters() }.getOrDefault(emptyList())
    }

    BottomDialog(
        visible = visible,
        closeLabel = strings.close,
        onDismiss = onDismiss,
        footer = {
            Div(attrs = { classes(Styles.dialogActions) }) {
                Button(attrs = {
                    classes(Styles.btnText)
                    attr("type", "button")
                    onClick { onDismiss() }
                }) { Text(strings.close) }
                PrimaryButton(
                    label = strings.adminGenerate,
                    icon = "auto_awesome",
                    enabled = prompt.isNotBlank(),
                ) {
                    onStart(
                        GenerateStoryImageRequest(
                            prompt = prompt.trim(),
                            scenePhotoIds = selectedScenes.toList(),
                            characterIds = selectedCharacters.toList(),
                            storyId = storyId,
                            pageId = pageId,
                            asCover = asCover,
                        ),
                    )
                }
            }
        },
    ) {
        Div(attrs = { classes(Styles.dialogHeader) }) {
            P(attrs = { classes(Styles.dialogTitle) }) { Text(strings.adminGeneratePhoto) }
        }
        Div(attrs = { classes(Styles.dialogBody) }) {
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.adminGeneratePrompt) }
                TextArea(prompt) {
                    classes(Styles.textArea)
                    onInput { prompt = it.value }
                }
            }
            Label(attrs = { classes(Styles.phraseExpandHint) }) { Text(strings.adminSelectScenes) }
            Div(attrs = { classes(Styles.adminRefGrid) }) {
                scenes.forEach { photo ->
                    val on = photo.id in selectedScenes
                    Button(attrs = {
                        classes(Styles.adminRefTile)
                        if (on) classes(Styles.adminRefTileSelected)
                        attr("type", "button")
                        onClick {
                            selectedScenes = if (on) selectedScenes - photo.id else selectedScenes + photo.id
                        }
                    }) {
                        Img(src = photo.imageUrl, alt = photo.name, attrs = { classes(Styles.adminRefThumb) })
                        Span { Text(photo.name) }
                    }
                }
            }
            AdminStoryField(strings.adminSceneName, sceneName) { sceneName = it }
            Input(type = InputType.File) {
                classes(Styles.textInput)
                attr("accept", "image/*")
                onChange {
                    sceneFile = (it.nativeEvent.target as? HTMLInputElement)?.firstFileOrNull()
                }
            }
            Spacer()
            PrimaryButton(
                label = strings.adminUploadScene,
                icon = "upload",
                enabled = sceneName.isNotBlank() && sceneFile != null,
            ) {
                val file = sceneFile ?: return@PrimaryButton
                scope.launch {
                    try {
                        PhraseApi.uploadReferencePhoto(sceneName.trim(), file)
                        sceneName = ""
                        sceneFile = null
                        scenes = PhraseApi.fetchReferencePhotos()
                        Toaster.show(strings.adminSaved)
                    } catch (e: Throwable) {
                        Toaster.show(e.message ?: strings.adminError)
                    }
                }
            }
            Label(attrs = { classes(Styles.phraseExpandHint) }) { Text(strings.adminSelectCharacters) }
            AdminCharacterGrid(
                characters = characters,
                selectedIds = selectedCharacters,
                onSelect = { character ->
                    selectedCharacters = if (character.id in selectedCharacters) {
                        selectedCharacters - character.id
                    } else {
                        selectedCharacters + character.id
                    }
                },
            )
            AdminStoryField(strings.adminCharacterName, characterName) { characterName = it }
            Div(attrs = { classes(Styles.textField) }) {
                Label(attrs = { classes(Styles.textLabel) }) { Text(strings.adminCharacterPrompt) }
                TextArea(characterPrompt) {
                    classes(Styles.textArea)
                    onInput { characterPrompt = it.value }
                }
            }
            Input(type = InputType.File) {
                classes(Styles.textInput)
                attr("accept", "image/*")
                onChange {
                    characterFile = (it.nativeEvent.target as? HTMLInputElement)?.firstFileOrNull()
                }
            }
            if (generatingCharacter) {
                P(attrs = { classes(Styles.homePrompt) }) { Text(strings.adminGenerating) }
            }
            Spacer()
            Div(attrs = { classes(Styles.adminRowActions) }) {
                PrimaryButton(
                    label = strings.adminUploadCharacter,
                    icon = "upload",
                    enabled = !generatingCharacter && characterName.isNotBlank() && characterFile != null,
                ) {
                    val file = characterFile ?: return@PrimaryButton
                    scope.launch {
                        try {
                            StoryApi.uploadCharacter(characterName.trim(), file)
                            characterName = ""
                            characterPrompt = ""
                            characterFile = null
                            characters = StoryApi.fetchCharacters()
                            Toaster.show(strings.adminSaved)
                        } catch (e: Throwable) {
                            Toaster.show(e.message ?: strings.adminError)
                        }
                    }
                }
                PrimaryButton(
                    label = if (generatingCharacter) strings.adminGenerating else strings.adminGenerateCharacter,
                    icon = "auto_awesome",
                    enabled = !generatingCharacter && characterName.isNotBlank() && characterPrompt.isNotBlank(),
                ) {
                    scope.launch {
                        generatingCharacter = true
                        try {
                            StoryApi.generateCharacter(characterName.trim(), characterPrompt.trim())
                            characterName = ""
                            characterPrompt = ""
                            characterFile = null
                            characters = StoryApi.fetchCharacters()
                            Toaster.show(strings.adminSaved)
                        } catch (e: Throwable) {
                            Toaster.show(e.message ?: strings.adminError)
                        } finally {
                            generatingCharacter = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminCharacterGrid(
    characters: List<StoryCharacter>,
    selectedIds: Set<String> = emptySet(),
    onSelect: ((StoryCharacter) -> Unit)? = null,
    onPhotoClick: ((StoryCharacter) -> Unit)? = null,
    actions: (@Composable (StoryCharacter) -> Unit)? = null,
) {
    Div(attrs = { classes(Styles.adminRefGrid) }) {
        characters.forEach { character ->
            val selected = character.id in selectedIds
            if (onSelect != null) {
                Button(attrs = {
                    classes(Styles.adminRefTile)
                    if (selected) classes(Styles.adminRefTileSelected)
                    attr("type", "button")
                    onClick { onSelect(character) }
                }) {
                    AdminCharacterTileBody(character)
                }
            } else {
                Div(attrs = {
                    classes(Styles.adminRefTile)
                    if (selected) classes(Styles.adminRefTileSelected)
                }) {
                    AdminCharacterTileBody(character, onPhotoClick)
                    actions?.invoke(character)
                }
            }
        }
    }
}

@Composable
private fun AdminCharacterTileBody(
    character: StoryCharacter,
    onPhotoClick: ((StoryCharacter) -> Unit)? = null,
) {
    if (onPhotoClick != null) {
        PhotoThumbButton(
            url = character.imageUrl,
            alt = character.name,
            thumbClass = Styles.adminRefThumb,
        ) { onPhotoClick(character) }
    } else {
        Img(src = character.imageUrl, alt = character.name, attrs = { classes(Styles.adminRefThumb) })
    }
    Span { Text(character.name) }
}

@Composable
private fun AdminStoryField(label: String, value: String, placeholderText: String? = null, onChange: (String) -> Unit) {
    Div(attrs = { classes(Styles.textField) }) {
        Label(attrs = { classes(Styles.textLabel) }) { Text(label) }
        Input(type = InputType.Text) {
            classes(Styles.textInput)
            value(value)
            if (placeholderText != null) attr("placeholder", placeholderText)
            onInput { onChange(it.value) }
        }
    }
}
