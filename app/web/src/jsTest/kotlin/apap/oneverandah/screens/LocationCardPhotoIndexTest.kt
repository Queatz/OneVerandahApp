package apap.oneverandah.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class LocationCardPhotoIndexTest {
    @Test
    fun singlePhotoOrEmptyViewportStaysAtZero() {
        assertEquals(0, locationCardPhotoIndex(120.0, 100.0, 1))
        assertEquals(0, locationCardPhotoIndex(120.0, 0.0, 3))
        assertEquals(0, locationCardPhotoIndex(120.0, 100.0, 0))
    }

    @Test
    fun roundsToNearestPhoto() {
        assertEquals(0, locationCardPhotoIndex(40.0, 100.0, 3))
        assertEquals(1, locationCardPhotoIndex(50.0, 100.0, 3))
        assertEquals(1, locationCardPhotoIndex(140.0, 100.0, 3))
        assertEquals(2, locationCardPhotoIndex(160.0, 100.0, 3))
    }

    @Test
    fun clampsToLastPhoto() {
        assertEquals(2, locationCardPhotoIndex(400.0, 100.0, 3))
        assertEquals(0, locationCardPhotoIndex(-20.0, 100.0, 3))
    }
}
