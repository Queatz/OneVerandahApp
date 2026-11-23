package apap.oneverandah.api

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ChatApiTest {
    @Test
    fun `chat endpoints use backend domain`() {
        assertEquals("https://api.oneverandah.app/api/chat", chatApiUrl())
        assertEquals("https://api.oneverandah.app/api/chat/conversation-1", chatApiUrl("/conversation-1"))
        assertEquals("https://api.oneverandah.app/api/chat/speech", chatApiUrl("/speech"))
        assertEquals("https://api.oneverandah.app/api/chat/study", chatApiUrl("/study"))
        assertEquals("https://api.oneverandah.app/api/chat/study/dissect", chatApiUrl("/study/dissect"))
        assertEquals("https://api.oneverandah.app/api/chat/study/sessions", chatApiUrl("/study/sessions"))
        assertEquals(
            "https://api.oneverandah.app/api/chat/study/sessions/abc-123",
            chatApiUrl("/study/sessions/abc-123"),
        )
        assertEquals(
            "https://api.oneverandah.app/api/chat/study/sessions/community",
            chatApiUrl("/study/sessions/community"),
        )
    }

    @Test
    fun `study build NDJSON event lines decode with the type discriminator`() {
        val event: StudyBuildEvent = StudyBuildEvent.WordImage(
            sentenceIndex = 2,
            tokenIndex = 3,
            imageUrl = "https://img/word",
        )
        val line = Json { classDiscriminator = "type" }
            .encodeToString(StudyBuildEvent.serializer(), event)

        assertEquals(event, ChatApi.decodeStudyBuildEventLine(line))
    }

    @Test
    fun `read retry recovers from Ktor JS fetch Error and 503 responses`() {
        var attempts = 0
        val delays = mutableListOf<Int>()

        val result = runSuspend {
            executeWithReadRetry(
                obtainToken = { "tok-1" },
                onUnauthorized = { error("Should not clear token on transient failure") },
                delayBeforeRetry = { delays += it },
                execute = {
                    attempts += 1
                    when (attempts) {
                        1 -> throw Error("Fail to fetch")
                        2 -> throw ChatApiException("Study sessions request timed out", 503)
                        else -> "loaded"
                    }
                },
            )
        }

        assertEquals("loaded", result)
        assertEquals(3, attempts)
        assertEquals(listOf(0, 1), delays)
    }

    @Test
    fun `read retry refreshes device token once on 401 and succeeds`() {
        var currentToken = "stale-token"
        val clearedTokens = mutableListOf<String>()
        val usedTokens = mutableListOf<String>()

        val result = runSuspend {
            executeWithReadRetry(
                obtainToken = { currentToken },
                onUnauthorized = { failed ->
                    clearedTokens += failed
                    currentToken = "fresh-token"
                },
                execute = { token ->
                    usedTokens += token
                    if (token == "stale-token") {
                        throw ChatApiException("Valid device token required", 401)
                    }
                    "ok"
                },
            )
        }

        assertEquals("ok", result)
        assertEquals(listOf("stale-token"), clearedTokens)
        assertEquals(listOf("stale-token", "fresh-token"), usedTokens)
    }

    @Test
    fun `read retry stops after a single 401 refresh`() {
        var attempts = 0
        val cleared = mutableListOf<String>()

        val error = assertFailsWith<ChatApiException> {
            runSuspend {
                executeWithReadRetry(
                    obtainToken = { "tok-${attempts + 1}" },
                    onUnauthorized = { cleared += it },
                    execute = {
                        attempts += 1
                        throw ChatApiException("Valid device token required", 401)
                    },
                )
            }
        }

        assertEquals(401, error.statusCode)
        assertEquals(2, attempts)
        assertEquals(listOf("tok-1"), cleared)
    }

    @Test
    fun `read retry does not retry 404, serialization errors, or cancellation`() {
        var notFoundAttempts = 0
        assertFailsWith<ChatApiException> {
            runSuspend {
                executeWithReadRetry(
                    obtainToken = { "tok" },
                    onUnauthorized = {},
                    execute = {
                        notFoundAttempts += 1
                        throw ChatApiException("Not found", 404)
                    },
                )
            }
        }
        assertEquals(1, notFoundAttempts)

        var decodeAttempts = 0
        assertFailsWith<SerializationException> {
            runSuspend {
                executeWithReadRetry(
                    obtainToken = { "tok" },
                    onUnauthorized = {},
                    execute = {
                        decodeAttempts += 1
                        throw SerializationException("Unexpected token")
                    },
                )
            }
        }
        assertEquals(1, decodeAttempts)

        var cancelAttempts = 0
        assertFailsWith<CancellationException> {
            runSuspend {
                executeWithReadRetry(
                    obtainToken = { "tok" },
                    onUnauthorized = {},
                    execute = {
                        cancelAttempts += 1
                        throw CancellationException("Cancelled")
                    },
                )
            }
        }
        assertEquals(1, cancelAttempts)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return outcome!!.getOrThrow()
    }
}