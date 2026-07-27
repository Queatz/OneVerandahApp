package apap.oneverandah.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechTimingAlignerTest {
    @Test
    fun `aligns token using character indices from TTS words`() {
        val sentence = "Xin chào bạn"
        val tokens = listOf("Xin", "chào", "bạn")
        val words = listOf(
            SpeechWordTiming(text = "Xin", beginMs = 0, endMs = 180, beginIndex = 0, endIndex = 3),
            SpeechWordTiming(text = "chào", beginMs = 200, endMs = 520, beginIndex = 4, endIndex = 8),
            SpeechWordTiming(text = "bạn", beginMs = 540, endMs = 800, beginIndex = 9, endIndex = 12),
        )

        assertEquals(
            200 to 520,
            SpeechTimingAligner.timingForToken(sentence, tokens, 1, words),
        )
    }

    @Test
    fun `aligns multi-character CJK token from several TTS words`() {
        val sentence = "你好世界"
        val tokens = listOf("你好", "世界")
        val words = listOf(
            SpeechWordTiming(text = "你", beginMs = 0, endMs = 100, beginIndex = 0, endIndex = 1),
            SpeechWordTiming(text = "好", beginMs = 100, endMs = 200, beginIndex = 1, endIndex = 2),
            SpeechWordTiming(text = "世", beginMs = 220, endMs = 320, beginIndex = 2, endIndex = 3),
            SpeechWordTiming(text = "界", beginMs = 320, endMs = 420, beginIndex = 3, endIndex = 4),
        )

        assertEquals(
            0 to 200,
            SpeechTimingAligner.timingForToken(sentence, tokens, 0, words),
        )
        assertEquals(
            220 to 420,
            SpeechTimingAligner.timingForToken(sentence, tokens, 1, words),
        )
    }

    @Test
    fun `selects the correct occurrence for repeated tokens`() {
        val sentence = "đi đi"
        val tokens = listOf("đi", "đi")
        val words = listOf(
            SpeechWordTiming(text = "đi", beginMs = 0, endMs = 120),
            SpeechWordTiming(text = "đi", beginMs = 140, endMs = 280),
        )

        assertEquals(0 to 120, SpeechTimingAligner.timingForToken(sentence, tokens, 0, words))
        assertEquals(140 to 280, SpeechTimingAligner.timingForToken(sentence, tokens, 1, words))
    }

    @Test
    fun `returns null when timings are missing`() {
        assertNull(
            SpeechTimingAligner.timingForToken(
                sentenceText = "Hello",
                tokenTexts = listOf("Hello"),
                tokenIndex = 0,
                words = emptyList(),
            ),
        )
    }
}
