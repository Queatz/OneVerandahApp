package apap.oneverandah.logging

internal object SafeFailureDiagnostics {
    fun describe(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }
            .take(6)
            .map { cause ->
                val type = cause.javaClass.simpleName.ifBlank { "BuildException" }
                val message = cause.message?.let(::sanitizeMessage).orEmpty()
                if (message.isBlank()) type else "$type: $message"
            }
            .joinToString(" <- ")
        val source = error.stackTrace.firstOrNull { it.className.startsWith("apap.oneverandah.") }
            ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        return listOfNotNull(chain.take(520), source?.let { "source=$it" })
            .joinToString("; ")
            .take(640)
    }

    fun sanitizeMessage(message: String): String = message
        .replace(Regex("(?i)data:[^\\s,]+;base64,[^\\s]+"), "[image data]")
        .replace(Regex("(?i)\\bBearer\\s+[^\\s,;]+"), "Bearer [redacted]")
        .replace(Regex("(?i)(?:https?|oss)://[^\\s\\\"'<>]+"), "[URL]")
        .replace(
            Regex("(?i)(authorization|api[_-]?key|token|secret|signature|oss_access_key_id)\\s*[:=]\\s*[^\\s,;]+"),
            "$1=[redacted]",
        )
        .replace(
            Regex("(?s)(\\([^)]*\\b\\d{3}\\b[^)]*\\)\\s*:\\s*).*$"),
            "$1[provider response omitted]",
        )
        .replace(Regex("(?s)\\s*:\\s*(?:\\{|\\[(?!URL\\])|<html\\b).*$", RegexOption.IGNORE_CASE), ": [provider response omitted]")
        .replace(Regex("(?i)(body|output|data)\\s*[=:]\\s*.*$"), "$1=[provider response omitted]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(220)
}