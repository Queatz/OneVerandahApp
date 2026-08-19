package apap.oneverandah.challenges

import apap.oneverandah.images.storage.GeneratedImage
import apap.oneverandah.images.storage.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChallengeSystemTest {
    @Test
    fun uploadPipelineStoresVideoTranscribesAndNames() = runBlocking {
        val stored = mutableListOf<String>()
        val system = ChallengeSystem(
            service = ChallengeService(InMemoryChallengeRepository()),
            storage = object : StorageService {
                override val kind: String = "fake"
                override suspend fun upload(objectKey: String, image: GeneratedImage): String {
                    stored += objectKey
                    return "https://cdn.test/$objectKey"
                }
            },
            transcriber = ChallengeTranscriber { audio, _, _ ->
                assertTrue(audio != null && audio.isNotEmpty())
                ChallengeTranscript("I swam twenty laps", listOf(ChallengeWord("I", 0, 200)))
            },
            namer = ChallengeNamer { transcript ->
                assertTrue(transcript.contains("swam"))
                "Pool laps"
            },
            media = ChallengeMediaProcessor { bytes, _, _ ->
                PreparedChallengeMedia(
                    videoBytes = bytes,
                    videoContentType = "video/mp4",
                    videoExtension = "mp4",
                    thumbnailBytes = byteArrayOf(1, 2, 3),
                    audioBytes = byteArrayOf(4, 5, 6),
                    durationMs = 12_000,
                )
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            jobIdGenerator = { "fixed" },
        )

        val pending = system.enqueueUpload(
            challengeId = null,
            challengerName = "Lan",
            deviceTokenHash = "hash-lan",
            bytes = byteArrayOf(9, 9, 9),
            fileName = "clip.mp4",
            contentType = "video/mp4",
        )
        assertEquals(ChallengeLogic.JOB_PENDING, pending.status)

        var ready: ChallengeUploadResponse? = null
        repeat(30) {
            val job = system.job("chaljob-fixed")
            if (job?.status == ChallengeLogic.JOB_READY) {
                ready = job
                return@repeat
            }
            delay(10)
        }
        val finished = ready ?: error("upload job did not finish")
        assertEquals(ChallengeLogic.STATUS_IN_REVIEW, finished.submission?.status)
        assertEquals("Pool laps", finished.submission?.challengeName)
        assertEquals("Pool laps", finished.challenge?.name)
        assertTrue(stored.any { it.startsWith("challenges/videos/") })
        assertTrue(stored.any { it.startsWith("challenges/thumbs/") })
        assertTrue(system.service.feed(null).challenges.isEmpty())
        assertEquals(1, system.service.feed("hash-lan").inReview.size)
    }
}
