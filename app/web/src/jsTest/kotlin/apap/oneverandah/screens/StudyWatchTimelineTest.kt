package apap.oneverandah.screens

import apap.oneverandah.api.SpeechWordTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyWatchTimelineTest {
    @Test
    fun `clip duration uses last word end and falls back when timings are missing`() {
        assertEquals(2_000, watchClipDurationMs(emptyList()))
        assertEquals(
            1_500,
            watchClipDurationMs(
                listOf(
                    SpeechWordTiming(text = "a", beginMs = 0, endMs = 400),
                    SpeechWordTiming(text = "b", beginMs = 400, endMs = 1_500),
                ),
            ),
        )
        assertEquals(
            1,
            watchClipDurationMs(
                listOf(SpeechWordTiming(text = "a", beginMs = 0, endMs = 0)),
            ),
        )
    }

    @Test
    fun `timeline stacks scene durations and exposes next-scene markers`() {
        val timeline = buildWatchTimeline(
            listOf(
                WatchSceneClip(sentenceIndex = 0, url = "a", durationMs = 1_000),
                WatchSceneClip(sentenceIndex = 1, url = "b", durationMs = 500),
                WatchSceneClip(sentenceIndex = 2, url = "c", durationMs = 1_500),
            ),
        )
        assertEquals(3_000, timeline.totalMs)
        assertEquals(listOf(0, 1_000, 1_500), timeline.startsMs)
        assertEquals(listOf(1_000.0 / 3_000.0, 1_500.0 / 3_000.0), watchSceneMarkerFractions(timeline))
        assertTrue(watchSceneMarkerFractions(buildWatchTimeline(emptyList())).isEmpty())
        assertTrue(
            watchSceneMarkerFractions(
                buildWatchTimeline(listOf(WatchSceneClip(0, "a", 800))),
            ).isEmpty(),
        )
    }

    @Test
    fun `scene index and local offset follow the global playhead`() {
        val timeline = buildWatchTimeline(
            listOf(
                WatchSceneClip(sentenceIndex = 0, url = "a", durationMs = 1_000),
                WatchSceneClip(sentenceIndex = 1, url = "b", durationMs = 500),
                WatchSceneClip(sentenceIndex = 2, url = "c", durationMs = 1_500),
            ),
        )
        assertEquals(0, watchSceneIndexAt(timeline, 0))
        assertEquals(0, watchSceneIndexAt(timeline, 999))
        assertEquals(1, watchSceneIndexAt(timeline, 1_000))
        assertEquals(2, watchSceneIndexAt(timeline, 2_999))
        assertEquals(2, watchSceneIndexAt(timeline, 8_000))
        assertEquals(0, watchSceneIndexAt(buildWatchTimeline(emptyList()), 10))

        assertEquals(200, watchLocalOffsetMs(timeline, 200))
        assertEquals(0, watchLocalOffsetMs(timeline, 1_000))
        assertEquals(250, watchLocalOffsetMs(timeline, 1_250))
        assertEquals(1_500, watchLocalOffsetMs(timeline, 3_000))
        assertEquals(0, watchLocalOffsetMs(buildWatchTimeline(emptyList()), 40))
    }

    @Test
    fun `clock formats minutes and seconds`() {
        assertEquals("0:00", formatWatchClock(0))
        assertEquals("0:00", formatWatchClock(-20))
        assertEquals("0:05", formatWatchClock(5_200))
        assertEquals("1:01", formatWatchClock(61_000))
    }

    @Test
    fun `preload indexes include current scene and the next three`() {
        assertEquals(emptyList(), watchPreloadIndexes(0, 0))
        assertEquals(listOf(0), watchPreloadIndexes(0, 1))
        assertEquals(listOf(0, 1, 2, 3), watchPreloadIndexes(0, 10))
        assertEquals(listOf(4, 5, 6, 7), watchPreloadIndexes(4, 10))
        assertEquals(listOf(8, 9), watchPreloadIndexes(8, 10))
        assertEquals(listOf(9), watchPreloadIndexes(9, 10))
        assertEquals(listOf(9), watchPreloadIndexes(40, 10))
        assertEquals(listOf(0, 1, 2, 3), watchPreloadIndexes(-2, 10))
    }
}
