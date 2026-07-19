package apap.oneverandah.components

import apap.oneverandah.api.chatApiUrl
import kotlinx.browser.window
import org.w3c.dom.WebSocket
import kotlin.js.JSON
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val targetSampleRate = 16_000
private const val webSocketOpen: Short = 1

internal class VoiceTranscript(private val initialValue: String) {
    fun partial(text: String, stash: String): String = merge(text + stash)

    fun completed(transcript: String): String = merge(transcript)

    private fun merge(recognized: String): String {
        val prefix = initialValue.trimEnd()
        val speech = recognized.trimStart()
        return when {
            prefix.isEmpty() -> speech
            speech.isEmpty() -> prefix
            else -> "$prefix $speech"
        }
    }
}

internal class HoldToSpeakGesture(
    private val schedule: (action: () -> Unit) -> Int,
    private val cancelScheduled: (Int) -> Unit,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
) {
    private var scheduledId: Int? = null
    private var started = false

    fun press() {
        if (scheduledId != null || started) return
        scheduledId = schedule {
            scheduledId = null
            started = true
            onStart()
        }
    }

    fun release() {
        cancelPending()
        if (started) {
            started = false
            onStop()
        }
    }

    fun cancel() = release()

    private fun cancelPending() {
        scheduledId?.let(cancelScheduled)
        scheduledId = null
    }
}

internal class BrowserVoiceInput(
    initialValue: String,
    private val language: String,
    private val onValueChange: (String) -> Unit,
    private val onListeningChange: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val transcript = VoiceTranscript(initialValue)
    private var socket: WebSocket? = null
    private var mediaStream: dynamic = null
    private var audioContext: dynamic = null
    private var audioSource: dynamic = null
    private var audioProcessor: dynamic = null
    private var stopRequested = false
    private var sessionReady = false
    private var closeTimer: Int? = null

    fun start() {
        stopRequested = false
        sessionReady = false
        onListeningChange(true)
        val mediaDevices = window.navigator.asDynamic().mediaDevices
        if (mediaDevices == null) {
            fail("Microphone access is not supported by this browser")
            return
        }
        val constraints = js("({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true},video:false})")
        mediaDevices.getUserMedia(constraints).then(
            { stream: dynamic ->
                if (stopRequested) {
                    stopTracks(stream)
                } else {
                    mediaStream = stream
                    openSocket()
                    startAudio(stream)
                }
                null
            },
            { cause: dynamic ->
                fail(cause?.message?.toString() ?: "Microphone permission was denied")
                null
            },
        )
    }

    fun stop() {
        stopRequested = true
        stopAudio()
        onListeningChange(false)
        val activeSocket = socket
        if (activeSocket != null && activeSocket.readyState < 2) {
            if (activeSocket.readyState == webSocketOpen && sessionReady) {
                finishSession(activeSocket)
            } else {
                scheduleSocketClose()
            }
        }
    }

    fun dispose() {
        stopRequested = true
        stopAudio()
        closeSocket()
        onListeningChange(false)
    }

    private fun openSocket() {
        val activeSocket = WebSocket(chatAsrWebSocketUrl(language))
        socket = activeSocket
        activeSocket.onopen = {
            activeSocket.send(sessionUpdateEvent(language))
        }
        activeSocket.onmessage = { event ->
            val data = runCatching { JSON.parse<dynamic>(event.asDynamic().data as String) }.getOrNull()
            if (data != null) {
                when (data.type as? String) {
                    "session.updated" -> {
                        sessionReady = true
                        if (stopRequested) finishSession(activeSocket)
                    }
                    "conversation.item.input_audio_transcription.text" -> {
                        onValueChange(transcript.partial(data.text as? String ?: "", data.stash as? String ?: ""))
                    }
                    "conversation.item.input_audio_transcription.delta" -> {
                        onValueChange(transcript.partial(data.delta as? String ?: "", ""))
                    }
                    "conversation.item.input_audio_transcription.completed" -> {
                        onValueChange(transcript.completed(data.transcript as? String ?: ""))
                    }
                    "session.finished" -> closeSocket()
                    "error" -> fail(data.error?.message?.toString() ?: "Voice recognition failed")
                }
            }
        }
        activeSocket.onerror = { fail("Voice recognition connection failed") }
        activeSocket.onclose = {
            socket = null
            stopAudio()
            onListeningChange(false)
        }
    }

    private fun startAudio(stream: dynamic) {
        val context = js("new (window.AudioContext || window.webkitAudioContext)()")
        audioContext = context
        context.resume()
        val source = context.createMediaStreamSource(stream)
        val processor = context.createScriptProcessor(4096, 1, 1)
        audioSource = source
        audioProcessor = processor
        processor.onaudioprocess = { event: dynamic ->
            val activeSocket = socket
            if (!stopRequested && sessionReady && activeSocket?.readyState == webSocketOpen) {
                val samples = event.inputBuffer.getChannelData(0)
                val audio = pcm16Base64(samples, (context.sampleRate as Number).toInt())
                activeSocket.send("""{"type":"input_audio_buffer.append","audio":"$audio"}""")
            }
        }
        source.connect(processor)
        processor.connect(context.destination)
    }

    private fun stopAudio() {
        val processor = audioProcessor
        if (processor != null) {
            runCatching { processor.disconnect() }
        }
        val source = audioSource
        if (source != null) {
            runCatching { source.disconnect() }
        }
        val context = audioContext
        if (context != null) {
            runCatching { context.close() }
        }
        val stream = mediaStream
        if (stream != null) {
            runCatching { stopTracks(stream) }
        }
        audioProcessor = null
        audioSource = null
        audioContext = null
        mediaStream = null
    }

    private fun fail(message: String) {
        stopRequested = true
        stopAudio()
        closeSocket()
        onListeningChange(false)
        onError(message)
    }

    private fun closeSocket() {
        closeTimer?.let(window::clearTimeout)
        closeTimer = null
        val activeSocket = socket
        socket = null
        if (activeSocket != null && activeSocket.readyState < 2) runCatching { activeSocket.close() }
    }

    private fun finishSession(activeSocket: WebSocket) {
        sessionReady = false
        activeSocket.send("""{"type":"input_audio_buffer.commit"}""")
        activeSocket.send("""{"type":"session.finish"}""")
        scheduleSocketClose()
    }

    private fun scheduleSocketClose() {
        closeTimer?.let(window::clearTimeout)
        closeTimer = window.setTimeout({ closeSocket() }, 5_000)
    }
}

