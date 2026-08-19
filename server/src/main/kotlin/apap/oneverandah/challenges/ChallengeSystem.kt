package apap.oneverandah.challenges

import apap.oneverandah.challenges.arango.ArangoChallengeRepository
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns challenge persistence plus the upload → transcribe → review pipeline.
 */
class ChallengeSystem(
    val service: ChallengeService,
    val storage: StorageService,
    private val transcriber: ChallengeTranscriber,
    private val namer: ChallengeNamer,
    private val media: ChallengeMediaProcessor,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val jobIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(ChallengeSystem::class.java)
    private val jobs = ConcurrentHashMap<String, ChallengeUploadResponse>()

    suspend fun enqueueUpload(
        challengeId: String?,
        challengerName: String,
        deviceTokenHash: String,
        bytes: ByteArray,
        fileName: String?,
        contentType: String,
    ): ChallengeUploadResponse {
        require(bytes.isNotEmpty()) { "A video file is required" }
        val (challenge, submission) = service.beginUpload(challengeId, challengerName, deviceTokenHash)
        val jobId = "chaljob-${jobIdGenerator()}"
        val pending = ChallengeUploadResponse(
            jobId = jobId,
            status = ChallengeLogic.JOB_PENDING,
            submission = ChallengeLogic.toView(submission, challengeName = challenge.name, viewerHash = deviceTokenHash),
            challenge = ChallengeLogic.toCard(challenge, emptyList()),
            progress = 10,
        )
        jobs[jobId] = pending
        scope.launch {
            try {
                process(jobId, submission.id, deviceTokenHash, bytes, fileName, contentType)
            } catch (e: Throwable) {
                log.warn("Challenge job {} failed: {}", jobId, e.message)
                val failed = runCatching { service.failProcessing(submission.id, e.message ?: "Processing failed") }
                    .getOrNull()
                jobs[jobId] = ChallengeUploadResponse(
                    jobId = jobId,
                    status = ChallengeLogic.JOB_FAILED,
                    submission = failed?.let { ChallengeLogic.toView(it, viewerHash = deviceTokenHash) },
                    error = e.message ?: "Processing failed",
                    progress = 100,
                )
            }
        }
        return pending
    }

    fun job(jobId: String): ChallengeUploadResponse? = jobs[jobId]

    private suspend fun process(
        jobId: String,
        submissionId: String,
        deviceTokenHash: String,
        bytes: ByteArray,
        fileName: String?,
        contentType: String,
    ) {
        fun update(progress: Int, submission: ChallengeSubmission? = null, challengeName: String = "") {
            jobs[jobId] = ChallengeUploadResponse(
                jobId = jobId,
                status = ChallengeLogic.JOB_PENDING,
                submission = submission?.let { ChallengeLogic.toView(it, challengeName = challengeName, viewerHash = deviceTokenHash) },
                progress = progress,
            )
        }
        update(20)
        val prepared = media.prepare(bytes, fileName, contentType)
        update(45)
        val videoKey = "challenges/videos/${UUID.randomUUID()}.${prepared.videoExtension}"
        val videoUrl = storage.upload(
            videoKey,
            GeneratedImage(
                bytes = prepared.videoBytes,
                contentType = prepared.videoContentType,
                extension = prepared.videoExtension,
            ),
        )
        val thumbnailUrl = prepared.thumbnailBytes?.let { thumb ->
            storage.upload(
                "challenges/thumbs/${UUID.randomUUID()}.jpg",
                GeneratedImage(bytes = thumb, contentType = "image/jpeg", extension = "jpg"),
            )
        }
        val audioUrl = prepared.audioBytes?.let { audio ->
            storage.upload(
                "challenges/audio/${UUID.randomUUID()}.wav",
                GeneratedImage(bytes = audio, contentType = "audio/wav", extension = "wav"),
            )
        }
        val attached = service.attachMedia(submissionId, videoUrl, thumbnailUrl, prepared.durationMs)
        update(65, attached)
        val transcript = transcriber.transcribe(prepared.audioBytes, audioUrl, prepared.durationMs)
        update(85, attached)
        val generatedName = if (transcript.text.isNotBlank()) namer.nameFrom(transcript.text) else ""
        val completed = service.completeProcessing(submissionId, transcript, generatedName)
        val challenge = service.findChallenge(completed.challengeId)
        jobs[jobId] = ChallengeUploadResponse(
            jobId = jobId,
            status = ChallengeLogic.JOB_READY,
            submission = ChallengeLogic.toView(
                completed,
                challengeName = challenge?.name.orEmpty(),
                viewerHash = deviceTokenHash,
            ),
            challenge = challenge?.let { ChallengeLogic.toCard(it, emptyList()) },
            progress = 100,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChallengeSystem::class.java)

        suspend fun create(
            config: AppConfig,
            storage: StorageService,
            httpClient: HttpClient? = null,
        ): ChallengeSystem {
            val repo = ArangoChallengeRepository.connect(config.arango, httpClient)
                ?: InMemoryChallengeRepository().also {
                    log.info(
                        "Challenge repository using {}in-memory store",
                        if (config.arango.enabled) "fallback " else "",
                    )
                }
            val transcriber = if (config.qwenChat.enabled && httpClient != null) {
                QwenFileTranscriptionClient(config.qwenChat, httpClient)
            } else {
                NoopChallengeTranscriber
            }
            val namer = if (config.qwenChat.enabled && httpClient != null) {
                QwenChallengeNamer(config.qwenChat, httpClient)
            } else {
                NoopChallengeNamer
            }
            return ChallengeSystem(
                service = ChallengeService(repo),
                storage = storage,
                transcriber = transcriber,
                namer = namer,
                media = FfmpegChallengeMedia(),
            )
        }
    }
}
