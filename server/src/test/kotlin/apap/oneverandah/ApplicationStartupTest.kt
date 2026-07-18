package apap.oneverandah

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ApplicationStartupTest {
    @Test
    fun `chat startup does not block application initialization`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val startupStarted = CompletableDeferred<Unit>()
        val allowStartupToComplete = CompletableDeferred<Unit>()
        val releaseStartup = scope.launch {
            startupStarted.await()
            delay(1_000)
            allowStartupToComplete.complete(Unit)
        }

        lateinit var startupJob: kotlinx.coroutines.Job
        val elapsedMillis = measureTimeMillis {
            startupJob = scope.launchChatStartup {
                startupStarted.complete(Unit)
                allowStartupToComplete.await()
            }
        }

        try {
            assertTrue(elapsedMillis < 500, "Scheduling startup took ${elapsedMillis}ms")
            assertTrue(startupStarted.isCompleted)
            assertFalse(startupJob.isCompleted)
            allowStartupToComplete.complete(Unit)
            joinAll(startupJob, releaseStartup)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `chat startup is canceled with application scope`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val startupStarted = CompletableDeferred<Unit>()
        val startupJob = scope.launchChatStartup {
            startupStarted.complete(Unit)
            awaitCancellation()
        }
        startupStarted.await()

        scope.cancel()
        startupJob.join()

        assertTrue(startupJob.isCancelled)
    }

    @Test
    fun `chat startup failure does not cancel application scope`() = runBlocking {
        val parentJob = SupervisorJob()
        val scope = CoroutineScope(parentJob + Dispatchers.Default)

        val startupJob = scope.launchChatStartup { error("Voice design unavailable") }
        startupJob.join()

        try {
            assertTrue(startupJob.isCompleted)
            assertFalse(startupJob.isCancelled)
            assertTrue(parentJob.isActive)
        } finally {
            scope.cancel()
        }
    }
}