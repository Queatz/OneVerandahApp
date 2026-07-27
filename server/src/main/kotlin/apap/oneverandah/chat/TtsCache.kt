package apap.oneverandah.chat

import apap.oneverandah.api.SpeechWordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Server-side cache for synthesized speech.
 *
 * Entries are keyed by the exact synthesis inputs that affect output:
 * text, language tag, and voice id. Implementations must treat those three
 * fields as a composite exact-match key.
 */
interface TtsCache {
    suspend fun get(text: String, languageTag: String, voiceId: String): GeneratedSpeech?

    suspend fun put(text: String, languageTag: String, voiceId: String, speech: GeneratedSpeech)
}

/** Canonical cache coordinates for one TTS utterance. */
data class TtsCacheKey(
    val text: String,
    val languageTag: String,
    val voiceId: String,
) {
    /**
     * Stable fingerprint used for filesystem paths and lock maps.
     * Collision-resistant; loaders still verify exact field equality.
     */
    fun fingerprint(): String {
        val material = buildString {
            append(text.length)
            append('\u0000')
            append(text)
            append('\u0000')
            append(languageTag)
            append('\u0000')
            append(voiceId)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}

/**
 * Bounded in-memory LRU cache. Suitable for tests and as the hot tier in front
 * of a durable [FileTtsCache].
 */
class InMemoryTtsCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : TtsCache {
    private val mutex = Mutex()
    private val entries = object : LinkedHashMap<String, CacheRow>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheRow>?): Boolean =
            maxEntries > 0 && size > maxEntries
    }

    override suspend fun get(text: String, languageTag: String, voiceId: String): GeneratedSpeech? {
        val key = TtsCacheKey(text, languageTag, voiceId)
        return mutex.withLock {
            val row = entries[key.fingerprint()] ?: return@withLock null
            if (row.key != key) return@withLock null
            row.speech.isolatedCopy()
        }
    }

    override suspend fun put(text: String, languageTag: String, voiceId: String, speech: GeneratedSpeech) {
        if (maxEntries <= 0) return
        val key = TtsCacheKey(text, languageTag, voiceId)
        mutex.withLock {
            entries[key.fingerprint()] = CacheRow(key, speech.isolatedCopy())
        }
    }

    private data class CacheRow(
        val key: TtsCacheKey,
        val speech: GeneratedSpeech,
    )

    companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 512
    }
}

/**
 * Durable on-disk TTS cache.
 *
 * Layout (sharded for large corpora):
 * ```
 * <root>/<aa>/<fingerprint>.json   metadata + word timings + exact key fields
 * <root>/<aa>/<fingerprint>.mp3    audio payload (extension from content type)
 * ```
 *
 * Lookups are O(1) path resolution by SHA-256 fingerprint. On hit the metadata
 * is verified against the requested text/language/voice before returning audio,
 * so hash collisions cannot serve the wrong clip.
 */
