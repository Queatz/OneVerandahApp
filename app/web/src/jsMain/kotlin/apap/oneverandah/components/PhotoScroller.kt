package apap.oneverandah.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import apap.oneverandah.Styles
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.WheelEvent
import kotlin.math.abs

internal enum class PhotoSwipeAxis { Horizontal, Vertical }

/** Pointer-drag state for the photo scroller. Compose reads [offsetX] after [move]. */
internal class PhotoSwipeState {
    var offsetX: Double = 0.0
        private set
    var active: Boolean = false
        private set
    var axis: PhotoSwipeAxis? = null
        private set
    var suppressClick: Boolean = false
        private set

    private var startX = 0.0
    private var startY = 0.0

    fun press(x: Double, y: Double) {
        active = true
        startX = x
        startY = y
        offsetX = 0.0
        axis = null
        suppressClick = false
    }

    /** @return true when a horizontal swipe is in progress. */
    fun move(x: Double, y: Double, slopPx: Double = 8.0): Boolean {
        if (!active) return false
        val dx = x - startX
        val dy = y - startY
        if (axis == null) {
            if (abs(dx) < slopPx && abs(dy) < slopPx) return false
            axis = if (abs(dx) >= abs(dy)) PhotoSwipeAxis.Horizontal else PhotoSwipeAxis.Vertical
            suppressClick = true
        }
        if (axis != PhotoSwipeAxis.Horizontal) return false
        offsetX = dx
        return true
    }

    fun finish(
        index: Int,
        count: Int,
        viewportWidthPx: Double,
        thresholdFraction: Double = 0.2,
    ): Int {
        if (!active) return index.coerceAtLeast(0)
        val next = if (axis == PhotoSwipeAxis.Horizontal) {
            photoIndexAfterSwipe(index, count, offsetX, viewportWidthPx, thresholdFraction)
        } else {
            index
        }
        cancel()
        return next
    }

    fun cancel() {
        active = false
        offsetX = 0.0
        axis = null
    }
}

internal fun stepPhotoIndex(index: Int, count: Int, delta: Int, wrap: Boolean): Int {
    if (count <= 0) return 0
    val next = index + delta
    return if (wrap) {
        val mod = next % count
        if (mod < 0) mod + count else mod
    } else {
        next.coerceIn(0, count - 1)
    }
}

internal fun photoIndexAfterSwipe(
    index: Int,
    count: Int,
    offsetPx: Double,
    viewportWidthPx: Double,
    thresholdFraction: Double = 0.2,
): Int {
    if (count <= 1 || viewportWidthPx <= 0.0) return index.coerceAtLeast(0)
    val threshold = viewportWidthPx * thresholdFraction
    val delta = when {
        offsetPx <= -threshold -> 1
        offsetPx >= threshold -> -1
        else -> 0
    }
    return stepPhotoIndex(index, count, delta, wrap = false)
}

internal fun resistedSwipeOffset(
    index: Int,
    count: Int,
    offsetPx: Double,
    resist: Double = 0.35,
): Double {
    if (count <= 0) return 0.0
    return when {
        index <= 0 && offsetPx > 0 -> offsetPx * resist
        index >= count - 1 && offsetPx < 0 -> offsetPx * resist
        else -> offsetPx
    }
}

internal fun horizontalWheelDelta(deltaX: Double, deltaY: Double, shiftKey: Boolean): Double {
    if (shiftKey && abs(deltaY) >= abs(deltaX)) return deltaY
    if (abs(deltaX) > abs(deltaY)) return deltaX
    return 0.0
}

internal fun photoIndexAfterWheel(
    index: Int,
    count: Int,
    accumulatedPx: Double,
    deltaPx: Double,
    thresholdPx: Double = 48.0,
): Pair<Int, Double> {
    if (count <= 1 || thresholdPx <= 0.0) return index.coerceAtLeast(0) to 0.0
    var acc = accumulatedPx + deltaPx
    var next = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    while (acc >= thresholdPx) {
        val stepped = stepPhotoIndex(next, count, 1, wrap = false)
        if (stepped == next) {
            acc = thresholdPx
            break
        }
        next = stepped
        acc -= thresholdPx
    }
    while (acc <= -thresholdPx) {
        val stepped = stepPhotoIndex(next, count, -1, wrap = false)
        if (stepped == next) {
            acc = -thresholdPx
            break
        }
        next = stepped
        acc += thresholdPx
    }
    return next to acc
}

