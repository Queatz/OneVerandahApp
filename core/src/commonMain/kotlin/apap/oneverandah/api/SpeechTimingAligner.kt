package apap.oneverandah.api

/**
 * Maps study tokens onto TTS word timings so a tapped word can be clipped from
 * full-sentence audio without a second synthesis call.
 *
 * When the provider returns no usable word marks, callers should fall back
 * (e.g. per-token TTS) rather than inventing timings.
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
        val compactToken = compact(token)
        if (compactToken.isEmpty()) return null

        // Occurrence index among equal tokens (handles repeated words).
        var occurrence = 0
        for (i in 0 until tokenIndex) {
            if (compact(tokenTexts[i]) == compactToken) occurrence += 1
        }

        val matches = mutableListOf<List<SpeechWordTiming>>()
        var i = 0
        while (i < words.size) {
            val first = words[i]
            val firstCompact = compact(first.text)
            if (firstCompact.isEmpty()) {
                i += 1
                continue
            }
            if (compactToken == firstCompact) {
                matches += listOf(first)
                i += 1
                continue
            }
            // Join consecutive TTS words until they cover the token (CJK char marks).
            if (compactToken.startsWith(firstCompact)) {
                var joined = firstCompact
                val group = mutableListOf(first)
                var j = i + 1
                while (j < words.size && joined.length < compactToken.length) {
                    val next = compact(words[j].text)
                    if (next.isEmpty()) {
                        j += 1
                        continue
                    }
                    if (!compactToken.startsWith(joined + next)) break
                    joined += next
                    group += words[j]
                    j += 1
                }
                if (joined == compactToken) {
                    matches += group
                    i = j
                    continue
                }
            }
            i += 1
        }
        return matches.getOrNull(occurrence)
    }

    private fun mergeTiming(words: List<SpeechWordTiming>): Pair<Int, Int>? {
        if (words.isEmpty()) return null
        val begin = words.minOf { it.beginMs }
        val end = words.maxOf { it.endMs }
        if (end <= begin) return null
        return begin to end
    }

    private fun looksLikeNonSpeechToken(token: String): Boolean {
        val t = token.trim()
        if (t.isEmpty()) return true
        return t.all { ch ->
            !ch.isLetterOrDigit() && ch.category != CharCategory.OTHER_LETTER
        }
    }

    private fun compact(text: String): String {
        return text.filterNot { ch ->
            ch.isWhitespace() || (!ch.isLetterOrDigit() && ch.category != CharCategory.OTHER_LETTER)
        }
    }
}
