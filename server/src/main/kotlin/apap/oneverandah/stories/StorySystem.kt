package apap.oneverandah.stories

import apap.oneverandah.chat.StudyService
import apap.oneverandah.chat.StudySessionsService
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.phrases.PhraseSystem
import apap.oneverandah.phrases.ReferencePhotoRepository
import apap.oneverandah.stories.arango.ArangoStoryCharacterRepository
import apap.oneverandah.stories.arango.ArangoStoryRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns Mini Story persistence, character portraits, and Qwen photo generation
 * that reuses phrasebook scenes plus study-session characters.
 */
class StorySystem(
    val service: StoryService,
    val stories: StoryRepository,
    val characters: StoryCharacterRepository,
    val referencePhotos: ReferencePhotoRepository,
    val storage: StorageService,
    val qwen: QwenClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val jobIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(StorySystem::class.java)
    private val jobs = ConcurrentHashMap<String, GenerateStoryImageResponse>()

    /** Start Qwen in the background so the admin request can return immediately. */
    fun enqueueGenerate(request: GenerateStoryImageRequest): GenerateStoryImageResponse {
        val prompt = request.prompt.trim()
        require(prompt.isNotEmpty()) { "prompt is required" }
        val jobId = "imgjob-${jobIdGenerator()}"
        val pending = GenerateStoryImageResponse(
            jobId = jobId,
            status = StoryLogic.IMAGE_JOB_PENDING,
        )
        jobs[jobId] = pending
        scope.launch {
            try {
                val result = generateImage(request)
                jobs[jobId] = result.copy(jobId = jobId, status = StoryLogic.IMAGE_JOB_READY)
            } catch (e: Throwable) {
                log.warn("Story image job {} failed: {}", jobId, e.message)
                jobs[jobId] = GenerateStoryImageResponse(
                    jobId = jobId,
                    status = StoryLogic.IMAGE_JOB_FAILED,
                    error = e.message ?: "Image generation failed",
                )
            }
        }
        return pending
    }

    fun generateJob(jobId: String): GenerateStoryImageResponse? = jobs[jobId]

    suspend fun generateImage(request: GenerateStoryImageRequest): GenerateStoryImageResponse {
        val prompt = request.prompt.trim()
        require(prompt.isNotEmpty()) { "prompt is required" }

        val sceneUrls = request.scenePhotoIds.mapNotNull { id ->
            referencePhotos.findById(id)?.imageUrl?.takeIf { it.isNotBlank() }
        }
        val characterUrls = request.characterIds.mapNotNull { id ->
            service.findCharacter(id)?.imageUrl?.takeIf { it.isNotBlank() }
        }
        val refs = (sceneUrls + characterUrls).distinct()
        val fullPrompt = buildString {
            append(prompt)
            if (!prompt.contains(STORY_ART_STYLE, ignoreCase = true)) {
                append(". Art style: $STORY_ART_STYLE, warm storytelling vibe, no text, no letters, no watermark.")
            }
        }
        val generated = qwen.generate(fullPrompt, refs)
        val objectKey = "stories/generated/${UUID.randomUUID()}.${generated.extension}"
        val imageUrl = storage.upload(objectKey, generated)

        val storyId = request.storyId?.trim()?.takeIf { it.isNotEmpty() }
        val story = if (storyId != null) {
            service.attachImage(
                storyId = storyId,
                imageUrl = imageUrl,
                pageId = request.pageId,
                asCover = request.asCover,
            )
        } else {
            null
        }
        return GenerateStoryImageResponse(imageUrl = imageUrl, story = story)
    }

    suspend fun uploadImage(bytes: ByteArray, contentType: String, extension: String): String {
        require(bytes.isNotEmpty()) { "An image file is required" }
        val objectKey = "stories/uploads/${UUID.randomUUID()}.$extension"
        return storage.upload(
            objectKey,
            GeneratedImage(bytes = bytes, contentType = contentType, extension = extension),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(StorySystem::class.java)

        suspend fun create(
            config: AppConfig,
            storage: StorageService,
            phraseSystem: PhraseSystem,
            studyService: StudyService,
            studySessionsService: StudySessionsService,
            httpClient: HttpClient? = null,
        ): StorySystem {
            val storyRepo = ArangoStoryRepository.connect(config.arango, httpClient)
                ?: InMemoryStoryRepository().also {
                    log.info(
                        "Story repository using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }
            val characterRepo = ArangoStoryCharacterRepository.connect(config.arango, httpClient)
                ?: InMemoryStoryCharacterRepository().also {
                    log.info(
                        "Story character repository using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }
            val service = StoryService(
                stories = storyRepo,
                characters = characterRepo,
                tokenizer = StoryService.tokenizerFrom(studyService),
                languageDetector = StoryService.detectorFrom(studyService),
                studyCharacterSource = StoryCharacterSource { studySessionsService.listRecentCharacters() },
            )
            return StorySystem(
                service = service,
                stories = storyRepo,
                characters = characterRepo,
                referencePhotos = phraseSystem.referencePhotos,
                storage = storage,
                qwen = phraseSystem.qwen,
            )
        }
    }
}

private const val STORY_ART_STYLE = "expressive storybook art"