internal fun photoIndexForDialogKey(key: String, index: Int, count: Int): Int? {
    if (count <= 1) return null
    return when (key) {
        "ArrowLeft" -> stepPhotoIndex(index, count, -1, wrap = true)
        "ArrowRight" -> stepPhotoIndex(index, count, 1, wrap = true)
        else -> null
    }
}

internal fun isPhotoDialogDismissKey(key: String): Boolean = key == "Escape"

internal fun eventClientPoint(event: Event): Pair<Double, Double>? {
    val dyn = event.asDynamic()
    if (event.type.startsWith("touch")) {
        val touch = dyn.touches.item(0) ?: dyn.changedTouches.item(0) ?: return null
        return (touch.clientX as Double) to (touch.clientY as Double)
    }
    return (dyn.clientX as Double) to (dyn.clientY as Double)
}

/**
 * Horizontal photo gallery: one large photo, optional arrows and dots, and a
 * thumbnail strip. Mouse and touch dragging, plus trackpad/shift-wheel
 * horizontal scrolling, swipe between photos.
 */
@Composable
fun PhotoScroller(
    photos: List<String>,
    alt: String = "",
    previousLabel: String = "Previous photo",
    nextLabel: String = "Next photo",
    onPhotoClick: ((index: Int) -> Unit)? = null,
) {
    val urls = remember(photos) { photos.filter { it.isNotBlank() } }
    if (urls.isEmpty()) return

    var index by remember { mutableStateOf(0) }
    var offsetX by remember { mutableStateOf(0.0) }
    var dragging by remember { mutableStateOf(false) }
    var pointerActive by remember { mutableStateOf(false) }
    var wheelAccum by remember { mutableStateOf(0.0) }
    var viewportEl by remember { mutableStateOf<HTMLElement?>(null) }
    val swipe = remember { PhotoSwipeState() }
    val canNavigate = urls.size > 1

    LaunchedEffect(urls) {
        index = index.coerceIn(0, urls.lastIndex)
        swipe.cancel()
        offsetX = 0.0
        dragging = false
        pointerActive = false
        wheelAccum = 0.0
    }

    LaunchedEffect(index) { wheelAccum = 0.0 }

    fun finishSwipe() {
        if (!swipe.active && !pointerActive) return
        val width = viewportEl?.clientWidth?.toDouble() ?: 0.0
        index = swipe.finish(index, urls.size, width)
        offsetX = 0.0
        dragging = false
        pointerActive = false
    }

    DisposableEffect(pointerActive) {
        if (!pointerActive) return@DisposableEffect onDispose {}
        val move: (Event) -> Unit = { event ->
            val point = eventClientPoint(event)
            if (point != null && swipe.move(point.first, point.second)) {
                offsetX = swipe.offsetX
                dragging = true
            }
        }
        val up: (Event) -> Unit = { finishSwipe() }
        window.addEventListener("mousemove", move)
        window.addEventListener("mouseup", up)
        window.addEventListener("touchmove", move, true)
        window.addEventListener("touchend", up, true)
        window.addEventListener("touchcancel", up, true)
        onDispose {
            window.removeEventListener("mousemove", move)
            window.removeEventListener("mouseup", up)
            window.removeEventListener("touchmove", move, true)
            window.removeEventListener("touchend", up, true)
            window.removeEventListener("touchcancel", up, true)
        }
    }

    val dragOffset = if (dragging) resistedSwipeOffset(index, urls.size, offsetX) else 0.0

    Div(attrs = { classes(Styles.photoScroller) }) {
        Div(attrs = {
            classes(Styles.photoScrollerViewport)
            attr("role", "group")
            attr("aria-roledescription", "carousel")
            attr("aria-label", alt.ifBlank { "Photos" })
            ref { element ->
                viewportEl = element
                onDispose { if (viewportEl === element) viewportEl = null }
            }
            onMouseDown { e ->
                swipe.press(e.clientX.toDouble(), e.clientY.toDouble())
                pointerActive = true
            }
            onTouchStart { e ->
                val touch = e.touches.item(0) ?: return@onTouchStart
                swipe.press(touch.clientX.toDouble(), touch.clientY.toDouble())
                pointerActive = true
            }
            onTouchMove { e ->
                val touch = e.touches.item(0) ?: return@onTouchMove
                if (swipe.move(touch.clientX.toDouble(), touch.clientY.toDouble())) {
                    offsetX = swipe.offsetX
                    dragging = true
                }
            }
            onTouchEnd { finishSwipe() }
            onWheel { event ->
                if (!canNavigate || pointerActive) return@onWheel
                val wheel = event.nativeEvent as WheelEvent
                val delta = horizontalWheelDelta(wheel.deltaX, wheel.deltaY, wheel.shiftKey)
                if (delta == 0.0) return@onWheel
                event.preventDefault()
                event.stopPropagation()
                val (next, acc) = photoIndexAfterWheel(index, urls.size, wheelAccum, delta)
                wheelAccum = acc
                if (next != index) {
                    swipe.cancel()
                    offsetX = 0.0
                    dragging = false
                    index = next
                }
            }
            onClick {
                if (!swipe.suppressClick) onPhotoClick?.invoke(index)
            }
        }) {
            Div(attrs = {
                classes(Styles.photoScrollerTrack)
                if (dragging) classes(Styles.photoScrollerDragging)
                style {
                    property("transform", "translateX(calc(${-index * 100}% + ${dragOffset}px))")
                }
            }) {
                urls.forEachIndexed { i, url ->
                    Div(attrs = {
                        classes(Styles.photoScrollerSlide)
                        attr("aria-hidden", (i != index).toString())
                    }) {
                        Img(src = url, alt = if (i == index) alt else "", attrs = {
                            classes(Styles.photoScrollerImage)
                            attr("draggable", "false")
                        })
                    }
                }
            }
            if (canNavigate) {
                Button(attrs = {
                    classes(Styles.photoScrollerNav, Styles.photoScrollerNavPrev)
                    attr("type", "button")
                    attr("aria-label", previousLabel)
                    onMouseDown { it.stopPropagation() }
                    onTouchStart { it.stopPropagation() }
                    onClick {
                        it.stopPropagation()
                        index = stepPhotoIndex(index, urls.size, -1, wrap = true)
                    }
                }) { MaterialSymbol("chevron_left") }
                Button(attrs = {
                    classes(Styles.photoScrollerNav, Styles.photoScrollerNavNext)
                    attr("type", "button")
                    attr("aria-label", nextLabel)
                    onMouseDown { it.stopPropagation() }
                    onTouchStart { it.stopPropagation() }
                    onClick {
                        it.stopPropagation()
                        index = stepPhotoIndex(index, urls.size, 1, wrap = true)
                    }
                }) { MaterialSymbol("chevron_right") }
                Div(attrs = { classes(Styles.photoScrollerDots) }) {
                    urls.forEachIndexed { i, _ ->
                        Button(attrs = {
                            classes(Styles.photoScrollerDot)
                            if (i == index) classes(Styles.photoScrollerDotActive)
                            attr("type", "button")
                            attr("aria-label", "Photo ${i + 1}")
                            attr("aria-current", (i == index).toString())
                            onMouseDown { it.stopPropagation() }
                            onTouchStart { it.stopPropagation() }
                            onClick {
                                it.stopPropagation()
                                index = i
                            }
                        })
                    }
                }
            }
        }
        if (canNavigate) {
            Div(attrs = { classes(Styles.photoScrollerThumbs) }) {
                urls.forEachIndexed { i, url ->
                    Button(attrs = {
                        classes(Styles.photoScrollerThumb)
                        if (i == index) classes(Styles.photoScrollerThumbActive)
                        attr("type", "button")
                        attr("aria-label", alt.ifBlank { "Photo ${i + 1}" })
                        attr("aria-current", (i == index).toString())
                        onClick { index = i }
                    }) {
                        Img(src = url, alt = "", attrs = { attr("draggable", "false") })
                    }
                }
            }
        }
    }
}
