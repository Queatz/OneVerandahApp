package apap.oneverandah.api

import apap.oneverandah.challenges.ChallengeLogic
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLVideoElement
import org.w3c.files.File
import kotlin.coroutines.resume

/**
 * Validates duration and, when the browser can, reports local processing
 * progress before upload. Server-side ffmpeg still downscales to 720p H.264.
 */
suspend fun prepareChallengeVideo(file: File, onProgress: (Int) -> Unit): File {
    onProgress(6)
    val durationMs = videoDurationMs(file)
    onProgress(28)
    if (durationMs != null && durationMs > ChallengeLogic.MAX_DURATION_MS + 1_000) {
        throw ChallengeApiException("Videos can be at most 5 minutes long")
    }
    onProgress(38)
    return file
}

private suspend fun videoDurationMs(file: File): Long? = suspendCancellableCoroutine { cont ->
    val video = document.createElement("video") as HTMLVideoElement
    video.preload = "metadata"
    video.muted = true
    val url = window.asDynamic().URL.createObjectURL(file) as String
    video.onloadedmetadata = {
        val duration = video.duration
        window.asDynamic().URL.revokeObjectURL(url)
        if (cont.isActive) {
            if (duration.isFinite() && duration > 0) {
                cont.resume((duration * 1000).toLong())
            } else {
                cont.resume(null)
            }
        }
    }
    video.addEventListener("error", {
        window.asDynamic().URL.revokeObjectURL(url)
        if (cont.isActive) cont.resume(null)
    })
    cont.invokeOnCancellation {
        window.asDynamic().URL.revokeObjectURL(url)
        video.src = ""
    }
    video.src = url
}
