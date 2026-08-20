package apap.oneverandah.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import apap.oneverandah.api.ChallengeApi
import apap.oneverandah.challenges.ChallengeDetailResponse
import apap.oneverandah.challenges.ChallengeLogic
import apap.oneverandah.challenges.ChallengeSubmissionView
import apap.oneverandah.components.MaterialSymbol
import apap.oneverandah.components.Scaffold
import apap.oneverandah.i18n.Localization
import apap.oneverandah.state.Toaster
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.WheelEvent
import kotlin.random.Random

private data class FlyingEmoji(val id: Int, val glyph: String, val left: Int)

@Composable
fun ChallengePlayerScreen(
    challengeId: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
) {
    val strings = Localization.strings
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<ChallengeDetailResponse?>(null) }
    var index by remember { mutableStateOf(0) }
    var caption by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var showEmojis by remember { mutableStateOf(false) }
    var flying by remember { mutableStateOf<List<FlyingEmoji>>(emptyList()) }
    var videoEl by remember { mutableStateOf<HTMLVideoElement?>(null) }
    var dragStartY by remember { mutableStateOf<Double?>(null) }
    var lastAdvanceAt by remember { mutableStateOf(0.0) }
    var enterOpen by remember { mutableStateOf(false) }

    LaunchedEffect(challengeId) {
        detail = ChallengeApi.fetchChallenge(challengeId)
        index = 0
        if (detail == null || detail?.submissions.isNullOrEmpty()) onBack()
    }

    val submissions = detail?.submissions.orEmpty()
    val current = submissions.getOrNull(index)
    val previous = submissions.getOrNull(index - 1)
    val next = submissions.getOrNull(index + 1)

    fun resetPlaybackChrome() {
        caption = ""
        paused = false
        showEmojis = false
        flying = emptyList()
    }

    fun goNext() {
        val now = window.performance.now()
        if (!ChallengeLogic.canAdvance(now.toLong(), lastAdvanceAt.toLong())) return
        val total = detail?.submissions.orEmpty().size
        val following = ChallengeLogic.nextIndexOrDone(index, total)
        if (following != null) {
            lastAdvanceAt = now
            index = following
            resetPlaybackChrome()
        } else if (ChallengeLogic.shouldExitAfterLast(index, total)) {
            lastAdvanceAt = now
            onBack()
        }
    }

    fun goPrev() {
        val now = window.performance.now()
        if (!ChallengeLogic.canAdvance(now.toLong(), lastAdvanceAt.toLong())) return
        val prior = ChallengeLogic.previousIndex(index) ?: return
        lastAdvanceAt = now
        index = prior
        resetPlaybackChrome()
    }

    fun burst(submission: ChallengeSubmissionView) {
        val glyphs = ChallengeLogic.flyingEmojis(submission.emojis)
        flying = glyphs.mapIndexed { i, glyph ->
            FlyingEmoji(id = i, glyph = glyph, left = 8 + Random.nextInt(84))
        }
    }

    val latestOnBack = rememberUpdatedState(onBack)
    val latestGoNext = rememberUpdatedState { goNext() }
    val latestGoPrev = rememberUpdatedState { goPrev() }

    DisposableEffect(Unit) {
        val onKey: (Event) -> Unit = { event ->
            val key = event.asDynamic().key as? String
            if (key == "Escape") latestOnBack.value()
            if (key == "ArrowDown" || key == "ArrowUp" || key == " ") {
                event.preventDefault()
                if (key == "ArrowDown") latestGoNext.value()
                if (key == "ArrowUp") latestGoPrev.value()
            }
        }
        window.addEventListener("keydown", onKey)
        onDispose { window.removeEventListener("keydown", onKey) }
    }

    Scaffold(
        title = detail?.challenge?.name?.ifBlank { strings.challengesTitle } ?: strings.challengesTitle,
        onBack = onBack,
        onHome = onHome,
        showChatAction = false,
        showPhrasesAction = false,
        lockScroll = true,
        scrollKey = "challenge-player",
    ) {
    Div(attrs = { classes(Styles.challengePlayer) }) {
        if (current == null) {
            Div(attrs = { classes(Styles.challengePlayerEmpty) }) {
                Text(strings.challengesLoading)
            }
            return@Div
        }

        Div(attrs = {
            classes(Styles.challengeStage)
            if (!current.thumbnailUrl.isNullOrBlank()) {
                style {
                    property("background-image", "url('${current.thumbnailUrl}')")
                }
            }
            onClick {
                val video = videoEl ?: return@onClick
                if (video.paused) {
                    video.play()
                    paused = false
                } else {
                    video.pause()
                    paused = true
                }
            }
            onTouchStart { event ->
                dragStartY = event.touches.item(0)?.clientY?.toDouble()
            }
            onTouchEnd { event ->
                val start = dragStartY
                val end = event.changedTouches.item(0)?.clientY?.toDouble()
                dragStartY = null
                if (start != null && end != null) {
                    when (ChallengeLogic.swipeGoesNext(end - start)) {
                        true -> goNext()
                        false -> goPrev()
                        null -> Unit
                    }
                }
            }
            onWheel { event ->
                event.preventDefault()
                val wheel = event.nativeEvent as WheelEvent
                if (wheel.deltaY > 40) goNext()
                else if (wheel.deltaY < -40) goPrev()
            }
        }) {
            submissions.forEachIndexed { i, nearby ->
                if (nearby.videoUrl.isNotBlank() && kotlin.math.abs(i - index) <= 1) {
                    key(nearby.id) {
                        ChallengeVideo(
                            url = nearby.videoUrl,
                            poster = nearby.thumbnailUrl,
                            active = i == index,
                            onReady = { el ->
                                if (i == index) {
                                    videoEl = el
                                    burst(nearby)
                                }
                            },
                            onTime = { ms ->
                                if (i == index) {
                                    caption = ChallengeLogic.captionAt(
                                        ChallengeLogic.captionWords(nearby.words, nearby.transcript, nearby.durationMs),
                                        ms,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            flying.forEach { flake ->
                Span(attrs = {
                    classes(Styles.challengeFlyEmoji)
                    style {
                        property("left", "${flake.left}%")
                        property("animation-delay", "${flake.id * 40}ms")
                    }
                }) { Text(flake.glyph) }
            }

            Button(attrs = {
                classes(Styles.challengeClose)
                attr("type", "button")
                attr("aria-label", strings.close)
                onClick { onBack() }
            }) { MaterialSymbol("close") }

            Div(attrs = { classes(Styles.challengeTopMeta) }) {
                if (current.medal != null) {
                    Span(attrs = { classes(Styles.challengeMedal, medalClass(current.medal!!)) }) {
                        Text(medalLabel(current.medal!!))
                    }
                }
                Span(attrs = { classes(Styles.challengeVoteCount) }) {
                    Text("${current.voteCount}")
                }
            }

            if (paused) {
                Div(attrs = { classes(Styles.challengePauseHint) }) { MaterialSymbol("play_arrow") }
            }

            Div(attrs = { classes(Styles.challengeBottom) }) {
            Div(attrs = { classes(Styles.challengeOverlayBar) }) {
                Div(attrs = { classes(Styles.challengeName) }) { Text(current.challengerName) }
                if (previous != null || next != null) {
                    Div(attrs = { classes(Styles.challengeNavBtns) }) {
                        if (previous != null) {
                            Button(attrs = {
                                classes(Styles.challengeNextBtn)
                                attr("type", "button")
                                attr("aria-label", strings.challengesPrevious)
                                onClick { event ->
                                    event.stopPropagation()
                                    goPrev()
                                }
                            }) { MaterialSymbol("keyboard_arrow_up") }
                        }
                        if (next != null) {
                            Button(attrs = {
                                classes(Styles.challengeNextBtn)
                                attr("type", "button")
                                attr("aria-label", strings.challengesNext)
                                onClick { event ->
                                    event.stopPropagation()
                                    goNext()
                                }
                            }) { MaterialSymbol("keyboard_arrow_down") }
                        }
                    }
                }
            }
            if (caption.isNotBlank()) {
                Div(attrs = { classes(Styles.challengeCaption) }) { Text(caption) }
            }

            Div(attrs = {
                classes(Styles.challengeDock)
                onClick { event -> event.stopPropagation() }
            }) {
            if (showEmojis) {
                Div(attrs = { classes(Styles.challengeEmojiRow) }) {
                    ChallengeLogic.EMOJI_IDS.forEach { id ->
                        Button(attrs = {
                            classes(Styles.challengeEmojiBtn)
                            if (current.myEmoji == id) classes(Styles.challengeEmojiBtnActive)
                            attr("type", "button")
                            onClick {
                                scope.launch {
                                    try {
                                        detail = ChallengeApi.vote(challengeId, current.id, id)
                                        showEmojis = false
                                        detail?.submissions?.getOrNull(index)?.let(::burst)
                                    } catch (t: Throwable) {
                                        Toaster.show(t.message ?: strings.challengesVoteError)
                                    }
                                }
                            }
                        }) { Text(ChallengeLogic.emojiGlyph(id)) }
                    }
                }
            }
            Button(attrs = {
                classes(Styles.challengeVoteBtn)
                if (current.votedByMe) classes(Styles.challengeVoteBtnOn)
                attr("type", "button")
                onClick {
                    if (current.mine) {
                        Toaster.show(strings.challengesVoteError)
                    } else {
                        showEmojis = !showEmojis
                    }
                }
            }) {
                MaterialSymbol(if (current.votedByMe) "favorite" else "favorite_border")
                Text(if (current.votedByMe) strings.challengesVoted else strings.challengesVote)
            }
            Button(attrs = {
                classes(Styles.btnText)
                attr("type", "button")
                onClick { enterOpen = true }
            }) { Text(strings.challengesEnter) }
            }
            }
        }

        ChallengeUploadDialog(
            visible = enterOpen,
            challengeId = challengeId,
            onDismiss = { enterOpen = false },
            onUploaded = {
                enterOpen = false
                scope.launch {
                    ChallengeApi.fetchChallenge(challengeId)?.let { detail = it }
                }
            },
        )
    }
    }

    LaunchedEffect(flying) {
        if (flying.isNotEmpty()) {
            delay(2_400)
            flying = emptyList()
        }
    }
}

@Composable
private fun ChallengeVideo(
    url: String,
    poster: String?,
    active: Boolean,
    onReady: (HTMLVideoElement) -> Unit,
    onTime: (Long) -> Unit,
) {
    var videoEl by remember { mutableStateOf<HTMLVideoElement?>(null) }
    val latestOnTime = rememberUpdatedState(onTime)
    val latestOnReady = rememberUpdatedState(onReady)
    val latestActive = rememberUpdatedState(active)

    LaunchedEffect(active, videoEl) {
        val video = videoEl ?: return@LaunchedEffect
        if (active) {
            video.muted = false
            video.currentTime = 0.0
            video.play()
            latestOnReady.value(video)
        } else {
            video.pause()
            video.muted = true
        }
    }

    Div(attrs = {
        if (active) classes(Styles.challengeVideoWrap) else classes(Styles.challengePreload)
        ref { element ->
            val video = kotlinx.browser.document.createElement("video") as HTMLVideoElement
            video.setAttribute("playsinline", "true")
            video.setAttribute("webkit-playsinline", "true")
            video.controls = false
            video.loop = true
            video.preload = "auto"
            video.autoplay = false
            video.muted = !active
            if (!poster.isNullOrBlank()) video.poster = poster
            video.src = url
            video.className = Styles.challengeVideo
            var lastTime = 0.0
            val onTimeUpdate: (Event) -> Unit = {
                lastTime = video.currentTime
                if (latestActive.value) latestOnTime.value((video.currentTime * 1000).toLong())
            }
            val onSeeking: (Event) -> Unit = {
                val target = video.currentTime
                if (
                    !ChallengeLogic.isNaturalLoopSeek(target, lastTime) &&
                    kotlin.math.abs(target - lastTime) > 0.35
                ) {
                    video.currentTime = lastTime
                } else if (ChallengeLogic.isNaturalLoopSeek(target, lastTime)) {
                    lastTime = target
                }
            }
            val onEnded: (Event) -> Unit = {
                lastTime = 0.0
                video.currentTime = 0.0
                if (latestActive.value) video.play()
            }
            val onLoaded: (Event) -> Unit = {
                if (latestActive.value) latestOnReady.value(video)
            }
            video.addEventListener("timeupdate", onTimeUpdate)
            video.addEventListener("seeking", onSeeking)
            video.addEventListener("ended", onEnded)
            video.addEventListener("loadeddata", onLoaded)
            element.appendChild(video)
            videoEl = video
            video.load()
            onDispose {
                video.pause()
                video.removeEventListener("timeupdate", onTimeUpdate)
                video.removeEventListener("seeking", onSeeking)
                video.removeEventListener("ended", onEnded)
                video.removeEventListener("loadeddata", onLoaded)
                video.src = ""
                element.removeChild(video)
                videoEl = null
            }
        }
    })
}

private fun medalLabel(medal: String): String = when (medal) {
    ChallengeLogic.MEDAL_GOLD -> "Gold"
    ChallengeLogic.MEDAL_SILVER -> "Silver"
    ChallengeLogic.MEDAL_BRONZE -> "Bronze"
    else -> medal
}

private fun medalClass(medal: String): String = when (medal) {
    ChallengeLogic.MEDAL_GOLD -> Styles.challengeMedalGold
    ChallengeLogic.MEDAL_SILVER -> Styles.challengeMedalSilver
    else -> Styles.challengeMedalBronze
}
