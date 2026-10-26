package apap.oneverandah.chat

import apap.oneverandah.api.StudyExamBeat
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamBeatScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyExamScoringTest {
    @Test
    fun `level bands use inclusive integer percentage boundaries`() {
        assertEquals(1, StudyExamScoring.level(0, 100))
        assertEquals(1, StudyExamScoring.level(20, 100))
        assertEquals(2, StudyExamScoring.level(21, 100))
        assertEquals(2, StudyExamScoring.level(40, 100))
        assertEquals(3, StudyExamScoring.level(41, 100))
        assertEquals(3, StudyExamScoring.level(60, 100))
        assertEquals(4, StudyExamScoring.level(61, 100))
        assertEquals(4, StudyExamScoring.level(80, 100))
        assertEquals(5, StudyExamScoring.level(81, 100))
        assertEquals(1, StudyExamScoring.level(0, 0))
    }

    @Test
    fun `overall feedback is available in each supported UI language`() {
        val languageTags = listOf("en", "vi", "zh", "ja", "ko", "ru", "es", "de", "my")

        assertTrue(languageTags.all { StudyExamScoring.feedback(it).isNotBlank() })
    }

    @Test
    fun `full and half points map to native and intermediate overall grades`() {
        val beats = listOf(
            beat("listen", StudyExamBeatKind.LISTEN),
            beat("speak", StudyExamBeatKind.SPEAK),
        )
        val full = StudyExamScoring.grade(
            beats = beats,
            beatScores = beats.map { StudyExamBeatScore(it.id, 2, "Clear.") },
            scoredAt = 1L,
            feedback = "Summary.",
        )
        val half = StudyExamScoring.grade(
            beats = beats,
            beatScores = beats.map { StudyExamBeatScore(it.id, 1, "Partial.") },
            scoredAt = 2L,
            feedback = "Summary.",
        )

        assertEquals(listOf(5, 5, 5), listOf(full.overall, full.listening, full.speaking))
        assertEquals(listOf(3, 3, 3), listOf(half.overall, half.listening, half.speaking))
    }

    @Test
    fun `grade averages skill levels equally and handles a missing skill`() {
        val balancedBeats = (0 until 10).map { beat("listen-$it", StudyExamBeatKind.LISTEN) } +
            (0 until 10).map { beat("speak-$it", StudyExamBeatKind.SPEAK) }
        val mixedScores = balancedBeats.mapIndexed { index, item ->
            StudyExamBeatScore(
                beatId = item.id,
                points = when {
                    index < 8 -> 2
                    index in 10..13 -> 2
                    else -> 0
                },
                feedback = "note",
            )
        }
        val mixed = StudyExamScoring.grade(balancedBeats, mixedScores, scoredAt = 10L, feedback = "Summary.")

        assertEquals(4, mixed.listening)
        assertEquals(2, mixed.speaking)
        assertEquals(3, mixed.overall)

        val listeningOnly = StudyExamScoring.grade(
            beats = listOf(beat("listen", StudyExamBeatKind.LISTEN)),
            beatScores = listOf(StudyExamBeatScore("listen", 2, "Clear.")),
            scoredAt = 20L,
            feedback = "Summary.",
        )
        assertEquals(5, listeningOnly.listening)
        assertEquals(1, listeningOnly.speaking)
        assertEquals(listeningOnly.listening, listeningOnly.overall)
    }

    private fun beat(id: String, kind: StudyExamBeatKind) = StudyExamBeat(
        id = id,
        kind = kind,
        speakerId = "main",
        speakerName = "Lan",
        line = if (kind == StudyExamBeatKind.LISTEN) "Xin chào." else null,
        prompt = "Respond to the moment.",
        expectedMeaning = "A greeting.",
        sourceSentence = "Xin chào.",
    )
}
