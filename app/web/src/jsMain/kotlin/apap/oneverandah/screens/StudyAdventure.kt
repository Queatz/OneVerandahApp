package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.StudyAdventureProgress
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import apap.oneverandah.i18n.UiStrings
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.web.attributes.ref
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.js.Date
import kotlin.random.Random

internal const val ADVENTURE_MAX_HEALTH = 7

internal data class AdventureWord(
    val targetText: String,
    val sourceText: String,
)

internal data class AdventureSentence(
    val text: String,
    val sourceText: String,
    val words: List<AdventureWord>,
    val imageUrl: String? = null,
    val translation: String = "",
)

internal data class AdventureLevel(
    val sentence: AdventureSentence,
    val sentences: List<AdventureSentence>,
) {
    val imageUrl: String?
        get() = sentence.imageUrl
}

internal fun buildAdventureLevels(session: StudySessionResponse): List<AdventureLevel> =
    session.sentences.mapNotNull { mainSentence ->
        val mainWords = mainSentence.tokens.mapNotNull(::adventureWordFromToken)
        if (mainWords.isEmpty()) return@mapNotNull null
        val main = AdventureSentence(
            text = mainSentence.text,
            sourceText = mainWords.joinToString(" ") { it.sourceText },
            words = mainWords,
            imageUrl = mainSentence.imageUrl,
            translation = mainSentence.translation.trim(),
        )
        val examples = linkedMapOf<String, AdventureSentence>()
        mainSentence.tokens
            .filter { it.status == StudyTokenStatus.READY && it.text.isNotBlank() }
            .flatMap { it.exampleSentences }
            .forEach { example ->
                adventureExampleSentence(example, mainSentence.imageUrl)?.let { built ->
                    if (built.text !in examples) examples[built.text] = built
                }
            }
        AdventureLevel(sentence = main, sentences = listOf(main) + examples.values)
    }

private fun adventureWordFromToken(token: StudyToken): AdventureWord? {
    if (token.status != StudyTokenStatus.READY) return null
    val target = token.text.trim().takeIf(String::isNotEmpty) ?: return null
    val source = listOf(token.literalMeaning.trim(), token.contextualMeaning.trim())
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("; ")
        .takeIf(String::isNotEmpty) ?: return null
    return AdventureWord(targetText = target, sourceText = source)
}

private fun adventureExampleSentence(
    example: StudyExampleSentence,
    imageUrl: String?,
): AdventureSentence? {
    val text = example.text.trim().takeIf(String::isNotEmpty) ?: return null
    val words = example.tokens.mapNotNull { token ->
        val target = token.text.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val source = listOf(token.translation.trim(), token.contextualMeaning.trim())
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("; ")
            .takeIf(String::isNotEmpty) ?: return@mapNotNull null
        AdventureWord(targetText = target, sourceText = source)
    }
    if (words.isEmpty()) return null
    return AdventureSentence(
        text = text,
        sourceText = words.joinToString(" ") { it.sourceText },
        words = words,
        imageUrl = imageUrl,
        translation = example.translation.trim(),
    )
}

internal fun adventureTimerMs(health: Int): Int =
    (15_000 - health.coerceIn(0, ADVENTURE_MAX_HEALTH - 1) * 2_000).coerceAtLeast(3_000)

internal fun pickAdventurePrompt(
    health: List<Int>,
    previous: Int? = null,
    random: Random = Random.Default,
): Int? {
    val unfinished = health.indices.filter { health[it] < ADVENTURE_MAX_HEALTH }
    if (unfinished.isEmpty()) return null
    val alternatives = unfinished.filter { it != previous }.ifEmpty { unfinished }
    return alternatives[random.nextInt(alternatives.size)]
}

internal fun applyAdventureClick(
    health: List<Int>,
    words: List<AdventureWord>,
    promptIndex: Int,
    clickedIndex: Int,
    stage: Int,
): List<Int> {
    if (promptIndex !in health.indices || clickedIndex !in words.indices || words.size != health.size) return health
    val promptText = if (stage == 1) words[promptIndex].targetText else words[promptIndex].sourceText
    val clickedText = if (stage == 1) words[clickedIndex].targetText else words[clickedIndex].sourceText
    val correct = promptText == clickedText
    return health.toMutableList().also { updated ->
        val affectedIndex = if (correct) promptIndex else promptIndex
        updated[affectedIndex] = if (correct) {
            (updated[affectedIndex] + 1).coerceAtMost(ADVENTURE_MAX_HEALTH)
        } else {
            (updated[affectedIndex] - 1).coerceAtLeast(0)
        }
    }
}

