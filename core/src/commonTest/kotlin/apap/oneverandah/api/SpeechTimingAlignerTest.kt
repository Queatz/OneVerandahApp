package apap.oneverandah.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechTimingAlignerTest {
    @Test
    fun `aligns token using character indices from TTS words`() {
        val sentence = "Xin chào bạn."
        val tokens = listOf("Xin", "chào", "bạn", ".")
        val words = listOf(
            SpeechWordTiming(text = "Xin", beginMs = 0, endMs = 160, beginIndex = 0, endIndex = 3),
            SpeechWordTiming(text = "chào", beginMs = 180, endMs = 420, beginIndex = 4, endIndex = 8),
            SpeechWordTiming(text = "bạn", beginMs = 440, endMs = 700, beginIndex = 9, endIndex = 12),
        )

        assertEquals(0 to 160, SpeechTimingAligner.timingForToken(sentence, tokens, 0, words))
        assertEquals(180 to 420, SpeechTimingAligner.timingForToken(sentence, tokens, 1, words))
        assertEquals(440 to 700, SpeechTimingAligner.timingForToken(sentence, tokens, 2, words))
        assertNull(SpeechTimingAligner.timingForToken(sentence, tokens, 3, words))
    }

    @Test
    fun `aligns multi-character tokens from character-level TTS words by text`() {
        val sentence = "你好世界"
        val tokens = listOf("你好", "世界")
        val words = listOf(
            SpeechWordTiming(text = "你", beginMs = 0, endMs = 100),
            SpeechWordTiming(text = "好", beginMs = 100, endMs = 220),
            SpeechWordTiming(text = "世", beginMs = 240, endMs = 360),
            SpeechWordTiming(text = "界", beginMs = 360, endMs = 500),
        )

        assertEquals(0 to 220, SpeechTimingAligner.timingForToken(sentence, tokens, 0, words))
        assertEquals(240 to 500, SpeechTimingAligner.timingForToken(sentence, tokens, 1, words))
    }

    @Test
    fun `prefers the correct occurrence of a repeated token`() {
        val sentence = "go go go"
        val tokens = listOf("go", "go", "go")
        val words = listOf(
            SpeechWordTiming(text = "go", beginMs = 0, endMs = 100, beginIndex = 0, endIndex = 2),
            SpeechWordTiming(text = "go", beginMs = 120, endMs = 220, beginIndex = 3, endIndex = 5),
            SpeechWordTiming(text = "go", beginMs = 240, endMs = 340, beginIndex = 6, endIndex = 8),
        )

        assertEquals(120 to 220, SpeechTimingAligner.timingForToken(sentence, tokens, 1, words))
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
