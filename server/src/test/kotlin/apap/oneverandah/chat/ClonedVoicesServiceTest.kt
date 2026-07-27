package apap.oneverandah.chat

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ClonedVoicesServiceTest {
    @Test
    fun `upload activates first voice for a language and later ones stay inactive`() = runBlocking {
        val enrollment = RecordingEnrollment()
        val service = service(enrollment)

        val first = service.upload("Warm VI", "vi", byteArrayOf(1, 2, 3), "audio/wav", "warm.wav")
        val second = service.upload("Bright VI", "vi-VN", byteArrayOf(4, 5), "audio/mpeg", "bright.mp3")

        assertTrue(first.active)
        assertFalse(second.active)
        assertEquals("vi", first.languageTag)
        assertEquals("vi", second.languageTag)
        assertEquals(2, enrollment.enrollCalls)
        assertEquals(first.providerVoiceId, service.resolveVoiceId("vi"))
    }

    @Test
    fun `activate switches the active voice for that language only`() = runBlocking {
        val service = service()
        val viA = service.upload("A", "vi", byteArrayOf(1), "audio/wav", "a.wav")
        val viB = service.upload("B", "vi", byteArrayOf(2), "audio/wav", "b.wav")
        val all = service.upload("Fallback", "all", byteArrayOf(3), "audio/wav", "all.wav")

        service.activate(viB.id)

        val items = service.list().items.associateBy { it.id }
        assertFalse(items.getValue(viA.id).active)
        assertTrue(items.getValue(viB.id).active)
        assertTrue(items.getValue(all.id).active)
        assertEquals(items.getValue(viB.id).providerVoiceId, service.resolveVoiceId("vi"))
        assertEquals(items.getValue(all.id).providerVoiceId, service.resolveVoiceId("en"))
    }

    @Test
    fun `resolve prefers language-specific voice over fallback`() = runBlocking {
        val service = service()
        val all = service.upload("All", "all", byteArrayOf(1), "audio/wav", "all.wav")
        val vi = service.upload("VI", "vi", byteArrayOf(2), "audio/wav", "vi.wav")

        assertEquals(vi.providerVoiceId, service.resolveVoiceId("vi-VN"))
        assertEquals(all.providerVoiceId, service.resolveVoiceId("en"))
    }

    @Test
    fun `speech service uses resolved cloned voice when present`() = runBlocking {
        val voices = service()
        val uploaded = voices.upload("VI", "vi", byteArrayOf(9), "audio/wav", "vi.wav")
        val client = RecordingSpeechClient()
        val speech = SpeechService(client, voices)

        speech.synthesize(SpeechRequest("xin chào", "vi"))

        assertEquals(0, client.designCalls)
        assertEquals(uploaded.providerVoiceId, client.lastVoiceId)
        assertEquals("xin chào", client.lastText)
    }

    @Test
    fun `speech service falls back to designed voice without cloned match`() = runBlocking {
        val client = RecordingSpeechClient()
        val speech = SpeechService(client, service())

        speech.synthesize(SpeechRequest("hello", "en"))

        assertEquals(1, client.designCalls)
        assertEquals("designed-voice", client.lastVoiceId)
    }

    @Test
    fun `delete removes voice and clears resolution`() = runBlocking {
        val enrollment = RecordingEnrollment()
        val service = service(enrollment)
        val voice = service.upload("VI", "vi", byteArrayOf(1), "audio/wav", "vi.wav")

        assertTrue(service.delete(voice.id))
        assertNull(service.resolveVoiceId("vi"))
        assertEquals(listOf(voice.providerVoiceId), enrollment.deleted)
    }

    private fun service(
        enrollment: VoiceEnrollmentClient = RecordingEnrollment(),
    ) = ClonedVoicesService(
        repository = InMemoryClonedVoicesRepository(),
        storage = FakeStorage(),
        enrollment = enrollment,
        clock = { 1_000L },
        idGenerator = sequenceOf(
            "aaaaaaaa",
            "bbbbbbbb",
            "cccccccc",
            "dddddddd",
            "eeeeeeee",
        ).iterator()::next,
    )
}

private class FakeStorage : StorageService {
    override val kind: String = "fake"
    override suspend fun upload(objectKey: String, image: GeneratedImage): String =
        "https://example.test/$objectKey"
}

private class RecordingEnrollment : VoiceEnrollmentClient {
    var enrollCalls = 0
    val deleted = mutableListOf<String>()

    override suspend fun enroll(
        name: String,
        sampleUrl: String,
        sampleBytes: ByteArray,
        contentType: String,
    ): String {
        enrollCalls += 1
        return "provider-$enrollCalls"
    }

    override suspend fun deleteProviderVoice(providerVoiceId: String) {
        deleted += providerVoiceId
    }
}

private class RecordingSpeechClient : SpeechClient {
    var designCalls = 0
    var lastVoiceId: String? = null
    var lastText: String? = null

    override suspend fun designVoice(): String {
        designCalls += 1
        return "designed-voice"
    }

    override suspend fun synthesize(request: SpeechRequest, voiceId: String): GeneratedSpeech {
        lastVoiceId = voiceId
        lastText = request.text
        return GeneratedSpeech(byteArrayOf(1), "audio/mpeg")
    }
}