internal fun applyAdventureTimeout(health: List<Int>, wordIndex: Int): List<Int> =
    health.toMutableList().also { updated ->
        if (wordIndex in updated.indices) updated[wordIndex] = (updated[wordIndex] - 1).coerceAtLeast(0)
    }

internal fun adventureUnlockedLevels(progress: StudyAdventureProgress?, levelCount: Int): Set<Int> {
    if (levelCount <= 0) return emptySet()
    val completed = progress?.completedLevels?.coerceIn(0, levelCount) ?: 0
    return (0 until minOf(levelCount, completed + 1)).toSet()
}

internal fun mergeAdventureProgress(
    current: StudyAdventureProgress?,
    incoming: StudyAdventureProgress?,
): StudyAdventureProgress? {
    if (current == null) return incoming?.normalizedOrNull()
    if (incoming == null) return current.normalizedOrNull()
    val old = current.normalizedOrNull() ?: return incoming.normalizedOrNull()
    val new = incoming.normalizedOrNull() ?: return old
    return when {
        new.completedLevels > old.completedLevels -> new
        new.completedLevels < old.completedLevels -> old
        new.updatedAt >= old.updatedAt -> new
        else -> old
    }.copy(completedLevels = maxOf(old.completedLevels, new.completedLevels))
}

internal fun adventureStageIntro(levelLabel: String, stageIndex: Int, totalStages: Int): String {
    val total = totalStages.coerceAtLeast(1)
    val stage = stageIndex.coerceIn(1, total)
    return "$levelLabel • Stage $stage / $total"
}

private enum class AdventureScreen { OVERWORLD, INTRO, PLAYING, STAGE_REVIEW, STAGE_CLEAR, SENTENCE_CLEAR, LEVEL_COMPLETE, COMPLETE }

