package apap.oneverandah.stories

import apap.oneverandah.chat.StudyService
import apap.oneverandah.chat.StudySessionsService
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.PromptFactory
import apap.oneverandah.images.qwen.QwenClient
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import apap.oneverandah.phrases.PhraseSystem
import apap.oneverandah.phrases.ReferencePhotoRepository
import apap.oneverandah.stories.arango.ArangoStoryCharacterRepository
import apap.oneverandah.stories.arango.ArangoStoryRepository
import apap.oneverandah.stories.arango.ArangoStoryStudyCache
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
    private val characterJobs = ConcurrentHashMap<String, GenerateStoryCharacterResponse>()
    private val publishJobs = ConcurrentHashMap<String, StoryPublishResponse>()

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

    /** Start a study-style character portrait in the background. */
    fun enqueueGenerateCharacter(request: GenerateStoryCharacterRequest): GenerateStoryCharacterResponse {
        val name = request.name.trim()
        require(name.isNotEmpty()) { "A character name is required" }
        val prompt = request.prompt.trim()
        require(prompt.isNotEmpty()) { "prompt is required" }
        val jobId = "charjob-${jobIdGenerator()}"
        val pending = GenerateStoryCharacterResponse(
            jobId = jobId,
            status = StoryLogic.IMAGE_JOB_PENDING,
        )
        characterJobs[jobId] = pending
        scope.launch {
            try {
                val character = generateCharacter(request)
                characterJobs[jobId] = GenerateStoryCharacterResponse(
                    character = character,
                    jobId = jobId,
                    status = StoryLogic.IMAGE_JOB_READY,
                )
            } catch (e: Throwable) {
                log.warn("Story character job {} failed: {}", jobId, e.message)
                characterJobs[jobId] = GenerateStoryCharacterResponse(
                    jobId = jobId,
                    status = StoryLogic.IMAGE_JOB_FAILED,
                    error = e.message ?: "Character generation failed",
                )
            }
        }
        return pending
    }

    fun characterJob(jobId: String): GenerateStoryCharacterResponse? = characterJobs[jobId]

    suspend fun generateCharacter(request: GenerateStoryCharacterRequest): StoryCharacter {
        val name = request.name.trim()
        require(name.isNotEmpty()) { "A character name is required" }
        val prompt = request.prompt.trim()
        require(prompt.isNotEmpty()) { "prompt is required" }
        val portraitPrompt = PromptFactory.forCharacterPortrait(
            name = name,
            role = "main",
            description = prompt,
        )
        val generated = qwen.generate(portraitPrompt, emptyList())
        val objectKey = "stories/characters/${UUID.randomUUID()}.${generated.extension}"
        val imageUrl = storage.upload(objectKey, generated)
        return service.addCharacter(name, imageUrl)
    }

    /** Tokenize in the background so the admin request can return immediately. */
    suspend fun enqueuePublish(id: String, languageTag: String): StoryPublishResponse {
        val started = service.beginPublish(id)
        val jobId = "pubjob-${jobIdGenerator()}"
        val pending = StoryPublishResponse(
            story = started,
            jobId = jobId,
            status = StoryLogic.PUBLISH_JOB_PENDING,
        )
        publishJobs[jobId] = pending
        scope.launch {
            try {
                val published = service.publish(id, languageTag)
                publishJobs[jobId] = StoryPublishResponse(
                    story = published,
                    jobId = jobId,
                    status = StoryLogic.PUBLISH_JOB_READY,
                )
            } catch (e: Throwable) {
                log.warn("Story publish job {} failed: {}", jobId, e.message)
                val failedStory = runCatching { service.failPublish(id) }.getOrNull()
                publishJobs[jobId] = StoryPublishResponse(
                    story = failedStory,
                    jobId = jobId,
                    status = StoryLogic.PUBLISH_JOB_FAILED,
                    error = e.message ?: "Publishing failed",
                )
            }
        }
        return pending
    }

    fun publishJob(jobId: String): StoryPublishResponse? = publishJobs[jobId]

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
        val generated = qwen.generate(PromptFactory.withArtDirection(prompt), refs)
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
            val studyCache = ArangoStoryStudyCache.connect(config.arango, httpClient)
                ?: InMemoryStoryStudyCache().also {
                    log.info(
                        "Story study cache using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }
            val service = StoryService(
                stories = storyRepo,
                characters = characterRepo,
                tokenizer = StoryService.tokenizerFrom(studyService),
                dissector = StoryService.dissectorFrom(studyService),
                studyCache = studyCache,
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
