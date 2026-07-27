package apap.oneverandah.chat

import apap.oneverandah.api.ClonedVoice
import apap.oneverandah.api.ClonedVoicesResponse
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.model.Language
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ClonedVoiceRecord(
    val id: String,
    val name: String,
    val languageTag: String,
    val providerVoiceId: String,
    val sampleUrl: String? = null,
    val active: Boolean = false,
    val createdAt: Long,
) {
    fun toApi() = ClonedVoice(
        id = id,
        name = name,
        languageTag = languageTag,
        providerVoiceId = providerVoiceId,
        sampleUrl = sampleUrl,
        active = active,
        createdAt = createdAt,
    )
}

interface ClonedVoicesRepository {
    suspend fun list(): List<ClonedVoiceRecord>
    suspend fun findById(id: String): ClonedVoiceRecord?
    suspend fun save(record: ClonedVoiceRecord)
    suspend fun delete(id: String): Boolean
    /** Deactivates every voice for [languageTag] except [exceptId] (nullable = deactivate all). */
    suspend fun deactivateLanguage(languageTag: String, exceptId: String? = null)
}

class InMemoryClonedVoicesRepository : ClonedVoicesRepository {
    private val items = ConcurrentHashMap<String, ClonedVoiceRecord>()
    private val mutex = Mutex()

    override suspend fun list(): List<ClonedVoiceRecord> =
        mutex.withLock { items.values.sortedByDescending { it.createdAt } }

    override suspend fun findById(id: String): ClonedVoiceRecord? =
        mutex.withLock { items[id] }

    override suspend fun save(record: ClonedVoiceRecord) {
        mutex.withLock { items[record.id] = record }
    }

    override suspend fun delete(id: String): Boolean =
        mutex.withLock { items.remove(id) != null }

    override suspend fun deactivateLanguage(languageTag: String, exceptId: String?) {
        mutex.withLock {
            items.replaceAll { id, record ->
                if (record.languageTag == languageTag && record.active && id != exceptId) {
                    record.copy(active = false)
                } else {
                    record
                }
            }
        }
    }
}

interface VoiceEnrollmentClient {
    /**
     * Enrolls a cloned voice from a publicly reachable sample URL and/or raw bytes.
     * @return provider voice id used as the TTS `voice` parameter.
     */
    suspend fun enroll(
        name: String,
        sampleUrl: String,
        sampleBytes: ByteArray,
        contentType: String,
    ): String

    suspend fun deleteProviderVoice(providerVoiceId: String) {}
}

/**
 * Admin-managed cloned voices used by [SpeechService] when synthesizing speech.
 *
 * Resolution order for a request language:
 * 1. Active voice for that language tag
 * 2. Active fallback voice ([ClonedVoice.FALLBACK_LANGUAGE_TAG])
 * 3. Configured default system voice (handled by the caller)
 */
class ClonedVoicesService(
    private val repository: ClonedVoicesRepository,
    private val storage: StorageService,
    private val enrollment: VoiceEnrollmentClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : VoiceResolver {
    suspend fun list(): ClonedVoicesResponse =
        ClonedVoicesResponse(items = repository.list().map { it.toApi() })

    suspend fun upload(
        rawName: String,
        rawLanguageTag: String,
        bytes: ByteArray,
        contentType: String,
        originalFileName: String?,
    ): ClonedVoice {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "A non-blank name is required" }
        require(bytes.isNotEmpty()) { "An audio file is required" }
        val languageTag = requireNotNull(ClonedVoice.normalizeLanguageTag(rawLanguageTag)) {
            "languageTag is required"
        }
        val extension = audioExtensionFor(contentType, originalFileName)
        val safeName = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "voice" }
            .take(40)
        val id = "voice-$safeName-${idGenerator().take(8)}"
        val objectKey = "voices/$safeName-$id.$extension"
        val sampleUrl = storage.upload(
            objectKey,
            GeneratedImage(bytes = bytes, contentType = contentType, extension = extension),
        )
        val providerVoiceId = enrollment.enroll(
            name = safeName.take(10).ifBlank { "ovvoice" },
            sampleUrl = sampleUrl,
            sampleBytes = bytes,
            contentType = contentType,
        )
        // First voice for a language becomes active automatically.
        val existingForLanguage = repository.list().any { it.languageTag == languageTag }
        val active = !existingForLanguage
        if (active) {
            repository.deactivateLanguage(languageTag, exceptId = id)
        }
        val record = ClonedVoiceRecord(
            id = id,
            name = name,
            languageTag = languageTag,
            providerVoiceId = providerVoiceId,
            sampleUrl = sampleUrl,
            active = active,
            createdAt = clock(),
        )
        repository.save(record)
        return record.toApi()
    }

    suspend fun activate(id: String): ClonedVoice {
        val existing = repository.findById(id)
            ?: error("Voice '$id' not found")
        repository.deactivateLanguage(existing.languageTag, exceptId = id)
        val updated = existing.copy(active = true)
        repository.save(updated)
        return updated.toApi()
    }

    /**
     * @return true when deleted.
     */
    suspend fun delete(id: String): Boolean {
        val existing = repository.findById(id) ?: return false
        runCatching { enrollment.deleteProviderVoice(existing.providerVoiceId) }
        return repository.delete(id)
    }

    override suspend fun resolveVoiceId(languageTag: String): String? {
        val normalized = Language.fromTag(languageTag).tag
        val items = repository.list()
        val specific = items.firstOrNull {
            it.active && it.languageTag == normalized
        }
        if (specific != null) return specific.providerVoiceId
        return items.firstOrNull {
            it.active && it.languageTag == ClonedVoice.FALLBACK_LANGUAGE_TAG
        }?.providerVoiceId
    }
}

private fun audioExtensionFor(contentType: String, fileName: String?): String {
    val fromName = fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf {
        it in setOf("wav", "mp3", "m4a", "ogg", "webm", "aac", "flac")
    }
    if (fromName != null) return fromName
    return when {
        contentType.contains("wav") -> "wav"
        contentType.contains("mpeg") || contentType.contains("mp3") -> "mp3"
        contentType.contains("mp4") || contentType.contains("m4a") -> "m4a"
        contentType.contains("ogg") -> "ogg"
        contentType.contains("webm") -> "webm"
        contentType.contains("flac") -> "flac"
        else -> "wav"
    }
}

/** No-op enrollment used when Qwen is disabled (local/dev without API key). */
class PlaceholderVoiceEnrollmentClient(
    private val idGenerator: () -> String = { "local-${UUID.randomUUID().toString().take(8)}" },
) : VoiceEnrollmentClient {
    override suspend fun enroll(
        name: String,
        sampleUrl: String,
        sampleBytes: ByteArray,
        contentType: String,
    ): String = "placeholder-$name-${idGenerator()}"
}

fun interface VoiceResolver {
    suspend fun resolveVoiceId(languageTag: String): String?
}