@Composable
internal fun StudyAdventureTab(
    session: StudySessionResponse,
    progress: StudyAdventureProgress?,
    onAdventureProgress: (StudyAdventureProgress) -> Unit,
    strings: UiStrings,
) {
    val levels = remember(session) { buildAdventureLevels(session) }
    val copy = strings.chatAdventure
    val scope = rememberCoroutineScope()
    val audio = remember(session.text) { mutableStateMapOf<String, String>() }
    var activeLevelIndex by remember(session.text) { mutableStateOf<Int?>(null) }
    var screen by remember(session.text) { mutableStateOf(AdventureScreen.OVERWORLD) }
    var sentenceIndex by remember(session.text) { mutableStateOf(0) }
    var stage by remember(session.text) { mutableStateOf(1) }
    var health by remember(session.text) { mutableStateOf(emptyList<Int>()) }
    var promptIndex by remember(session.text) { mutableStateOf<Int?>(null) }
    var celebratingIndex by remember(session.text) { mutableStateOf<Int?>(null) }
    var celebrationJob by remember(session.text) { mutableStateOf<Job?>(null) }
    var remainingMs by remember(session.text) { mutableStateOf(15_000) }
    var loadingAudioCount by remember(session.text) { mutableStateOf(0) }
    var totalAudioCount by remember(session.text) { mutableStateOf(0) }
    var audioReady by remember(session.text) { mutableStateOf(false) }
    var adventureElement by remember { mutableStateOf<HTMLElement?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    val latestFullscreen by rememberUpdatedState(isFullscreen)
    val latestElement by rememberUpdatedState(adventureElement)
    val currentLevel = activeLevelIndex?.let(levels::getOrNull)
    val currentSentence = currentLevel?.sentences?.getOrNull(sentenceIndex)

    fun savePointer(levelIndex: Int, sentence: Int, currentStage: Int, completed: Int = progress?.completedLevels ?: 0) {
        onAdventureProgress(
            StudyAdventureProgress(
                completedLevels = completed.coerceIn(0, levels.size),
                levelIndex = levelIndex.coerceAtLeast(0),
                sentenceIndex = sentence.coerceAtLeast(0),
                stage = currentStage.coerceIn(1, 2),
                updatedAt = Date.now().toLong(),
            ),
        )
    }

    fun speakTargetWord(word: AdventureWord, onEnded: () -> Unit = {}): Boolean {
        AdventureSfx.click()
        val url = audio[word.targetText] ?: return false
        playAudio(url, restart = true, onEnded = onEnded)
        return true
    }

    fun cancelCelebration() {
        celebrationJob?.cancel()
        celebrationJob = null
        celebratingIndex = null
    }

    fun selectLevel(levelIndex: Int) {
        cancelCelebration()
        AdventureSfx.click()
        val unlocked = adventureUnlockedLevels(progress, levels.size)
        if (levelIndex !in unlocked) return
        activeLevelIndex = levelIndex
        screen = AdventureScreen.INTRO
        val shouldResume = progress?.levelIndex == levelIndex && levelIndex == progress.completedLevels
        sentenceIndex = if (shouldResume) progress.sentenceIndex.coerceIn(0, levels[levelIndex].sentences.lastIndex) else 0
        stage = if (shouldResume) progress.stage.coerceIn(1, 2) else 1
        health = emptyList()
        promptIndex = null
        audioReady = false
    }

    fun exitToOverworld() {
        cancelCelebration()
        AdventureSfx.click()
        if (isFullscreen) exitWatchFullscreen()
        screen = AdventureScreen.OVERWORLD
        activeLevelIndex = null
        stopActiveStudyAudio()
    }

    fun startCurrentLevel() {
        val levelIndex = activeLevelIndex ?: return
        val sentence = levels.getOrNull(levelIndex)?.sentences?.getOrNull(sentenceIndex) ?: return
        AdventureSfx.click()
        health = List(sentence.words.size) { 0 }
        promptIndex = pickAdventurePrompt(health)
        remainingMs = adventureTimerMs(0)
        screen = AdventureScreen.PLAYING
        savePointer(levelIndex, sentenceIndex, stage)
        scope.launch {
            audio[sentence.text]?.let { playAudio(it, restart = true) }
        }
    }

    fun continueAfterStage() {
        val levelIndex = activeLevelIndex ?: return
        val sentence = currentSentence ?: return
        stage = 2
        health = List(sentence.words.size) { 0 }
        promptIndex = pickAdventurePrompt(health)
        remainingMs = adventureTimerMs(0)
        screen = AdventureScreen.PLAYING
        savePointer(levelIndex, sentenceIndex, 2)
        promptIndex?.let { index ->
            val word = sentence.words[index]
            audio[word.targetText]?.let { playAudio(it, restart = true) }
        }
    }

    fun advanceSentence() {
        val levelIndex = activeLevelIndex ?: return
        if (currentLevel == null) return
        AdventureSfx.click()
        if (sentenceIndex < currentLevel.sentences.lastIndex) {
            sentenceIndex += 1
            stage = 1
            screen = AdventureScreen.INTRO
            audioReady = true
            savePointer(levelIndex, sentenceIndex, 1)
        } else {
            AdventureSfx.levelClear()
            val completed = maxOf(progress?.completedLevels ?: 0, levelIndex + 1).coerceAtMost(levels.size)
            savePointer(levelIndex + 1, 0, 1, completed)
            screen = if (levelIndex == levels.lastIndex) AdventureScreen.COMPLETE else AdventureScreen.LEVEL_COMPLETE
        }
    }

    fun goToNextLevel() {
        val next = activeLevelIndex?.plus(1) ?: return
        if (next >= levels.size) {
            screen = AdventureScreen.COMPLETE
        } else {
            selectLevel(next)
        }
    }

    fun continueAfterCorrectAnswer(prompt: Int) {
        if (health.all { it >= ADVENTURE_MAX_HEALTH }) {
            screen = AdventureScreen.STAGE_REVIEW
            promptIndex = null
        } else {
            promptIndex = pickAdventurePrompt(health, prompt)
            remainingMs = adventureTimerMs(promptIndex?.let { health[it] } ?: 0)
        }
    }

    fun answerWord(clickedIndex: Int) {
        if (celebratingIndex != null) {
            AdventureSfx.click()
            return
        }
        val sentence = currentSentence ?: return
        val prompt = promptIndex ?: return
        val clickedWord = sentence.words.getOrNull(clickedIndex) ?: return
        val correct = (if (stage == 1) clickedWord.targetText else clickedWord.sourceText) ==
                (if (stage == 1) sentence.words[prompt].targetText else sentence.words[prompt].sourceText)
        health = applyAdventureClick(health, sentence.words, prompt, clickedIndex, stage)
        if (correct) {
            celebratingIndex = clickedIndex
            val audioEnded = CompletableDeferred<Unit>()
            val celebrationStartedAt = Date.now()
            val audioStarted = speakTargetWord(clickedWord) { audioEnded.complete(Unit) }
            AdventureSfx.correct(health.getOrElse(prompt) { 0 })
            celebrationJob = scope.launch {
                if (audioStarted) withTimeoutOrNull(4_000) { audioEnded.await() }
                val minimumDisplayMs = 600 - (Date.now() - celebrationStartedAt).toLong()
                if (minimumDisplayMs > 0) delay(minimumDisplayMs)
                if (celebratingIndex != clickedIndex) return@launch
                celebratingIndex = null
                celebrationJob = null
                continueAfterCorrectAnswer(prompt)
            }
        } else {
            speakTargetWord(clickedWord)
            AdventureSfx.wrong()
            remainingMs = adventureTimerMs(health[prompt])
        }
    }

    LaunchedEffect(activeLevelIndex) {
        val level = activeLevelIndex?.let(levels::getOrNull) ?: return@LaunchedEffect
        val speechTexts = (level.sentences.flatMap { sentence -> listOf(sentence.text) + sentence.words.map { it.targetText } })
            .map(String::trim).filter(String::isNotEmpty).distinct()
        totalAudioCount = speechTexts.size
        loadingAudioCount = 0
        audioReady = speechTexts.isEmpty()
        val semaphore = Semaphore(3)
        coroutineScope {
            speechTexts.map { text ->
                async {
                    if (text !in audio) {
                        semaphore.withPermit {
                            val dataUrl = try {
                                loadStudySpeechDataUrl(SpeechRequest(text, session.textLanguageTag))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                null
                            }
                            dataUrl?.let { audio[text] = it }
                        }
                    }
                    loadingAudioCount += 1
                }
            }.awaitAll()
        }
        audioReady = true
    }

    LaunchedEffect(screen, currentSentence?.text, audioReady) {
        if (screen != AdventureScreen.INTRO || !audioReady) return@LaunchedEffect
        currentSentence?.text?.let { text ->
            audio[text]?.let { playAudio(it, restart = true) }
        }
    }

    LaunchedEffect(screen, stage, promptIndex, health, sentenceIndex, activeLevelIndex, celebratingIndex) {
        if (screen != AdventureScreen.PLAYING || promptIndex == null || celebratingIndex != null) return@LaunchedEffect
        val targetIndex = promptIndex ?: return@LaunchedEffect
        remainingMs = adventureTimerMs(health.getOrElse(targetIndex) { 0 })
        while (remainingMs > 0) {
            delay(100)
            remainingMs = (remainingMs - 100).coerceAtLeast(0)
        }
        val sentence = currentSentence ?: return@LaunchedEffect
        health = applyAdventureTimeout(health, targetIndex)
        AdventureSfx.timeout()
        remainingMs = adventureTimerMs(health[targetIndex])
        promptIndex = targetIndex
        if (sentence.words.isEmpty()) screen = AdventureScreen.INTRO
    }

    DisposableEffect(Unit) {
        val onKey: (Event) -> Unit = { event ->
            val key = event.asDynamic().key as? String
            if ((key == "Escape" || key == "Esc") && latestFullscreen) {
                isFullscreen = false
                exitWatchFullscreen()
            }
        }
        val onFullscreen: (Event) -> Unit = {
            val current = currentFullscreenElement()
            val element = latestElement
            when {
                element != null && current === element -> isFullscreen = true
                latestFullscreen && current == null -> isFullscreen = false
            }
        }
        window.addEventListener("keydown", onKey)
        document.addEventListener("fullscreenchange", onFullscreen)
        document.addEventListener("webkitfullscreenchange", onFullscreen)
        onDispose {
            window.removeEventListener("keydown", onKey)
            document.removeEventListener("fullscreenchange", onFullscreen)
            document.removeEventListener("webkitfullscreenchange", onFullscreen)
            if (latestElement != null && currentFullscreenElement() === latestElement) exitWatchFullscreen()
            celebrationJob?.cancel()
            stopActiveStudyAudio()
        }
    }

    Div(attrs = {
        classes(Styles.studyAdventureShell)
        if (isFullscreen) classes(Styles.studyAdventureFullscreen)
        ref { element ->
            adventureElement = element
            onDispose { if (adventureElement === element) adventureElement = null }
        }
    }) {
        Div(attrs = { classes(Styles.studyAdventureHeader) }) {
            if (screen != AdventureScreen.OVERWORLD) {
                Button(attrs = {
                    classes(Styles.studyAdventureIconButton)
                    attr("type", "button")
                    attr("aria-label", copy.exit)
                    onClick { exitToOverworld() }
                }) { Text("←") }
            }
            Span(attrs = { classes(Styles.studyAdventureTitle) }) { Text(copy.tab) }
            Button(attrs = {
                classes(Styles.studyAdventureIconButton)
                attr("type", "button")
                attr("aria-label", if (isFullscreen) strings.chatWatchExitFullscreen else strings.chatWatchFullscreen)
                attr("aria-pressed", isFullscreen.toString())
                onClick {
                    AdventureSfx.click()
                    if (isFullscreen) {
                        isFullscreen = false
                        exitWatchFullscreen()
                    } else {
                        adventureElement?.let(::requestWatchFullscreen)
                    }
                }
            }) { Text(if (isFullscreen) "⤢" else "⛶") }
        }

        when (screen) {
            AdventureScreen.OVERWORLD -> {
                if (levels.isEmpty()) {
                    Div(attrs = { classes(Styles.studyAdventureEmpty) }) { Text(copy.preparing) }
                } else {
                    val unlocked = adventureUnlockedLevels(progress, levels.size)
                    Div(attrs = { classes(Styles.studyAdventureGrid) }) {
                        levels.forEachIndexed { index, level ->
                            val done = index < (progress?.completedLevels ?: 0)
                            val isUnlocked = index in unlocked
                            Button(attrs = {
                                classes(Styles.studyAdventureLevel)
                                if (!isUnlocked) classes(Styles.studyAdventureLocked)
                                attr("type", "button")
                                attr("aria-label", if (isUnlocked) copy.level(index + 1) else "${copy.level(index + 1)} ${copy.locked}")
                                attr("style", level.imageUrl?.let { "background-image:linear-gradient(180deg,rgba(0,0,0,.1),rgba(0,0,0,.65)),url('$it')" }.orEmpty())
                                onClick { selectLevel(index) }
                            }) {
                                Span(attrs = { classes(Styles.studyAdventureLevelNumber) }) { Text(copy.level(index + 1)) }
                                Span(attrs = { classes(Styles.studyAdventureLevelStatus) }) {
                                    Text(when {
                                        !isUnlocked -> "🔒"
                                        done -> "✓"
                                        else -> "⏳"
                                    })
                                }
                            }
                        }
                    }
                }
            }
            AdventureScreen.INTRO -> {
                val levelIndex = activeLevelIndex ?: 0
                val sentence = currentSentence
                val totalStages = currentLevel?.sentences?.size ?: 1
                Div(attrs = { classes(Styles.studyAdventureCard, Styles.studyAdventureIntro) }) {
                    P(attrs = { classes(Styles.studyAdventureStage) }) {
                        Text(adventureStageIntro(copy.level(levelIndex + 1), sentenceIndex + 1, totalStages))
                    }
                    if (sentence != null) {
                        Button(attrs = {
                            classes(Styles.studyAdventurePromptSentence)
                            attr("type", "button")
                            attr("aria-label", strings.chatStudyPlaySentence)
                            if (!audioReady) attr("disabled", "")
                            onClick { audio[sentence.text]?.let { playAudio(it, restart = true) } }
                        }) { Text(sentence.text) }
                        sentence.translation.takeIf(String::isNotBlank)?.let { translation ->
                            P(attrs = { classes(Styles.studyAdventureTranslation) }) { Text(translation) }
                        }
                    }
                    if (!audioReady) {
                        P(attrs = { classes(Styles.studyAdventureHint) }) {
                            Text(copy.loadingAudio(loadingAudioCount, totalAudioCount))
                        }
                    }
                    val canStart = audioReady && sentence != null
                    Button(attrs = {
                        classes(Styles.studyAdventurePrimary)
                        attr("type", "button")
                        if (!canStart) attr("disabled", "")
                        onClick { if (canStart) startCurrentLevel() }
                    }) {
                        Text(
                            when {
                                progress?.levelIndex == levelIndex && levelIndex == progress.completedLevels -> copy.resume
                                levelIndex < (progress?.completedLevels ?: 0) -> copy.replay
                                else -> copy.start
                            },
                        )
                    }
                }
            }
            AdventureScreen.PLAYING -> {
                val level = currentLevel
                val sentence = currentSentence
                if (sentence != null) {
                    Div(attrs = { classes(Styles.studyAdventurePlay) }) {
                        level.imageUrl?.let { url ->
                            Div(attrs = {
                                classes(Styles.studyAdventureBackdrop)
                                attr("aria-hidden", "true")
                                attr("style", "background-image:url('$url')")
                            })
                        }
                        Div(attrs = { classes(Styles.studyAdventureTop) }) {
                            Span(attrs = { classes(Styles.studyAdventureStage) }) {
                                Text("${sentenceIndex + 1} / ${level.sentences.size} · ${if (stage == 1) copy.stageTargetToSource else copy.stageSourceToTarget}")
                            }
                            Div(attrs = { classes(Styles.studyAdventureProgressTrack) }) {
                                val sentenceProgress = ((sentenceIndex + 1).toDouble() / level.sentences.size * 100).toInt().coerceIn(0, 100)
                                Div(attrs = { classes(Styles.studyAdventureProgressFill); attr("style", "width:${sentenceProgress}%") })
                            }
                        }
                        Div(attrs = { classes(Styles.studyAdventureWords) }) {
                            sentence.words.forEachIndexed { index, word ->
                                val shownWord = if (stage == 1) word.targetText else word.sourceText
                                Button(attrs = {
                                    classes(Styles.studyAdventureWord)
                                    if (index == celebratingIndex) classes(Styles.studyAdventureWordCorrect)
                                    attr("type", "button")
                                    onClick { answerWord(index) }
                                }) {
                                    Span(attrs = { classes(Styles.studyAdventureWordText) }) { Text(shownWord) }
                                    Span(attrs = { classes(Styles.studyAdventureHealth) }) {
                                        repeat(ADVENTURE_MAX_HEALTH) { segment ->
                                            Span(attrs = {
                                                classes(Styles.studyAdventureHealthSegment)
                                                if (segment < health.getOrElse(index) { 0 }) classes(Styles.studyAdventureHealthFilled)
                                            })
                                        }
                                    }
                                }
                            }
                        }
                        val currentPrompt = promptIndex?.let(sentence.words::getOrNull)
                        if (currentPrompt != null) {
                            Div(attrs = { classes(Styles.studyAdventurePrompt) }) {
                                P(attrs = { classes(Styles.studyAdventureHint) }) {
                                    Text(if (stage == 1) copy.tapWordHint else copy.tapMeaningHint)
                                }
                                if (celebratingIndex != null) {
                                    Div(attrs = { classes(Styles.studyAdventureCorrect) }) {
                                        Span(attrs = { classes(Styles.studyAdventureCorrectMark) }) { Text("✓") }
                                        Span { Text(copy.correct) }
                                    }
                                } else {
                                    P(attrs = { classes(Styles.studyAdventurePromptWord) }) {
                                        Text(if (stage == 1) currentPrompt.sourceText else currentPrompt.targetText)
                                    }
                                    Div(attrs = { classes(Styles.studyAdventureTimer) }) {
                                        Div(attrs = { classes(Styles.studyAdventureTimerFill); attr("style", "width:${(remainingMs.toDouble() / adventureTimerMs(health.getOrElse(promptIndex!!) { 0 }) * 100).toInt().coerceIn(0, 100)}%") })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AdventureScreen.STAGE_REVIEW -> {
                val sentence = currentSentence
                if (sentence != null) {
                    Div(attrs = { classes(Styles.studyAdventureCard, Styles.studyAdventureMessage, Styles.studyAdventureReview) }) {
                        P(attrs = { classes(Styles.studyAdventureReviewSentence) }) {
                            var cursor = 0
                            sentence.words.forEach { word ->
                                val wordStart = sentence.text.indexOf(word.targetText, cursor)
                                if (wordStart >= cursor) {
                                    if (wordStart > cursor) Text(sentence.text.substring(cursor, wordStart))
                                    Button(attrs = {
                                        classes(Styles.studyAdventureReviewWord)
                                        attr("type", "button")
                                        onClick { speakTargetWord(word) }
                                    }) { Text(word.targetText) }
                                    cursor = wordStart + word.targetText.length
                                }
                            }
                            if (cursor < sentence.text.length) Text(sentence.text.substring(cursor))
                        }
                        Button(attrs = {
                            classes(Styles.studyAdventurePrimary)
                            attr("type", "button")
                            onClick {
                                AdventureSfx.stageClear()
                                screen = if (stage == 1) AdventureScreen.STAGE_CLEAR else AdventureScreen.SENTENCE_CLEAR
                            }
                        }) { Text(copy.resume) }
                    }
                }
            }
            AdventureScreen.STAGE_CLEAR -> adventureMessage(copy.stageClear, copy.resume) { continueAfterStage() }
            AdventureScreen.SENTENCE_CLEAR -> adventureMessage(copy.stageClear, copy.nextSentence) { advanceSentence() }
            AdventureScreen.LEVEL_COMPLETE -> {
                val nextExists = (activeLevelIndex ?: 0) < levels.lastIndex
                adventureMessage(
                    copy.levelComplete((activeLevelIndex ?: 0) + 1),
                    if (nextExists) copy.nextLevel else copy.adventureComplete,
                    { goToNextLevel() },
                )
            }
            AdventureScreen.COMPLETE -> adventureMessage(copy.adventureComplete, copy.returnToOverworld, ::exitToOverworld)
        }
    }
}

@Composable
private fun adventureMessage(title: String, action: String, onAction: () -> Unit) {
    Div(attrs = { classes(Styles.studyAdventureCard, Styles.studyAdventureMessage) }) {
        P(attrs = { classes(Styles.studyAdventureMessageTitle) }) { Text(title) }
        Button(attrs = {
            classes(Styles.studyAdventurePrimary)
            attr("type", "button")
            onClick {
                AdventureSfx.click()
                onAction()
            }
        }) { Text(action) }
    }
}

private object AdventureSfx {
    private var context: dynamic = null

    private fun tone(frequency: Double, durationMs: Int, volume: Double = 0.09) {
        runCatching {
            if (context == null) context = newAdventureAudioContext()
            val oscillator = context.createOscillator()
            val gain = context.createGain()
            val now = context.currentTime as Double
            oscillator.type = "sine"
            oscillator.frequency.setValueAtTime(frequency, now)
            gain.gain.setValueAtTime(volume, now)
            gain.gain.exponentialRampToValueAtTime(0.001, now + durationMs / 1000.0)
            oscillator.connect(gain)
            gain.connect(context.destination)
            oscillator.start(now)
            oscillator.stop(now + durationMs / 1000.0)
        }
    }

    fun click() = tone(520.0, 45, 0.05)
    fun correct(health: Int) = tone(520.0 + health.coerceIn(0, ADVENTURE_MAX_HEALTH) * 65.0, 110)
    fun wrong() = tone(170.0, 180, 0.08)
    fun timeout() = tone(120.0, 250, 0.1)
    fun stageClear() {
        tone(620.0, 120)
        tone(830.0, 180)
    }
    fun levelClear() {
        tone(520.0, 120)
        tone(700.0, 130)
        tone(920.0, 220)
    }
}

private fun newAdventureAudioContext(): dynamic = js("new (window.AudioContext || window.webkitAudioContext)()")
