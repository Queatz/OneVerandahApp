package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.STUDY_COMPREHEND_SCENE_COUNT
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.StudyComprehendActionKind
import apap.oneverandah.api.StudyComprehendBuildStatus
import apap.oneverandah.api.StudyComprehendEnvironment
import apap.oneverandah.api.StudyComprehendJobResponse
import apap.oneverandah.api.StudyComprehendObject
import apap.oneverandah.api.StudyComprehendObjectAction
import apap.oneverandah.api.StudyComprehendScene
import apap.oneverandah.api.StudyComprehendScenePlayState
import apap.oneverandah.api.StudyComprehendTimeOfDay
import apap.oneverandah.api.StudyComprehendWeather
import apap.oneverandah.api.StudyComprehendWorld
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.completedTaskIds
import apap.oneverandah.api.dropInventoryItem
import apap.oneverandah.api.isObjectPresentOnMap
import apap.oneverandah.api.isSceneCompleted
import apap.oneverandah.api.isTaskCompleted
import apap.oneverandah.api.performAction
import apap.oneverandah.i18n.StudyComprehendStrings
import apap.oneverandah.i18n.UiStrings
import kotlinx.browser.document
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Canvas
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Ol
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.Audio
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.js.Date
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun StudyComprehendTab(
    strings: UiStrings,
    session: StudySessionResponse,
    world: StudyComprehendWorld?,
    jobState: StudyComprehendJobResponse?,
    requestError: String?,
    autoStart: Boolean = true,
    keyboardEnabled: Boolean = true,
    onStartOrRetry: () -> Unit,
) {
    val comprehend = strings.chatComprehend
    val scope = rememberCoroutineScope()

    var webGpuCheck by remember { mutableStateOf<WebGpuSupportResult?>(null) }
    var stageHostElement by remember { mutableStateOf<HTMLElement?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Ephemeral client-only gameplay state (never sent to or persisted on the server)
    var currentSceneIndex by remember(world) { mutableStateOf(0) }
    var maxUnlockedSceneIndex by remember(world) { mutableStateOf(0) }
    var replayCounter by remember(world) { mutableStateOf(0) }
    val sceneStates = remember(world, replayCounter) {
        mutableStateMapOf<String, StudyComprehendScenePlayState>().apply {
            world?.scenes?.forEach { sc ->
                put(sc.id, StudyComprehendScenePlayState.initial(sc))
            }
        }
    }

    var activeObject by remember(currentSceneIndex, replayCounter) { mutableStateOf<StudyComprehendObject?>(null) }
    var toastMessage by remember(currentSceneIndex, replayCounter) { mutableStateOf<String?>(null) }
    var speakingObjectId by remember { mutableStateOf<String?>(null) }
    val speechUrlCache = remember(session.textLanguageTag) { mutableStateMapOf<String, String>() }
    val inFlightSpeech = remember(session.textLanguageTag) { HashMap<String, Deferred<String?>>() }
    val activeAudioRef = remember { arrayOfNulls<Audio>(1) }
    val speakGenerationRef = remember { intArrayOf(0) }
    val lastSpokenTextRef = remember { arrayOfNulls<String>(1) }
    val lastSpokenAtMsRef = remember { doubleArrayOf(0.0) }

    // Check WebGPU support once on mount, and start/retry generation if world is not yet built
    LaunchedEffect(autoStart) {
        val gpuResult = webGpuCheck ?: checkWebGpuSupport().also { webGpuCheck = it }
        if (autoStart && gpuResult.supported && world == null) {
            onStartOrRetry()
        }
    }

    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = {
            isFullscreen = document.fullscreenElement != null &&
                stageHostElement != null &&
                document.fullscreenElement == stageHostElement
        }
        document.addEventListener("fullscreenchange", listener)
        onDispose {
            document.removeEventListener("fullscreenchange", listener)
            activeAudioRef[0]?.pause()
            activeAudioRef[0] = null
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(4500.milliseconds)
            toastMessage = null
        }
    }

    suspend fun resolveSpeechDataUrl(cleanText: String): String? {
        speechUrlCache[cleanText]?.let { return it }
        val existing = inFlightSpeech[cleanText]
        if (existing != null) return existing.await()
        val deferred = scope.async {
            runCatching {
                loadStudySpeechDataUrl(
                    SpeechRequest(
                        text = cleanText,
                        languageTag = session.textLanguageTag,
                    ),
                )
            }.getOrNull()?.also { url ->
                speechUrlCache[cleanText] = url
            }
        }
        inFlightSpeech[cleanText] = deferred
        return try {
            deferred.await()
        } finally {
            if (inFlightSpeech[cleanText] === deferred) {
                inFlightSpeech.remove(cleanText)
            }
        }
    }

    fun toggleFullscreen() {
        val el = stageHostElement ?: return
        if (document.fullscreenElement == el) {
            runCatching { document.exitFullscreen() }
        } else {
            runCatching { el.requestFullscreen() }
        }
    }

    fun speakLine(characterObjId: String, spokenText: String?, prebuiltUrl: String?) {
        val cleanText = spokenText?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val nowMs = Date.now()
        if (lastSpokenTextRef[0] == cleanText && (nowMs - lastSpokenAtMsRef[0]) in 0.0..350.0) {
            return
        }
        lastSpokenTextRef[0] = cleanText
        lastSpokenAtMsRef[0] = nowMs
        speakGenerationRef[0] += 1
        val reqGen = speakGenerationRef[0]

        scope.launch {
            val resolvedUrl = prebuiltUrl?.trim()?.takeIf { it.isNotEmpty() }
                ?: resolveSpeechDataUrl(cleanText)

            if (resolvedUrl.isNullOrBlank() || speakGenerationRef[0] != reqGen) return@launch
            activeAudioRef[0]?.pause()
            val audio = Audio(resolvedUrl)
            activeAudioRef[0] = audio
            speakingObjectId = characterObjId
            audio.onended = { _: Event ->
                if (activeAudioRef[0] === audio) {
                    speakingObjectId = null
                }
            }
            audio.onerror = { _, _, _, _, _ ->
                if (activeAudioRef[0] === audio) {
                    speakingObjectId = null
                }
                null
            }
            audio.currentTime = 0.0
            audio.play().catch {
                if (activeAudioRef[0] === audio) {
                    speakingObjectId = null
                }
            }
        }
    }

    Div(attrs = {
        classes(Styles.studyComprehendShell)
        if (isFullscreen) classes(Styles.studyComprehendFullscreen)
        ref { element ->
            stageHostElement = element
            onDispose {
                if (stageHostElement === element) stageHostElement = null
            }
        }
    }) {
        val gpu = webGpuCheck
        when {
            gpu == null -> {
                ComprehendLoadingCard(comprehend.loadingEngine)
            }

            !gpu.supported -> {
                ComprehendWebGpuUnavailableCard(comprehend, gpu.browserKind)
            }

            world == null -> {
                val isFailed = jobState?.status == StudyComprehendBuildStatus.FAILED || !requestError.isNullOrBlank()
                if (isFailed) {
                    ComprehendFailedCard(
                        comprehend = comprehend,
                        errorDetail = requestError ?: jobState?.error,
                        onRetry = onStartOrRetry,
                    )
                } else if (autoStart || jobState != null) {
                    ComprehendGeneratingCard(comprehend = comprehend, jobState = jobState)
                } else {
                    Div(attrs = { classes(Styles.studyComprehendStatusCard) }) {
                        H4(attrs = { classes(Styles.studyComprehendTitle) }) {
                            Text(comprehend.title)
                        }
                        Button(attrs = {
                            type(ButtonType.Button)
                            classes(Styles.savedItemAction)
                            onClick { onStartOrRetry() }
                        }) {
                            Span(attrs = {
                                classes(Styles.materialSymbols)
                                attr("aria-hidden", "true")
                            }) { Text("explore") }
                            Text(comprehend.tab)
                        }
                    }
                }
            }

            else -> {
                val scenes = world.scenes
                val safeIndex = currentSceneIndex.coerceIn(0, (scenes.size - 1).coerceAtLeast(0))
                val scene = scenes.getOrNull(safeIndex) ?: return@Div
                val playState = sceneStates[scene.id] ?: StudyComprehendScenePlayState.initial(scene)
                val completedTasks = scene.completedTaskIds(playState)
                val sceneDone = scene.isSceneCompleted(playState)

                LaunchedEffect(scene.id, sceneDone) {
                    if (sceneDone && safeIndex + 1 > maxUnlockedSceneIndex && safeIndex + 1 < scenes.size) {
                        maxUnlockedSceneIndex = safeIndex + 1
                    }
                }

                // Quietly pre-warm non-character object targetName TTS clips for responsive object tap / pickup audio
                LaunchedEffect(scene.id, session.textLanguageTag) {
                    val prioritizedNames = scene.objects
                        .filterNot { it.isCharacter }
                        .sortedByDescending { obj ->
                            when {
                                obj.pickable -> 2
                                obj.actions.isNotEmpty() -> 1
                                else -> 0
                            }
                        }
                        .mapNotNull { it.targetName.trim().takeIf(String::isNotEmpty) }
                        .distinct()
                    for (name in prioritizedNames) {
                        if (speechUrlCache.containsKey(name)) continue
                        resolveSpeechDataUrl(name)
                        delay(120.milliseconds)
                    }
                }

                // Top header: Scene indicator, 3-step pills, environment/time/weather metadata, fullscreen toggle
                Div(attrs = { classes(Styles.studyComprehendHeader) }) {
                    Div(attrs = { classes(Styles.studyComprehendHeaderLeft) }) {
                        H4(attrs = { classes(Styles.studyComprehendTitle) }) {
                            Text("${comprehend.sceneIndicator(safeIndex + 1, scenes.size)} — ${scene.title}")
                        }
                        P(attrs = { classes(Styles.studyComprehendMeta) }) {
                            val envLabel = if (scene.environment == StudyComprehendEnvironment.INDOOR) {
                                comprehend.indoorLabel
                            } else {
                                comprehend.outdoorLabel
                            }
                            Text("$envLabel · ${formatTimeOfDay(scene.timeOfDay)} · ${formatWeather(scene.weather)}")
                        }
                    }

                    Div(attrs = { classes(Styles.studyComprehendSceneSteps) }) {
                        scenes.forEachIndexed { idx, sc ->
                            val st = sceneStates[sc.id] ?: StudyComprehendScenePlayState.initial(sc)
                            val done = sc.isSceneCompleted(st)
                            val unlocked = idx <= maxUnlockedSceneIndex || done
                            Button(attrs = {
                                type(ButtonType.Button)
                                classes(Styles.studyComprehendSceneStep)
                                if (idx == safeIndex) classes(Styles.studyComprehendSceneStepActive)
                                else if (done) classes(Styles.studyComprehendSceneStepDone)
                                if (!unlocked) attr("disabled", "true")
                                attr("aria-label", "${comprehend.sceneIndicator(idx + 1, scenes.size)}: ${sc.title}")
                                onClick {
                                    if (unlocked && idx != safeIndex) {
                                        activeAudioRef[0]?.pause()
                                        speakingObjectId = null
                                        currentSceneIndex = idx
                                    }
                                }
                            }) {
                                Span(attrs = {
                                    classes(Styles.materialSymbols)
                                    attr("aria-hidden", "true")
                                    attr("style", "font-size:15px;")
                                }) {
                                    Text(
                                        when {
                                            done -> "check_circle"
                                            unlocked -> "explore"
                                            else -> "lock"
                                        },
                                    )
                                }
                                Text("${idx + 1}")
                            }
                        }

                        Button(attrs = {
                            type(ButtonType.Button)
                            classes(Styles.studyTalkIconButton)
                            val fsLabel = if (isFullscreen) strings.layoutExitFullscreen else strings.layoutFullscreen
                            attr("aria-label", fsLabel)
                            attr("title", fsLabel)
                            onClick { toggleFullscreen() }
                        }) {
                            Span(attrs = {
                                classes(Styles.materialSymbols)
                                attr("aria-hidden", "true")
                            }) {
                                Text(if (isFullscreen) "fullscreen_exit" else "fullscreen")
                            }
                        }
                    }
                }

                // 3D Stage + HUD overlays
                ComprehendSceneStage(
                    comprehend = comprehend,
                    world = world,
                    scene = scene,
                    sceneIndex = safeIndex,
                    totalScenes = scenes.size,
                    nextSceneTitle = scenes.getOrNull(safeIndex + 1)?.title,
                    playState = playState,
                    completedTaskIds = completedTasks,
                    sceneCompleted = sceneDone,
                    speakingObjectId = speakingObjectId,
                    activeObject = activeObject,
                    toastMessage = toastMessage,
                    replayCounter = replayCounter,
                    keyboardEnabled = keyboardEnabled,
                    onSelectActiveObject = { selected ->
                        if (selected != null && selected.id != activeObject?.id) {
                            toastMessage = null
                        }
                        activeObject = selected
                    },
                    onUpdatePlayState = { updated -> sceneStates[scene.id] = updated },
                    onSpeakLine = { charObjId, line, audioUrl -> speakLine(charObjId, line, audioUrl) },
                    onShowToast = { msg -> toastMessage = msg },
                    onAdvanceScene = {
                        if (safeIndex + 1 < scenes.size) {
                            activeAudioRef[0]?.pause()
                            speakingObjectId = null
                            currentSceneIndex = safeIndex + 1
                        }
                    },
                    onReplayAll = {
                        activeAudioRef[0]?.pause()
                        speakingObjectId = null
                        maxUnlockedSceneIndex = 0
                        currentSceneIndex = 0
                        replayCounter += 1
                    },
                )

                P(attrs = { classes(Styles.studyComprehendHintFooter) }) {
                    Text(comprehend.controlsHint)
                }
            }
        }
    }
}

