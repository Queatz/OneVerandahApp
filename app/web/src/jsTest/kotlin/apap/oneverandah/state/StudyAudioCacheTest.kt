package apap.oneverandah.state

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import apap.oneverandah.api.SpeechWordTiming
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StudyAudioCacheTest {
    @Test
    fun `put is available from session storage to a new cache instance`() {
        val storage = FakeStudyAudioStorage()
        val request = SpeechRequest("  Hello  ", "en")
        val response = sampleResponse()

        StudyAudioCacheStore(storage).put(request, response)

        assertEquals(response, StudyAudioCacheStore(storage).get(SpeechRequest("Hello", "en")))
    }

    @Test
    fun `cache miss returns null`() {
        assertNull(StudyAudioCacheStore(FakeStudyAudioStorage()).get(SpeechRequest("Hello", "en")))
    }

    @Test
    fun `text and language are isolated`() {
        val store = StudyAudioCacheStore(FakeStudyAudioStorage())
        val english = SpeechRequest("Hello", "en")
        val vietnamese = SpeechRequest("Hello", "vi")

        store.put(english, sampleResponse("english"))

        assertEquals("english", store.get(english)?.audioBase64)
        assertNull(store.get(vietnamese))
        assertNull(store.get(SpeechRequest("Goodbye", "en")))
    }

    @Test
    fun `load fetches once and returns the cached response afterwards`() {
        val store = StudyAudioCacheStore(FakeStudyAudioStorage())
        val request = SpeechRequest("Hello", "en")
        val response = sampleResponse()
        var fetchCount = 0

        val first = runSuspend { store.load(request) { fetchCount++; response } }
        val second = runSuspend { store.load(request) { fetchCount++; sampleResponse("unexpected") } }

        assertEquals(response, first)
        assertEquals(response, second)
        assertEquals(1, fetchCount)
    }

    @Test
    fun `data url contains the response content type and audio`() {
        assertEquals(
            "data:audio/mpeg;base64,audio-data",
            StudyAudioCache.dataUrl(sampleResponse("audio-data")),
        )
    }

    @Test
    fun `quota eviction removes oldest storage entry and put does not throw`() {
        val storage = FakeStudyAudioStorage()
        val store = StudyAudioCacheStore(storage)
        val oldest = SpeechRequest("old", "en")
        val newer = SpeechRequest("new", "en")
        val incoming = SpeechRequest("add", "en")
        store.put(oldest, sampleResponse("same-audio"))
        store.put(newer, sampleResponse("same-audio"))
        storage.maxChars = storage.usedChars + 1

        store.put(incoming, sampleResponse("same-audio"))

        val reloaded = StudyAudioCacheStore(storage)
        assertNull(reloaded.get(oldest))
        assertNotNull(reloaded.get(newer))
        assertNotNull(reloaded.get(incoming))
    }

    private fun sampleResponse(audioBase64: String = "audio-data") = SpeechResponse(
        audioBase64 = audioBase64,
        contentType = "audio/mpeg",
        words = listOf(SpeechWordTiming("Hello", 0, 500)),
    )

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

    private class FakeStudyAudioStorage : StudyAudioStorage {
        private val values = linkedMapOf<String, String>()
        var maxChars = Int.MAX_VALUE
        val usedChars: Int
            get() = values.entries.sumOf { it.key.length + it.value.length }

        override fun getItem(key: String): String? = values[key]

        override fun setItem(key: String, value: String) {
            val nextSize = usedChars - (values[key]?.let { key.length + it.length } ?: 0) + key.length + value.length
            if (nextSize > maxChars) throw IllegalStateException("QuotaExceededError")
            values[key] = value
        }

        override fun removeItem(key: String) {
            values.remove(key)
        }
    }
}
