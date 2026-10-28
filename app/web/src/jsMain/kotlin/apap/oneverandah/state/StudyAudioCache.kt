package apap.oneverandah.state

import apap.oneverandah.api.SpeechRequest
import apap.oneverandah.api.SpeechResponse
import kotlinx.browser.window
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val STUDY_AUDIO_CACHE_PREFIX = "oneverandah.studyAudio.v1|"
private const val STUDY_AUDIO_CACHE_INDEX_KEY = "oneverandah.studyAudio.v1.index"

internal interface StudyAudioStorage {
    fun getItem(key: String): String?
    fun setItem(key: String, value: String)
    fun removeItem(key: String)
}

private object SessionStudyAudioStorage : StudyAudioStorage {
    override fun getItem(key: String): String? = window.sessionStorage.getItem(key)

    override fun setItem(key: String, value: String) {
        window.sessionStorage.setItem(key, value)
    }

    override fun removeItem(key: String) {
        window.sessionStorage.removeItem(key)
    }
}

internal class StudyAudioCacheStore(private val storage: StudyAudioStorage) {
    private val memoryCache = mutableMapOf<String, SpeechResponse>()
    private val json = Json { ignoreUnknownKeys = true }
    private val keysSerializer = ListSerializer(String.serializer())

    fun get(request: SpeechRequest): SpeechResponse? {
        val key = cacheKey(request) ?: return null
        memoryCache[key]?.let {
            touch(key)
            return it
        }
        val serialized = runCatching { storage.getItem(key) }.getOrNull() ?: return null
        val response = runCatching {
            json.decodeFromString(SpeechResponse.serializer(), serialized)
        }.getOrNull()
        if (response == null) {
            runCatching { storage.removeItem(key) }
            removeFromIndex(key)
            return null
        }
        memoryCache[key] = response
        touch(key)
        return response
    }

    fun put(request: SpeechRequest, response: SpeechResponse) {
        val key = cacheKey(request) ?: return
        memoryCache[key] = response
        val serialized = runCatching {
            json.encodeToString(SpeechResponse.serializer(), response)
        }.getOrNull() ?: return
        val keys = readKeys().apply {
            remove(key)
            add(key)
        }
        val serializedKeys = encodeKeys(keys)
        try {
            storage.setItem(key, serialized)
            storage.setItem(STUDY_AUDIO_CACHE_INDEX_KEY, serializedKeys)
        } catch (_: Throwable) {
            evictOldestEntries(keys, key, serialized.length)
            runCatching {
                storage.setItem(key, serialized)
                storage.setItem(STUDY_AUDIO_CACHE_INDEX_KEY, encodeKeys(keys))
            }
        }
    }

    suspend fun load(
        request: SpeechRequest,
        fetch: suspend (SpeechRequest) -> SpeechResponse,
    ): SpeechResponse {
        get(request)?.let { return it }
        return fetch(request).also { put(request, it) }
    }

    private fun cacheKey(request: SpeechRequest): String? {
        val normalized = request.normalizedOrNull() ?: return null
        return "$STUDY_AUDIO_CACHE_PREFIX${normalized.languageTag}|${normalized.text}"
    }

    private fun touch(key: String) {
        val keys = readKeys()
        if (!keys.remove(key)) return
        keys.add(key)
        runCatching { storage.setItem(STUDY_AUDIO_CACHE_INDEX_KEY, encodeKeys(keys)) }
    }

    private fun removeFromIndex(key: String) {
        val keys = readKeys()
        if (keys.remove(key)) {
            runCatching { storage.setItem(STUDY_AUDIO_CACHE_INDEX_KEY, encodeKeys(keys)) }
        }
    }

    private fun readKeys(): MutableList<String> = runCatching {
        storage.getItem(STUDY_AUDIO_CACHE_INDEX_KEY)
            ?.let { json.decodeFromString(keysSerializer, it) }
            .orEmpty()
            .filter { it.startsWith(STUDY_AUDIO_CACHE_PREFIX) }
            .distinct()
            .toMutableList()
    }.getOrDefault(mutableListOf())

    private fun encodeKeys(keys: List<String>): String = json.encodeToString(keysSerializer, keys)

    private fun evictOldestEntries(keys: MutableList<String>, keyToKeep: String, requiredChars: Int) {
        var freedChars = 0
        val oldestFirst = keys.toList()
        for (oldestKey in oldestFirst) {
            if (freedChars >= requiredChars) break
            if (oldestKey == keyToKeep) continue
            val size = runCatching { storage.getItem(oldestKey)?.length ?: 0 }.getOrDefault(0)
            val removed = runCatching { storage.removeItem(oldestKey) }.isSuccess
            if (removed) {
                keys.remove(oldestKey)
                freedChars += size
            }
        }
    }
}

object StudyAudioCache {
    private val store = StudyAudioCacheStore(SessionStudyAudioStorage)

    fun get(request: SpeechRequest): SpeechResponse? = store.get(request)

    fun put(request: SpeechRequest, response: SpeechResponse) = store.put(request, response)

    suspend fun load(
        request: SpeechRequest,
        fetch: suspend (SpeechRequest) -> SpeechResponse,
    ): SpeechResponse = store.load(request, fetch)

    fun dataUrl(response: SpeechResponse): String =
        "data:${response.contentType};base64,${response.audioBase64}"
}
