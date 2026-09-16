package apap.oneverandah.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoScrollerTest {
    @Test
    fun stepClampsAtEndsWhenNotWrapping() {
        assertEquals(0, stepPhotoIndex(0, 3, -1, wrap = false))
        assertEquals(2, stepPhotoIndex(2, 3, 1, wrap = false))
        assertEquals(1, stepPhotoIndex(0, 3, 1, wrap = false))
    }

    @Test
    fun stepWrapsAroundWhenRequested() {
        assertEquals(2, stepPhotoIndex(0, 3, -1, wrap = true))
        assertEquals(0, stepPhotoIndex(2, 3, 1, wrap = true))
        assertEquals(0, stepPhotoIndex(0, 0, 1, wrap = true))
    }

    @Test
    fun swipeAdvancesWhenPastThreshold() {
        assertEquals(1, photoIndexAfterSwipe(0, 3, offsetPx = -50.0, viewportWidthPx = 200.0))
        assertEquals(0, photoIndexAfterSwipe(1, 3, offsetPx = 50.0, viewportWidthPx = 200.0))
    }

    @Test
    fun swipeSnapsBackWhenBelowThreshold() {
        assertEquals(1, photoIndexAfterSwipe(1, 3, offsetPx = -20.0, viewportWidthPx = 200.0))
        assertEquals(1, photoIndexAfterSwipe(1, 3, offsetPx = 20.0, viewportWidthPx = 200.0))
    }

    @Test
    fun swipeDoesNotWrapAtEnds() {
        assertEquals(0, photoIndexAfterSwipe(0, 3, offsetPx = 80.0, viewportWidthPx = 200.0))
        assertEquals(2, photoIndexAfterSwipe(2, 3, offsetPx = -80.0, viewportWidthPx = 200.0))
    }

    @Test
    fun resistedOffsetSoftensPullPastEnds() {
        assertEquals(14.0, resistedSwipeOffset(0, 3, 40.0), 0.001)
        assertEquals(-14.0, resistedSwipeOffset(2, 3, -40.0), 0.001)
        assertEquals(-40.0, resistedSwipeOffset(0, 3, -40.0), 0.001)
    }

    @Test
    fun wheelIgnoresMostlyVerticalDeltas() {
        assertEquals(0.0, horizontalWheelDelta(3.0, 40.0, shiftKey = false))
        assertEquals(40.0, horizontalWheelDelta(40.0, 3.0, shiftKey = false))
    }

    @Test
    fun shiftWheelMapsVerticalToHorizontal() {
        assertEquals(40.0, horizontalWheelDelta(0.0, 40.0, shiftKey = true))
        assertEquals(-12.0, horizontalWheelDelta(-12.0, 10.0, shiftKey = true))
    }

    @Test
    fun wheelAdvancesAfterThresholdAndAccumulatesBelow() {
        assertEquals(0 to 40.0, photoIndexAfterWheel(0, 3, 20.0, 20.0, 48.0))
        assertEquals(1 to 0.0, photoIndexAfterWheel(0, 3, 0.0, 48.0, 48.0))
        assertEquals(0 to 0.0, photoIndexAfterWheel(1, 3, 0.0, -48.0, 48.0))
    }

    @Test
    fun wheelDoesNotWrapAtEnds() {
        assertEquals(2 to 48.0, photoIndexAfterWheel(2, 3, 0.0, 80.0, 48.0))
        assertEquals(0 to -48.0, photoIndexAfterWheel(0, 3, 0.0, -80.0, 48.0))
    }

    @Test
    fun dialogKeysWrapAndEscapeDismisses() {
        assertEquals(0, photoIndexForDialogKey("ArrowRight", 2, 3))
        assertEquals(2, photoIndexForDialogKey("ArrowLeft", 0, 3))
        assertNull(photoIndexForDialogKey("ArrowLeft", 0, 1))
        assertNull(photoIndexForDialogKey("Enter", 1, 3))
        assertTrue(isPhotoDialogDismissKey("Escape"))
        assertFalse(isPhotoDialogDismissKey("ArrowLeft"))
    }

    @Test
    fun horizontalDragChangesIndexAndSuppressesClick() {
        val swipe = PhotoSwipeState()
        swipe.press(100.0, 50.0)
        assertFalse(swipe.move(104.0, 51.0))
        assertTrue(swipe.move(40.0, 52.0))
        assertTrue(swipe.suppressClick)
        assertEquals(1, swipe.finish(0, 3, 200.0))
        assertFalse(swipe.active)
    }

    @Test
    fun verticalDragLeavesIndexUnchanged() {
        val swipe = PhotoSwipeState()
        swipe.press(100.0, 50.0)
        assertFalse(swipe.move(102.0, 120.0))
        assertEquals(1, swipe.finish(1, 3, 200.0))
        assertTrue(swipe.suppressClick)
    }
}
