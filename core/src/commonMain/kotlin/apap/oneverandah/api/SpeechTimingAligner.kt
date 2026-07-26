package apap.oneverandah.api

/**
 * Maps study tokens onto TTS word timings so a tapped word can be clipped from
 * full-sentence audio without a second synthesis call.
 */
object SpeechTimingAligner {
    /**
     * Returns the `[beginMs, endMs]` span covering [tokenIndex] in [tokenTexts],
     * or null when timings cannot be aligned confidently.
     */
    fun timingForToken(
        sentenceText: String,
        tokenTexts: List<String>,
        tokenIndex: Int,
        words: List<SpeechWordTiming>,
    ): Pair<Int, Int>? {
        if (words.isEmpty() || tokenIndex !in tokenTexts.indices) return null
        val token = tokenTexts[tokenIndex]
        if (token.isBlank() || looksLikeNonSpeechToken(token)) return null

        val span = tokenCharSpans(sentenceText, tokenTexts).getOrNull(tokenIndex)
        val byIndex = span?.let { range ->
            words.filter { word ->
                word.beginIndex >= 0 &&
                    word.endIndex > word.beginIndex &&
                    word.beginIndex < range.last + 1 &&
                    word.endIndex > range.first
            }
        }.orEmpty()
        if (byIndex.isNotEmpty()) {
            return mergeTiming(byIndex)
        }

        val byText = matchWordsByTextOccurrence(tokenTexts, tokenIndex, words)
        return byText?.let(::mergeTiming)
    }

    /** Sequential first-fit of each token inside [sentenceText]. */
    fun tokenCharSpans(sentenceText: String, tokenTexts: List<String>): List<IntRange?> {
        var cursor = 0
        return tokenTexts.map { token ->
            if (token.isEmpty()) return@map null
            val idx = sentenceText.indexOf(token, startIndex = cursor)
            if (idx < 0) {
                null
            } else {
                cursor = idx + token.length
                idx until (idx + token.length)
            }
        }
    }

    private fun matchWordsByTextOccurrence(
        tokenTexts: List<String>,
        tokenIndex: Int,
        words: List<SpeechWordTiming>,
    ): List<SpeechWordTiming>? {
        val token = tokenTexts[tokenIndex]
        val occurrence = tokenTexts.take(tokenIndex + 1).count { it == token } - 1
        if (occurrence < 0) return null

        // Exact token match first (one TTS word == one study token).
        val exact = words.filter { it.text == token }
        exact.getOrNull(occurrence)?.let { return listOf(it) }

        // Otherwise accumulate consecutive TTS words until their joined text equals the token
        // (handles character-level timings common for CJK / some multilingual voices).
        val compactToken = compact(token)
        if (compactToken.isEmpty()) return null
        var seen = 0
        var i = 0
        while (i < words.size) {
            var joined = ""
            var j = i
            while (j < words.size && joined.length < compactToken.length) {
                joined += compact(words[j].text)
                j += 1
            }
            if (joined == compactToken) {
                if (seen == occurrence) {
                    return words.subList(i, j)
                }
                seen += 1
                i = j
            } else {
                i += 1
            }
        }
        return null
    }

    private fun mergeTiming(words: List<SpeechWordTiming>): Pair<Int, Int>? {
        if (words.isEmpty()) return null
        val begin = words.minOf { it.beginMs }
        val end = words.maxOf { it.endMs }
        if (end <= begin) return null
        return begin to end
    }

    private fun compact(text: String): String =
        text.filterNot { it.isWhitespace() }

    private fun looksLikeNonSpeechToken(text: String): Boolean {
        if (text.isEmpty()) return true
        return text.all { ch ->
            ch.isWhitespace() || (!ch.isLetterOrDigit() && ch.category != CharCategory.OTHER_LETTER)
        }
    }
}