private fun stopTracks(stream: dynamic) {
    val tracks = stream.getTracks()
    for (index in 0 until (tracks.length as Int)) tracks[index].stop()
}

private fun chatAsrWebSocketUrl(language: String): String {
    val url = chatApiUrl("/asr?language=$language")
    return when {
        url.startsWith("https://") -> "wss://${url.removePrefix("https://")}" 
        url.startsWith("http://") -> "ws://${url.removePrefix("http://")}" 
        else -> "${if (window.location.protocol == "https:") "wss" else "ws"}://${window.location.host}$url"
    }
}

private fun pcm16Base64(samples: dynamic, inputSampleRate: Int): String {
    val pcm = resamplePcm16(samples, inputSampleRate)
    val bytes = js("new Uint8Array(pcm.buffer)")
    val byteCount = bytes.length as Int
    val binary = buildString(byteCount) {
        for (index in 0 until byteCount) append((bytes[index] as Number).toInt().toChar())
    }
    return window.btoa(binary)
}

private fun resamplePcm16(samples: dynamic, inputSampleRate: Int): dynamic {
    val ratio = inputSampleRate.toDouble() / targetSampleRate
    val sampleCount = samples.length as Int
    val outputSize = max(1, floor(sampleCount / ratio).toInt())
    val output = js("new Int16Array(outputSize)")
    for (index in 0 until outputSize) {
        val start = floor(index * ratio).toInt()
        val end = min(sampleCount, max(start + 1, ceil((index + 1) * ratio).toInt()))
        var sum = 0.0
        for (sampleIndex in start until end) sum += (samples[sampleIndex] as Number).toDouble()
        val sample = (sum / (end - start)).coerceIn(-1.0, 1.0)
        output[index] = if (sample < 0) (sample * 32768).toInt() else (sample * 32767).toInt()
    }
    return output
}

internal fun sessionUpdateEvent(language: String): String =
    """{"type":"session.update","session":{"modalities":["text"],"input_audio_format":"pcm","sample_rate":16000,"input_audio_transcription":{"language":"$language"},"turn_detection":null}}"""

internal val sessionUpdateEvent: String get() = sessionUpdateEvent("en")