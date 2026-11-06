package apap.oneverandah.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class StudySpeakTabTest {
    @Test
    fun `listen and speak phases advance through exactly three rounds`() {
        var progress = startStudySpeak(StudySpeakProgress())
        assertEquals(StudySpeakProgress(StudySpeakPhase.LISTENING, 0), progress)

        progress = finishStudySpeakListen(progress)
        assertEquals(StudySpeakProgress(StudySpeakPhase.SPEAKING, 0), progress)
        progress = finishStudySpeakRecording(progress)
        assertEquals(StudySpeakProgress(StudySpeakPhase.LISTENING, 1), progress)

        progress = finishStudySpeakListen(progress)
        progress = finishStudySpeakRecording(progress)
        assertEquals(StudySpeakProgress(StudySpeakPhase.LISTENING, 2), progress)

        progress = finishStudySpeakListen(progress)
        progress = finishStudySpeakRecording(progress)
        assertEquals(StudySpeakProgress(StudySpeakPhase.REVIEW, 2), progress)
    }

    @Test
    fun `transitions outside their phase are no-ops`() {
        val ready = StudySpeakProgress()
        assertEquals(ready, finishStudySpeakListen(ready))
        assertEquals(ready, finishStudySpeakRecording(ready))

        val review = StudySpeakProgress(StudySpeakPhase.REVIEW, 2)
        assertEquals(review, startStudySpeak(review))
        assertEquals(review, finishStudySpeakListen(review))
        assertEquals(review, finishStudySpeakRecording(review))
    }

    @Test
    fun `restart resets a reviewed sentence to ready`() {
        assertEquals(
            StudySpeakProgress(),
            resetStudySpeak(StudySpeakProgress(StudySpeakPhase.REVIEW, 2)),
        )
    }

    @Test
    fun `review playlist pairs generated audio before each local attempt`() {
        assertEquals(
            listOf("target", "take-1", "target", "take-2", "target", "take-3"),
            studySpeakReviewPlaylist("target", listOf("take-1", "take-2", "take-3")),
        )
        assertEquals(
            listOf("target", "take-2"),
            studySpeakReviewPlaylist("target", listOf("take-2")),
        )
    }

    @Test
    fun `playing attempt index follows standalone and autoplay playlists`() {
        val attempts = listOf("take-1", "take-2", "take-3")
        val reviewPlaylist = studySpeakReviewPlaylist("target", attempts)

        assertEquals(0, studySpeakPlayingAttemptIndex("target", attempts, listOf("target", "take-1"), 0, true))
        assertEquals(1, studySpeakPlayingAttemptIndex("target", attempts, listOf("target", "take-2"), 1, true))
        assertEquals(0, studySpeakPlayingAttemptIndex("target", attempts, reviewPlaylist, 1, true))
        assertEquals(1, studySpeakPlayingAttemptIndex("target", attempts, reviewPlaylist, 3, true))
        assertEquals(2, studySpeakPlayingAttemptIndex("target", attempts, reviewPlaylist, 4, true))
        assertEquals(null, studySpeakPlayingAttemptIndex("target", attempts, reviewPlaylist, 4, false))
        assertEquals(null, studySpeakPlayingAttemptIndex("target", attempts, listOf("other"), 0, true))
    }
}