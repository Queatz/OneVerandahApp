package apap.oneverandah.challenges

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChallengeTranscriptionTest {
    @Test
    fun parseFileTranscriptionWordsAndText() {
        val raw = """
            {
              "output": {
                "task_status": "SUCCEEDED",
                "transcripts": [{
                  "text": "hello neighbors",
                  "sentences": [{
                    "text": "hello neighbors",
                    "words": [
                      {"text": "hello", "begin_time": 0, "end_time": 400},
                      {"text": "neighbors", "begin_time": 400, "end_time": 900}
                    ]
                  }]
                }]
              }
            }
        """.trimIndent()
        val parsed = parseChallengeTranscript(raw)
        assertEquals("hello neighbors", parsed.text)
        assertEquals(listOf("hello", "neighbors"), parsed.words.map { it.text })
        assertEquals(400, parsed.words.last().startMs)
    }

    @Test
    fun parsePlainTextFallsBackToEvenTimings() {
        val parsed = parseChallengeTranscript("""{"output":{"text":"pool day fun"}}""", durationMs = 900)
        assertEquals("pool day fun", parsed.text)
        assertEquals(3, parsed.words.size)
        assertEquals(0, parsed.words.first().startMs)
        assertEquals(900, parsed.words.last().endMs)
    }

    @Test
    fun parseNonJsonKeepsRawText() {
        val parsed = parseChallengeTranscript("just talking")
        assertEquals("just talking", parsed.text)
        assertTrue(parsed.words.isNotEmpty())
    }
}
