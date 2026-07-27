package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechWordTiming
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TtsCacheTest {
    @Test
    fun `cache key fingerprint is stable and distinguishes text language and voice`() {
        val base = TtsCacheKey("Xin chào", "vi", "voice-a")
        assertEquals(base.fingerprint(), TtsCacheKey("Xin chào", "vi", "voice-a").fingerprint())
        assertTrue(base.fingerprint() != TtsCacheKey("Xin chao", "vi", "voice-a").fingerprint())
        assertTrue(base.fingerprint() != TtsCacheKey("Xin chào", "en", "voice-a").fingerprint())
        assertTrue(base.fingerprint() != TtsCacheKey("Xin chào", "vi", "voice-b").fingerprint())
    }

    @Test
    fun `in-memory cache stores and returns exact text language and voice matches`() = runBlocking {
        val cache = InMemoryTtsCache()
        val words = listOf(SpeechWordTiming("Xin", 0, 120, 0, 3))
        val speech = GeneratedSpeech(byteArrayOf(9, 8, 7), "audio/mpeg", words)

        cache.put("Xin chào", "vi", "voice-a", speech)

        val hit = cache.get("Xin chào", "vi", "voice-a")
        assertNotNull(hit)
        assertContentEquals(byteArrayOf(9, 8, 7), hit.bytes)
        assertEquals("audio/mpeg", hit.contentType)
        assertEquals(words, hit.words)

        assertNull(cache.get("Xin chào", "vi", "voice-b"))
        assertNull(cache.get("Xin chào", "en", "voice-a"))
        assertNull(cache.get("Xin chao", "vi", "voice-a"))
    }

    @Test
    fun `in-memory cache returns defensive copies of audio bytes`() = runBlocking {
        val cache = InMemoryTtsCache()
        val original = byteArrayOf(1, 2, 3)
        cache.put("hi", "en", "v", GeneratedSpeech(original, "audio/mpeg"))

        val first = requireNotNull(cache.get("hi", "en", "v"))
        first.bytes[0] = 99
        original[1] = 88

        val second = requireNotNull(cache.get("hi", "en", "v"))
        assertContentEquals(byteArrayOf(1, 2, 3), second.bytes)
    }

    @Test
    fun `file cache persists across instances and verifies exact keys`() = runBlocking {
        val root = Files.createTempDirectory("tts-cache-test").toFile()
        try {
            val words = listOf(
                SpeechWordTiming(text = "Bonjour", beginMs = 0, endMs = 300, beginIndex = 0, endIndex = 7),
            )
            val speech = GeneratedSpeech(byteArrayOf(4, 5, 6, 7), "audio/mpeg", words)
            FileTtsCache(root).put("Bonjour", "fr", "longanhuan_v3.6", speech)

            val reloaded = FileTtsCache(root)
            val hit = reloaded.get("Bonjour", "fr", "longanhuan_v3.6")
            assertNotNull(hit)
            assertContentEquals(byteArrayOf(4, 5, 6, 7), hit.bytes)
            assertEquals(words, hit.words)
            assertNull(reloaded.get("Bonjour", "fr", "other-voice"))
            assertNull(reloaded.get("Bonjour", "en", "longanhuan_v3.6"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `tiered cache promotes disk hits into memory`() = runBlocking {
        val root = Files.createTempDirectory("tts-cache-tiered").toFile()
        try {
            val disk = FileTtsCache(root)
            val memory = InMemoryTtsCache()
            val tiered = TieredTtsCache(memory, disk)
            tiered.put("hello", "en", "v1", GeneratedSpeech(byteArrayOf(1), "audio/mpeg"))

            // Fresh memory tier still misses; disk supplies and warms memory.
            val coldMemory = InMemoryTtsCache()
            val promoted = TieredTtsCache(coldMemory, disk).get("hello", "en", "v1")
            assertNotNull(promoted)
            assertContentEquals(byteArrayOf(1), requireNotNull(coldMemory.get("hello", "en", "v1")).bytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `speech service serves repeated exact requests from cache`() = runBlocking {
        val client = CountingSpeechClient()
        val service = SpeechService(client = client, cache = InMemoryTtsCache())

        val first = service.synthesize(SpeechRequest("  Xin chào  ", "vi-VN"))
        val second = service.synthesize(SpeechRequest("Xin chào", "vi"))

        assertNotNull(first)
        assertNotNull(second)
        assertContentEquals(first.bytes, second!!.bytes)
        assertEquals(1, client.synthesizeCalls.get())
        assertEquals("Xin chào", client.lastText)
        assertEquals("vi", client.lastLanguage)
    }

    @Test
    fun `speech service cache misses when text language or voice differ`() = runBlocking {
        val client = CountingSpeechClient()
        val service = SpeechService(client = client, cache = InMemoryTtsCache())

        service.startup()
        service.synthesize(SpeechRequest("Hello", "en"))
        service.synthesize(SpeechRequest("Hello!", "en"))
        service.synthesize(SpeechRequest("Hello", "vi"))

        assertEquals(3, client.synthesizeCalls.get())
    }

    @Test
    fun `speech service single-flights concurrent identical synthesis`() = runBlocking {
        val gate = Mutex(locked = true)
        val client = object : SpeechClient {
            val calls = AtomicInteger(0)

            override suspend fun designVoice(): String = "shared-voice"

            override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech {
                calls.incrementAndGet()
                gate.withLock { }
                delay(20)
                return GeneratedSpeech(byteArrayOf(42), "audio/mpeg")
            }
        }
        val service = SpeechService(client = client, cache = InMemoryTtsCache())
        service.startup()

        val jobs = List(8) {
            async {
                service.synthesize(SpeechRequest("same phrase", "en"))
            }
        }
        gate.unlock()
        val results = jobs.awaitAll()

        assertEquals(1, client.calls.get())
        results.forEach { speech ->
            assertNotNull(speech)
            assertContentEquals(byteArrayOf(42), speech!!.bytes)
        }
    }

    @Test
    fun `default TTS cache directory is under data`() {
        val emptyEnvFile = Files.createTempFile("one-verandah", ".env").toFile()
        try {
            val config = apap.oneverandah.config.AppConfig.load(emptyEnvFile)
            assertEquals("data/tts-cache", config.ttsCacheDir)
        } finally {
            emptyEnvFile.delete()
        }
    }

    private class CountingSpeechClient : SpeechClient {
        val synthesizeCalls = AtomicInteger(0)
        var lastText: String? = null
        var lastLanguage: String? = null
        var lastVoice: String? = null

        override suspend fun designVoice(): String = "default-voice"

        override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech {
            synthesizeCalls.incrementAndGet()
            lastText = request.text
            lastLanguage = request.languageTag
            lastVoice = voiceId
            return GeneratedSpeech(
                bytes = byteArrayOf(request.text.length.toByte(), voiceId.length.toByte()),
                contentType = "audio/mpeg",
                words = listOf(
                    SpeechWordTiming(
                        text = request.text.take(1).ifEmpty { "x" },
                        beginMs = 0,
                        endMs = 10,
                    ),
                ),
            )
        }
    }
}
