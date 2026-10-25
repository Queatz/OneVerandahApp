package apap.oneverandah.phrases

import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.PromptFactory
import apap.oneverandah.images.qwen.DashScopeQwenClient
import apap.oneverandah.images.qwen.PlaceholderQwenClient
import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.model.LocalizedText
import apap.oneverandah.http.longRunningHttpClient
import apap.oneverandah.http.shortHttpClient
import apap.oneverandah.phrases.arango.ArangoPhraseRepository
import apap.oneverandah.phrases.arango.ArangoReferencePhotoRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Owns phrasebook + reference-photo persistence, Qwen scene generation for
 * phrase/cover photos, and the shared storage backend used for uploads.
 */
class PhraseSystem private constructor(
    val config: AppConfig,
    val phrases: PhraseRepository,
    val referencePhotos: ReferencePhotoRepository,
    val storage: StorageService,
    val qwen: QwenClient,
    private val ownsHttpClient: Boolean,
    private val httpClient: HttpClient?,
    private val ownsLongHttpClient: Boolean,
    private val longHttpClient: HttpClient?,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(PhraseSystem::class.java)

    fun startup() {
        scope.launch {
            runCatching { phrases.seedIfEmpty() }
                .onSuccess { log.info("Phrasebook seed ensured") }
                .onFailure { log.warn("Phrasebook seed failed: {}", it.message) }
        }
    }

    fun shutdown() {
        scope.cancel()
        if (ownsHttpClient) runCatching { httpClient?.close() }
        if (ownsLongHttpClient) runCatching { longHttpClient?.close() }
    }

    /** Public phrasebook cover URL, if an admin has generated one. */
    suspend fun coverImageUrl(): String? =
        phrases.findById(COVER_PHRASE_ID)?.imageUrl?.takeIf { it.isNotBlank() }

    /**
     * Crafts a scene with Qwen (R2I when [referencePhotoIds] resolve), stores
     * the bytes, and optionally attaches the URL to a phrase and/or the cover.
     */
    suspend fun generateImage(request: GeneratePhraseImageRequest): GeneratePhraseImageResponse {
        val prompt = request.prompt.trim()
        require(prompt.isNotEmpty()) { "prompt is required" }

        val refUrls = request.referencePhotoIds
            .mapNotNull { id -> referencePhotos.findById(id)?.imageUrl?.takeIf { it.isNotBlank() } }
            .distinct()

        val generated = qwen.generate(PromptFactory.withArtDirection(prompt), refUrls)
        val objectKey = "phrases/generated/${UUID.randomUUID()}.${generated.extension}"
        val imageUrl = storage.upload(objectKey, generated)

        var updatedPhrase: Phrase? = null
        val phraseId = request.phraseId?.trim()?.takeIf { it.isNotEmpty() && it != COVER_PHRASE_ID }
        if (phraseId != null) {
            val existing = phrases.findById(phraseId)
                ?: error("Phrase '$phraseId' not found")
            updatedPhrase = existing.copy(
                imageUrl = imageUrl,
                updatedAt = System.currentTimeMillis(),
            )
            phrases.save(updatedPhrase)
        }

        var coverUrl: String? = null
        if (request.asCover) {
            coverUrl = imageUrl
            persistCover(imageUrl)
        }

        return GeneratePhraseImageResponse(
            imageUrl = imageUrl,
            phrase = updatedPhrase,
            coverImageUrl = coverUrl ?: coverImageUrl(),
        )
    }

    private suspend fun persistCover(imageUrl: String) {
        val now = System.currentTimeMillis()
        val existing = phrases.findById(COVER_PHRASE_ID)
        val cover = (existing ?: Phrase(
            id = COVER_PHRASE_ID,
            locationKey = "cover",
            location = LocalizedText(en = "Cover", vi = "Bìa"),
            icon = "image",
            spoken = LocalizedText(en = "Phrasebook cover", vi = "Ảnh bìa"),
            verse = LocalizedText(en = "", vi = ""),
            rawAsk = LocalizedText(en = "", vi = ""),
            instructions = PhraseDefaults.instructions,
            approved = false,
            createdAt = now,
        )).copy(
            imageUrl = imageUrl,
            approved = false,
            updatedAt = now,
        )
        phrases.save(cover)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PhraseSystem::class.java)

        /** Internal pseudo-phrase id used to persist the phrasebook cover URL. */
        const val COVER_PHRASE_ID = InMemoryPhraseRepository.COVER_PHRASE_ID

        /**
         * Builds a [PhraseSystem] reusing the image system's [storage] and
         * optionally its [sharedHttpClient] so Arango connections stay consistent.
         */
        suspend fun create(
            config: AppConfig,
            storage: StorageService,
            sharedHttpClient: HttpClient? = null,
            sharedLongHttpClient: HttpClient? = null,
        ): PhraseSystem {
            val ownsClient = sharedHttpClient == null
            val ownsLongClient = config.qwen.enabled && sharedLongHttpClient == null
            val client = sharedHttpClient ?: shortHttpClient()
            val qwenClient = sharedLongHttpClient ?: if (config.qwen.enabled) longRunningHttpClient() else null

            val arangoPhrases = ArangoPhraseRepository.connect(config.arango, client)
            if (arangoPhrases == null && config.isProduction) {
                if (ownsClient) runCatching { client.close() }
                if (ownsLongClient) runCatching { qwenClient?.close() }
                error(
                    "Production phrases require ArangoDB at ${config.arango.baseUrl}. " +
                        "Set ARANGO_HOST (and credentials) so phrase edits/reactions persist across restarts.",
                )
            }
            val phraseRepo: PhraseRepository = arangoPhrases ?: InMemoryPhraseRepository().also {
                log.info(
                    "Phrase repository using in-memory development store " +
                        "(data resets on restart; configure ARANGO_HOST to persist)",
                )
            }
            log.info(
                "Phrase repository backend: {}",
                if (arangoPhrases != null) "arangodb" else "in-memory",
            )

            val photoRepo = ArangoReferencePhotoRepository.connect(config.arango, client)
                ?: InMemoryReferencePhotoRepository().also {
                    log.info(
                        "Reference photo repository using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }

            val qwen: QwenClient = if (config.qwen.enabled) {
                // Pass local image root so reference photos stored on disk can be
                // re-hosted to DashScope temp OSS (localhost URLs are rejected).
                DashScopeQwenClient(
                    config = config.qwen,
                    client = requireNotNull(qwenClient),
                    localImageRoot = java.io.File(config.localStorageDir),
                    publicBaseUrl = config.publicBaseUrl,
                )
            } else {
                PlaceholderQwenClient()
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return PhraseSystem(
                config = config,
                phrases = phraseRepo,
                referencePhotos = photoRepo,
                storage = storage,
                qwen = qwen,
                ownsHttpClient = ownsClient,
                httpClient = if (ownsClient) client else null,
                ownsLongHttpClient = ownsLongClient,
                longHttpClient = if (ownsLongClient) qwenClient else null,
                scope = scope,
            )
        }
    }
}
