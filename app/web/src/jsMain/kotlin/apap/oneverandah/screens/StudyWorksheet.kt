package apap.oneverandah.screens

import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudySessionResponse
import kotlinx.browser.window

internal data class StudyWorksheetVocabularyItem(
    val prompt: String,
    val answer: String,
)

internal data class StudyWorksheetPhraseItem(
    val prompt: String,
    val answer: String,
)

internal data class StudyWorksheetContent(
    val vocabulary: List<StudyWorksheetVocabularyItem>,
    val phrases: List<StudyWorksheetPhraseItem>,
) {
    val isEmpty: Boolean
        get() = vocabulary.isEmpty() && phrases.isEmpty()
}

internal data class StudyWorksheetLabels(
    val title: String,
    val instructions: String,
    val vocabulary: String,
    val phrases: String,
    val answerKey: String,
)

internal fun studyWorksheetContent(session: StudySessionResponse): StudyWorksheetContent {
    val vocabulary = studyVocabularyRows(session.sentences).mapNotNull { row ->
        val prompt = studyVocabularyMeaningLines(row.literalMeaning, row.meaning)
            .joinToString(" / ")
            .takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        StudyWorksheetVocabularyItem(prompt = prompt, answer = row.word)
    }

    val translatedExampleKeys = (session.sentences.flatMap { sentence ->
        sentence.tokens.flatMap { it.exampleSentences }
    } + session.practiceSentences)
        .filter { it.text.isNotBlank() && it.translation.isNotBlank() }
        .map { worksheetPhraseKey(it.text, it.translation) }
        .toSet()
    val translatedMainSentences = session.sentences.map { sentence ->
        StudyExampleSentence(text = sentence.text, translation = sentence.translation)
    }
    val translatedExtraSentences = collectExpressPracticeSentences(session).filter { example ->
        worksheetPhraseKey(example.text, example.translation) in translatedExampleKeys
    }
    val seenPhrases = mutableSetOf<String>()
    val phrases = (translatedMainSentences + translatedExtraSentences).mapNotNull { example ->
        val text = example.text.trim()
        val translation = example.translation.trim()
        if (text.isEmpty() || translation.isEmpty() || !seenPhrases.add(text.lowercase())) {
            return@mapNotNull null
        }
        StudyWorksheetPhraseItem(prompt = translation, answer = text)
    }

    return StudyWorksheetContent(vocabulary = vocabulary, phrases = phrases)
}

internal fun buildStudyWorksheetHtml(
    session: StudySessionResponse,
    labels: StudyWorksheetLabels,
): String? {
    val content = studyWorksheetContent(session)
    if (content.isEmpty) return null

    return buildString {
        appendLine("<!doctype html>")
        appendLine("<html><head><meta charset=\"utf-8\"><title>${labels.title.escapeHtml()}</title>")
        appendLine(
            """<style>
                @page { margin: 18mm; }
                body { color: #111; font: 12pt/1.5 Arial, sans-serif; margin: 0 auto; max-width: 760px; }
                h1 { font-size: 22pt; margin: 0 0 4mm; }
                h2 { border-bottom: 1px solid #777; font-size: 16pt; margin: 8mm 0 3mm; padding-bottom: 1mm; }
                .instructions { color: #333; margin: 0 0 6mm; }
                .exercise { break-inside: avoid; margin: 0 0 5mm; }
                .prompt { margin: 0 0 2mm; }
                .answer-line { border-bottom: 1px solid #777; height: 9mm; }
                .phrase .answer-line + .answer-line { margin-top: 2mm; }
                .answer-key { break-before: page; page-break-before: always; }
                .answer { margin: 0 0 2mm; }
                @media screen { body { padding: 24px; } }
            </style></head><body>""".trimIndent(),
        )
        appendLine("<h1>${labels.title.escapeHtml()}</h1>")
        appendLine("<p class=\"instructions\">${labels.instructions.escapeHtml()}</p>")
        if (content.vocabulary.isNotEmpty()) {
            appendLine("<section><h2>${labels.vocabulary.escapeHtml()}</h2>")
            content.vocabulary.forEachIndexed { index, item ->
                appendLine(
                    "<div class=\"exercise vocabulary\"><p class=\"prompt\">${index + 1}. " +
                        "${item.prompt.escapeHtml()}</p><div class=\"answer-line\"></div></div>",
                )
            }
            appendLine("</section>")
        }
        if (content.phrases.isNotEmpty()) {
            appendLine("<section><h2>${labels.phrases.escapeHtml()}</h2>")
            content.phrases.forEachIndexed { index, item ->
                appendLine(
                    "<div class=\"exercise phrase\"><p class=\"prompt\">${index + 1}. " +
                        "${item.prompt.escapeHtml()}</p><div class=\"answer-line\"></div>" +
                        "<div class=\"answer-line\"></div></div>",
                )
            }
            appendLine("</section>")
        }
        appendLine("<section class=\"answer-key\"><h2>${labels.answerKey.escapeHtml()}</h2>")
        if (content.vocabulary.isNotEmpty()) {
            appendLine("<h3>${labels.vocabulary.escapeHtml()}</h3>")
            content.vocabulary.forEachIndexed { index, item ->
                appendLine(
                    "<p class=\"answer\">${index + 1}. ${item.prompt.escapeHtml()} — " +
                        "${item.answer.escapeHtml()}</p>",
                )
            }
        }
        if (content.phrases.isNotEmpty()) {
            appendLine("<h3>${labels.phrases.escapeHtml()}</h3>")
            content.phrases.forEachIndexed { index, item ->
                appendLine(
                    "<p class=\"answer\">${index + 1}. ${item.prompt.escapeHtml()} — " +
                        "${item.answer.escapeHtml()}</p>",
                )
            }
        }
        appendLine("</section></body></html>")
    }
}

internal fun openStudyWorksheet(html: String): Boolean {
    return runCatching {
        val printWindow = window.open("", "_blank") ?: return@runCatching false
        printWindow.document.open()
        printWindow.document.write(html)
        printWindow.document.close()
        window.setTimeout({
            printWindow.focus()
            printWindow.print()
        }, 250)
        true
    }.getOrDefault(false)
}

private fun worksheetPhraseKey(text: String, translation: String): String =
    "${translation.trim().lowercase()}|${text.trim().lowercase()}"

private fun String.escapeHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")