class FileTtsCache(
    private val rootDir: File,
) : TtsCache {
    private val log = LoggerFactory.getLogger(FileTtsCache::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    override suspend fun get(text: String, languageTag: String, voiceId: String): GeneratedSpeech? =
        withContext(Dispatchers.IO) {
            val key = TtsCacheKey(text, languageTag, voiceId)
            val metaFile = metaFile(key)
            if (!metaFile.isFile || metaFile.length() == 0L) return@withContext null
            val meta = runCatching {
                json.decodeFromString(TtsCacheMeta.serializer(), metaFile.readText(Charsets.UTF_8))
            }.getOrElse {
                log.warn("Failed to read TTS cache metadata {}: {}", metaFile.path, it.message)
                return@withContext null
            }
            if (
                meta.text != key.text ||
                meta.languageTag != key.languageTag ||
                meta.voiceId != key.voiceId
            ) {
                log.warn("TTS cache fingerprint collision or stale metadata at {}", metaFile.path)
                return@withContext null
            }
            val audioFile = audioFile(key, meta.extension)
            if (!audioFile.isFile || audioFile.length() == 0L) {
                log.warn("TTS cache metadata present but audio missing for {}", key.fingerprint())
                return@withContext null
            }
            runCatching {
                GeneratedSpeech(
                    bytes = audioFile.readBytes(),
                    contentType = meta.contentType,
                    words = meta.words,
                )
            }.getOrElse {
                log.warn("Failed to read TTS cache audio {}: {}", audioFile.path, it.message)
                null
            }
        }

    override suspend fun put(text: String, languageTag: String, voiceId: String, speech: GeneratedSpeech) =
        withContext(Dispatchers.IO) {
            if (speech.bytes.isEmpty()) return@withContext
            val key = TtsCacheKey(text, languageTag, voiceId)
            val extension = extensionFor(speech.contentType)
            val meta = TtsCacheMeta(
                text = key.text,
                languageTag = key.languageTag,
                voiceId = key.voiceId,
                contentType = speech.contentType,
                extension = extension,
                createdAt = System.currentTimeMillis(),
                words = speech.words,
            )
            runCatching {
                val dir = shardDir(key)
                dir.mkdirs()
                val audio = audioFile(key, extension)
                val metaTarget = metaFile(key)
                // Write audio first, then metadata so a partial write never yields a false hit.
                val audioTmp = File(dir, "${audio.name}.tmp")
                val metaTmp = File(dir, "${metaTarget.name}.tmp")
                audioTmp.writeBytes(speech.bytes)
                metaTmp.writeText(json.encodeToString(TtsCacheMeta.serializer(), meta), Charsets.UTF_8)
                if (!audioTmp.renameTo(audio)) {
                    audio.writeBytes(speech.bytes)
                    audioTmp.delete()
                }
                if (!metaTmp.renameTo(metaTarget)) {
                    metaTarget.writeText(json.encodeToString(TtsCacheMeta.serializer(), meta), Charsets.UTF_8)
                    metaTmp.delete()
                }
                // Drop any leftover audio written under a previous content-type extension.
                dir.listFiles { file ->
                    file.isFile &&
                        file.nameWithoutExtension == key.fingerprint() &&
                        file.extension != "json" &&
                        file.extension != extension &&
                        !file.name.endsWith(".tmp")
                }?.forEach { it.delete() }
            }.onFailure {
                log.warn("Failed to persist TTS cache entry for fingerprint {}: {}", key.fingerprint(), it.message)
            }
            Unit
        }

    private fun shardDir(key: TtsCacheKey): File {
        val fingerprint = key.fingerprint()
        return File(rootDir, fingerprint.take(2))
    }

    private fun metaFile(key: TtsCacheKey): File =
        File(shardDir(key), "${key.fingerprint()}.json")

    private fun audioFile(key: TtsCacheKey, extension: String): File =
        File(shardDir(key), "${key.fingerprint()}.$extension")

    private fun extensionFor(contentType: String): String = when (contentType.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/ogg" -> "ogg"
        "audio/webm" -> "webm"
        "audio/mp4", "audio/aac" -> "m4a"
        else -> "bin"
    }
}

/**
 * Two-tier cache: fast in-process LRU in front of durable disk storage.
 * Disk remains the source of truth across process restarts.
 */
class TieredTtsCache(
    private val memory: TtsCache,
    private val disk: TtsCache,
) : TtsCache {
    override suspend fun get(text: String, languageTag: String, voiceId: String): GeneratedSpeech? {
        memory.get(text, languageTag, voiceId)?.let { return it }
        val fromDisk = disk.get(text, languageTag, voiceId) ?: return null
        memory.put(text, languageTag, voiceId, fromDisk)
        return fromDisk
    }

    override suspend fun put(text: String, languageTag: String, voiceId: String, speech: GeneratedSpeech) {
        memory.put(text, languageTag, voiceId, speech)
        disk.put(text, languageTag, voiceId, speech)
    }
}

/**
 * No-op cache used when caching is explicitly disabled.
 */
object NoOpTtsCache : TtsCache {
    override suspend fun get(text: String, languageTag: String, voiceId: String): GeneratedSpeech? = null
    override suspend fun put(text: String, languageTag: String, voiceId: String, speech: GeneratedSpeech) = Unit
}

@Serializable
internal data class TtsCacheMeta(
    val text: String,
    val languageTag: String,
    val voiceId: String,
    val contentType: String,
    val extension: String,
    val createdAt: Long,
    val words: List<SpeechWordTiming> = emptyList(),
)

/**
 * Defensive deep copy so callers cannot mutate cached byte arrays in place.
 * Named to avoid clashing with the data-class [GeneratedSpeech.copy] shallow copy.
 */
private fun GeneratedSpeech.isolatedCopy(): GeneratedSpeech = GeneratedSpeech(
    bytes = bytes.copyOf(),
    contentType = contentType,
    words = words,
)

/**
 * Builds the production TTS cache stack: in-memory LRU over a sharded file store.
 */
fun createTtsCache(rootDir: File, memoryEntries: Int = InMemoryTtsCache.DEFAULT_MAX_ENTRIES): TtsCache =
    TieredTtsCache(
        memory = InMemoryTtsCache(maxEntries = memoryEntries),
        disk = FileTtsCache(rootDir),
    )

/**
 * Striped locks used by [SpeechService] so concurrent identical synthesis
 * requests share one upstream TTS call (single-flight) without unbounded
 * per-key lock growth.
 */
internal class TtsSynthesisGate(
    stripeCount: Int = DEFAULT_STRIPES,
) {
    private val stripes = Array(stripeCount.coerceAtLeast(1)) { Mutex() }

    suspend fun <T> withLock(fingerprint: String, block: suspend () -> T): T {
        val index = (fingerprint.hashCode() ushr 1) % stripes.size
        return stripes[index].withLock { block() }
    }

    companion object {
        const val DEFAULT_STRIPES: Int = 64
    }
}