@Composable
private fun ComprehendSceneStage(
    comprehend: StudyComprehendStrings,
    world: StudyComprehendWorld,
    scene: StudyComprehendScene,
    sceneIndex: Int,
    totalScenes: Int,
    nextSceneTitle: String?,
    playState: StudyComprehendScenePlayState,
    completedTaskIds: Set<String>,
    sceneCompleted: Boolean,
    speakingObjectId: String?,
    activeObject: StudyComprehendObject?,
    toastMessage: String?,
    replayCounter: Int,
    keyboardEnabled: Boolean,
    onSelectActiveObject: (StudyComprehendObject?) -> Unit,
    onUpdatePlayState: (StudyComprehendScenePlayState) -> Unit,
    onSpeakLine: (characterObjId: String, spokenText: String?, audioUrl: String?) -> Unit,
    onShowToast: (String) -> Unit,
    onAdvanceScene: () -> Unit,
    onReplayAll: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var canvasElement by remember(scene.id, replayCounter) { mutableStateOf<HTMLCanvasElement?>(null) }
    var controllerRef by remember(scene.id, replayCounter) { mutableStateOf<StudyComprehendSceneController?>(null) }
    var engineLoading by remember(scene.id, replayCounter) { mutableStateOf(true) }
    var engineInitError by remember(scene.id, replayCounter) { mutableStateOf<String?>(null) }

    // Keep latest state & callbacks accessible to the 3D controller without recreating the WebGPU engine
    val latestPlayState = remember { arrayOf(playState) }
    latestPlayState[0] = playState
    LaunchedEffect(playState, controllerRef) {
        controllerRef?.syncPlayState(playState)
    }
    LaunchedEffect(speakingObjectId, controllerRef) {
        controllerRef?.speakingObjectId = speakingObjectId
    }
    LaunchedEffect(keyboardEnabled, controllerRef) {
        controllerRef?.keyboardEnabled = keyboardEnabled
    }

    fun executeAction(target: StudyComprehendObject, action: StudyComprehendObjectAction, offeredItemId: String? = null) {
        val current = latestPlayState[0]
        val outcome = scene.performAction(
            state = current,
            targetObjectId = target.id,
            actionId = action.id,
            offeredInventoryObjectId = offeredItemId ?: current.selectedInventoryObjectId,
        )
        onUpdatePlayState(outcome.state)
        latestPlayState[0] = outcome.state

        val spoken = outcome.spokenTextToPlay?.takeIf { it.isNotBlank() }
        if (spoken != null) {
            onSpeakLine(target.id, spoken, outcome.audioUrlToPlay)
        } else if (action.kind == StudyComprehendActionKind.TAKE && outcome.succeeded) {
            onSpeakLine(target.id, target.targetName, null)
        }
        outcome.feedbackMessage?.takeIf { it.isNotBlank() }?.let { feedback ->
            onShowToast(feedback)
        }
        if (action.kind == StudyComprehendActionKind.TAKE && outcome.succeeded) {
            onSelectActiveObject(null)
        }
    }

    val handleObjectInteracted = remember(scene.id, replayCounter) {
        { interacted: StudyComprehendObject ->
            val current = latestPlayState[0]
            if (interacted.isCharacter) {
                // Always play / repeat what the character said when tapped!
                val listenAction = interacted.actions.firstOrNull { it.kind == StudyComprehendActionKind.LISTEN }
                if (listenAction != null) {
                    executeAction(interacted, listenAction)
                } else {
                    onSpeakLine(interacted.id, interacted.spokenText, interacted.spokenAudioUrl)
                }
                onSelectActiveObject(interacted)
            } else if (interacted.actions.size == 1 &&
                interacted.actions.first().kind == StudyComprehendActionKind.TAKE &&
                scene.isObjectPresentOnMap(interacted, current)
            ) {
                executeAction(interacted, interacted.actions.first())
            } else if (interacted.actions.isNotEmpty()) {
                onSelectActiveObject(interacted)
            }
        }
    }

    DisposableEffect(scene.id, canvasElement, replayCounter) {
        val canvas = canvasElement
        if (canvas == null) {
            onDispose {}
        } else {
            engineLoading = true
            engineInitError = null
            val ctrl = StudyComprehendSceneController(
                canvas = canvas,
                world = world,
                sceneData = scene,
                initialState = latestPlayState[0],
                onObjectTapped = { obj ->
                    if (!obj.isCharacter) {
                        onSpeakLine(obj.id, obj.targetName, null)
                    }
                },
                onObjectInteracted = { obj -> handleObjectInteracted(obj) },
                onPlayerTileChanged = { tx, tz ->
                    val cur = latestPlayState[0]
                    if (cur.playerX != tx || cur.playerZ != tz) {
                        val next = cur.copy(playerX = tx, playerZ = tz)
                        latestPlayState[0] = next
                        onUpdatePlayState(next)
                    }
                },
            )
            controllerRef = ctrl
            val initJob = scope.launch {
                runCatching {
                    ctrl.initialize()
                }.onSuccess {
                    engineLoading = false
                }.onFailure { err ->
                    engineInitError = err.message ?: "WebGPU initialization error"
                    engineLoading = false
                }
            }
            onDispose {
                initJob.cancel()
                controllerRef = null
                ctrl.dispose()
            }
        }
    }

    val spritesById = remember(world) { world.sprites.associateBy { it.id } }
    val objectsById = remember(scene) { scene.objects.associateBy { it.id } }

    Div(attrs = {
        classes(Styles.studyComprehendStage)
        attr("aria-label", scene.title)
    }) {
        Canvas(attrs = {
            classes(Styles.studyComprehendCanvas)
            attr("tabindex", "0")
            ref { element ->
                canvasElement = element
                onDispose {
                    if (canvasElement === element) canvasElement = null
                }
            }
        })

        // Loading / Engine error overlay
        if (engineLoading) {
            Div(attrs = {
                classes(Styles.studyComprehendLoadingOverlay)
                attr("role", "status")
            }) {
                Span(attrs = {
                    classes(Styles.materialSymbols)
                    attr("aria-hidden", "true")
                }) { Text("progress_activity") }
                Span { Text(comprehend.loadingEngine) }
            }
        } else if (engineInitError != null) {
            Div(attrs = { classes(Styles.studyComprehendLoadingOverlay) }) {
                Span(attrs = { classes(Styles.materialSymbols) }) { Text("warning") }
                Span { Text(engineInitError.orEmpty()) }
            }
        }

        // Left-side Task List Panel
        Div(attrs = {
            classes(Styles.studyComprehendTasksPanel)
            attr("aria-label", comprehend.tasksTitle)
        }) {
            Div(attrs = { classes(Styles.studyComprehendTasksHeader) }) {
                Span { Text(comprehend.tasksTitle) }
                Span { Text(comprehend.tasksProgress(completedTaskIds.size, scene.tasks.size)) }
            }
            Ul(attrs = { classes(Styles.studyComprehendTasksList) }) {
                scene.tasks.forEach { task ->
                    val done = scene.isTaskCompleted(task, playState)
                    Li(attrs = {
                        classes(Styles.studyComprehendTaskItem)
                        if (done) classes(Styles.studyComprehendTaskDone)
                    }) {
                        Span(attrs = {
                            classes(Styles.materialSymbols)
                            attr("aria-hidden", "true")
                        }) {
                            Text(if (done) "check_circle" else "radio_button_unchecked")
                        }
                        Span { Text(task.description) }
                    }
                }
            }
        }

        // Top-right Player Inventory Bar
        Div(attrs = {
            classes(Styles.studyComprehendInventoryBar)
            attr("aria-label", comprehend.inventoryTitle)
        }) {
            Div(attrs = { classes(Styles.studyComprehendTasksHeader) }) {
                Span { Text(comprehend.inventoryTitle) }
                if (playState.selectedInventoryObjectId != null) {
                    Button(attrs = {
                        type(ButtonType.Button)
                        classes(Styles.studyComprehendActionBtn)
                        attr("style", "padding:3px 9px;font-size:11px;color:#93c5fd;")
                        onClick {
                            val selectedId = playState.selectedInventoryObjectId ?: return@onClick
                            val dropped = scene.dropInventoryItem(playState, selectedId)
                            latestPlayState[0] = dropped
                            onUpdatePlayState(dropped)
                        }
                    }) {
                        Text(comprehend.dropItem)
                    }
                }
            }
            if (playState.inventoryObjectIds.isEmpty()) {
                Span(attrs = { attr("style", "font-size:12px;color:#94a3b8;") }) {
                    Text(comprehend.inventoryEmpty)
                }
            } else {
                Div(attrs = { classes(Styles.studyComprehendInventoryItems) }) {
                    playState.inventoryObjectIds.forEach { itemId ->
                        val itemObj = objectsById[itemId] ?: return@forEach
                        val spriteUrl = spritesById[itemObj.spriteId]?.imageUrl
                        val isSelected = playState.selectedInventoryObjectId == itemId
                        Button(attrs = {
                            type(ButtonType.Button)
                            classes(Styles.studyComprehendInventoryChip)
                            if (isSelected) classes(Styles.studyComprehendInventoryChipSelected)
                            onClick {
                                val next = playState.copy(selectedInventoryObjectId = itemId)
                                latestPlayState[0] = next
                                onUpdatePlayState(next)
                            }
                        }) {
                            if (!spriteUrl.isNullOrBlank()) {
                                Img(src = spriteUrl, alt = itemObj.targetName, attrs = {
                                    classes(Styles.studyComprehendInventoryThumb)
                                })
                            }
                            Span { Text(itemObj.targetName) }
                        }
                    }
                }
            }
        }

        // Inspect / feedback bubble positioned directly above the object interaction container
        val activeToast = toastMessage?.takeIf { it.isNotBlank() }
        val focused = activeObject?.takeIf { scene.isObjectPresentOnMap(it, playState) }
        if (activeToast != null || focused != null) {
            Div(attrs = { classes(Styles.studyComprehendInteractionStack) }) {
                if (activeToast != null) {
                    Div(attrs = {
                        classes(Styles.studyComprehendToast)
                        attr("role", "status")
                    }) {
                        Text(activeToast)
                    }
                }

                if (focused != null) {
                    Div(attrs = { classes(Styles.studyComprehendActionTray) }) {
                        Div(attrs = { classes(Styles.studyComprehendActionHeader) }) {
                            Span { Text(focused.targetName) }
                            Button(attrs = {
                                type(ButtonType.Button)
                                classes(Styles.studyTalkIconButton)
                                attr("style", "width:28px;height:28px;color:#cbd5e1;")
                                onClick { onSelectActiveObject(null) }
                            }) {
                                Span(attrs = { classes(Styles.materialSymbols) }) { Text("close") }
                            }
                        }
                        Div(attrs = { classes(Styles.studyComprehendActionButtons) }) {
                            if (focused.isCharacter) {
                                Button(attrs = {
                                    type(ButtonType.Button)
                                    classes(Styles.studyComprehendActionBtn)
                                    onClick {
                                        val listenAct = focused.actions.firstOrNull { it.kind == StudyComprehendActionKind.LISTEN }
                                        if (listenAct != null) {
                                            executeAction(focused, listenAct)
                                        } else {
                                            onSpeakLine(focused.id, focused.spokenText, focused.spokenAudioUrl)
                                        }
                                    }
                                }) {
                                    Span(attrs = {
                                        classes(Styles.materialSymbols)
                                        attr("aria-hidden", "true")
                                    }) { Text("volume_up") }
                                    Text(comprehend.listenAgain)
                                }

                                // Offer carried inventory items to character if character has GIVE actions
                                val giveActions = focused.actions.filter { it.kind == StudyComprehendActionKind.GIVE }
                                if (giveActions.isNotEmpty() && playState.inventoryObjectIds.isNotEmpty()) {
                                    playState.inventoryObjectIds.forEach { carriedId ->
                                        val carriedObj = objectsById[carriedId] ?: return@forEach
                                        val matchingAction = giveActions.firstOrNull { it.requiredItemId == carriedId }
                                            ?: giveActions.first()
                                        Button(attrs = {
                                            type(ButtonType.Button)
                                            classes(Styles.studyComprehendActionBtn, Styles.studyComprehendActionBtnPrimary)
                                            onClick {
                                                executeAction(focused, matchingAction, offeredItemId = carriedId)
                                            }
                                        }) {
                                            Span(attrs = {
                                                classes(Styles.materialSymbols)
                                                attr("aria-hidden", "true")
                                            }) { Text("volunteer_activism") }
                                            Text(comprehend.giveItem(carriedObj.targetName))
                                        }
                                    }
                                }

                                // Any additional non-LISTEN / non-GIVE actions on the character
                                focused.actions.filter {
                                    it.kind != StudyComprehendActionKind.LISTEN && it.kind != StudyComprehendActionKind.GIVE
                                }.forEach { act ->
                                    Button(attrs = {
                                        type(ButtonType.Button)
                                        classes(Styles.studyComprehendActionBtn)
                                        onClick { executeAction(focused, act) }
                                    }) {
                                        Text(act.label)
                                    }
                                }
                            } else {
                                focused.actions.forEach { act ->
                                    Button(attrs = {
                                        type(ButtonType.Button)
                                        classes(Styles.studyComprehendActionBtn, Styles.studyComprehendActionBtnPrimary)
                                        onClick { executeAction(focused, act) }
                                    }) {
                                        Text(act.label)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom-center Advance Button when all tasks in the scene are completed (player can still keep playing!)
        if (sceneCompleted) {
            Div(attrs = { classes(Styles.studyComprehendNextSceneWrap) }) {
                if (sceneIndex + 1 < totalScenes && nextSceneTitle != null) {
                    Button(attrs = {
                        type(ButtonType.Button)
                        classes(Styles.studyComprehendNextSceneBtn)
                        onClick { onAdvanceScene() }
                    }) {
                        Text(comprehend.nextSceneButton(nextSceneTitle))
                        Span(attrs = {
                            classes(Styles.materialSymbols)
                            attr("aria-hidden", "true")
                        }) { Text("arrow_forward") }
                    }
                } else {
                    Button(attrs = {
                        type(ButtonType.Button)
                        classes(Styles.studyComprehendNextSceneBtn)
                        onClick { onReplayAll() }
                    }) {
                        Span(attrs = {
                            classes(Styles.materialSymbols)
                            attr("aria-hidden", "true")
                        }) { Text("celebration") }
                        Text("${comprehend.allScenesComplete} · ${comprehend.playAgain}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ComprehendGeneratingCard(
    comprehend: StudyComprehendStrings,
    jobState: StudyComprehendJobResponse?,
) {
    val status = jobState?.status ?: StudyComprehendBuildStatus.GENERATING_SCENES
    val done = jobState?.completedSteps ?: 0
    val total = (jobState?.totalSteps ?: 1).coerceAtLeast(1)
    val percent = if (status == StudyComprehendBuildStatus.GENERATING_SCENES) {
        14
    } else {
        ((done.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(15, 98)
    }

    Div(attrs = {
        classes(Styles.studyComprehendStatusCard)
        attr("role", "status")
        attr("aria-live", "polite")
    }) {
        Div(attrs = { classes(Styles.studyTalkStatus) }) {
            Div(attrs = { classes(Styles.studyTalkBuildHeader) }) {
                Span(attrs = {
                    classes(Styles.materialSymbols, Styles.studyComprehendSpinner)
                    attr("aria-hidden", "true")
                }) { Text("progress_activity") }
                Span {
                    Text(
                        if (status == StudyComprehendBuildStatus.GENERATING_ASSETS) {
                            comprehend.generatingAssets(done, total)
                        } else {
                            comprehend.generatingScenes
                        },
                    )
                }
            }
        }
        Div(attrs = {
            classes(Styles.studyTalkProgressBar)
            attr("role", "progressbar")
            attr("aria-valuemin", "0")
            attr("aria-valuemax", "100")
            attr("aria-valuenow", percent.toString())
        }) {
            Div(attrs = {
                classes(Styles.studyTalkProgressFill)
                attr("style", "width:$percent%;")
            })
        }
    }
}

@Composable
private fun ComprehendLoadingCard(label: String) {
    Div(attrs = {
        classes(Styles.studyComprehendStatusCard)
        attr("role", "status")
    }) {
        Div(attrs = { classes(Styles.studyTalkStatus) }) {
            Div(attrs = { classes(Styles.studyTalkBuildHeader) }) {
                Span(attrs = {
                    classes(Styles.materialSymbols, Styles.studyComprehendSpinner)
                    attr("aria-hidden", "true")
                }) { Text("progress_activity") }
                Span { Text(label) }
            }
        }
    }
}

@Composable
private fun ComprehendFailedCard(
    comprehend: StudyComprehendStrings,
    errorDetail: String?,
    onRetry: () -> Unit,
) {
    Div(attrs = { classes(Styles.studyComprehendStatusCard, Styles.studySessionError) }) {
        H4(attrs = { classes(Styles.studyComprehendTitle) }) {
            Text(comprehend.failedTitle)
        }
        if (!errorDetail.isNullOrBlank()) {
            P { Text(errorDetail) }
        }
        P { Text(comprehend.failedHint) }
        Button(attrs = {
            type(ButtonType.Button)
            classes(Styles.savedItemAction)
            onClick { onRetry() }
        }) {
            Span(attrs = {
                classes(Styles.materialSymbols)
                attr("aria-hidden", "true")
            }) { Text("refresh") }
            Text(comprehend.retry)
        }
    }
}

@Composable
private fun ComprehendWebGpuUnavailableCard(
    comprehend: StudyComprehendStrings,
    browserKind: WebGpuBrowserKind,
) {
    val orderedSteps = when (browserKind) {
        WebGpuBrowserKind.FIREFOX -> listOf(
            comprehend.webGpuFirefoxSteps,
            comprehend.webGpuChromeSteps,
            comprehend.webGpuSafariSteps,
        )
        WebGpuBrowserKind.SAFARI -> listOf(
            comprehend.webGpuSafariSteps,
            comprehend.webGpuChromeSteps,
            comprehend.webGpuFirefoxSteps,
        )
        else -> listOf(
            comprehend.webGpuChromeSteps,
            comprehend.webGpuFirefoxSteps,
            comprehend.webGpuSafariSteps,
        )
    }

    Div(attrs = { classes(Styles.studyComprehendStatusCard) }) {
        H4(attrs = { classes(Styles.studyComprehendTitle) }) {
            Text(comprehend.webGpuUnavailableTitle)
        }
        P(attrs = { classes(Styles.studyComprehendMeta) }) {
            Text(comprehend.webGpuUnavailableIntro)
        }
        Ol(attrs = { classes(Styles.studyComprehendWebGpuSteps) }) {
            orderedSteps.forEach { step ->
                Li { Text(step) }
            }
        }
    }
}

private fun formatTimeOfDay(timeOfDay: StudyComprehendTimeOfDay): String = when (timeOfDay) {
    StudyComprehendTimeOfDay.MORNING -> "Morning"
    StudyComprehendTimeOfDay.DAYTIME -> "Daytime"
    StudyComprehendTimeOfDay.EVENING -> "Evening"
    StudyComprehendTimeOfDay.NIGHT -> "Night"
}

private fun formatWeather(weather: StudyComprehendWeather): String = when (weather) {
    StudyComprehendWeather.SUNNY -> "Sunny"
    StudyComprehendWeather.CLOUDY -> "Cloudy"
    StudyComprehendWeather.RAINY -> "Rainy"
    StudyComprehendWeather.DUSTY -> "Dusty"
    StudyComprehendWeather.HOT -> "Hot"
    StudyComprehendWeather.COLD -> "Cold"
    StudyComprehendWeather.MISTY -> "Misty"
    StudyComprehendWeather.SNOWY -> "Snowy"
    StudyComprehendWeather.BREEZY -> "Breezy"
}
