package apap.oneverandah.components

import apap.oneverandah.api.chatApiUrl
import kotlinx.browser.window
import org.khronos.webgl.Int16Array
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import kotlin.js.JSON
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val targetSampleRate = 16_000
private const val webSocketOpen: Short = 1
private const val warmUpDelayMs = 150
private const val holdConfirmDelayMs = 350
private const val socketIdleTtlMs = 60_000
private const val maxBufferedPcmBytes = targetSampleRate * 2 * 3 // ~3s of PCM16 mono
private const val scriptProcessorBufferSize = 2048 // ~43ms at 48 kHz

internal enum class VoiceInputUiState {
    Idle,
    Connecting,
    Listening,
}

/**
 * UI state when a hold becomes active.
 *
 * Only show [VoiceInputUiState.Connecting] while the microphone permission / stream is still
 * outstanding. Once the mic is available, jump straight to listening even if the ASR socket is
 * still warming — audio is queued until the session is ready.
 */
internal fun voiceUiStateForStart(microphoneReady: Boolean): VoiceInputUiState =
    if (microphoneReady) VoiceInputUiState.Listening else VoiceInputUiState.Connecting

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

/**
 * Hold-to-speak gesture with a warm-up phase.
 *
 * - [warmUpDelayMs] (default 150ms): start background prep (mic/socket). Short clicks never reach this.
 * - [startDelayMs] (default 350ms): enter listening / send audio.
 */
internal class HoldToSpeakGesture(
    private val schedule: (delayMs: Int, action: () -> Unit) -> Int,
    private val cancelScheduled: (Int) -> Unit,
    private val onWarmUp: () -> Unit,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    /** Called when the press ends after warm-up but before the hold was confirmed. */
    private val onWarmUpCancel: () -> Unit = {},
    private val warmUpDelayMs: Int = apap.oneverandah.components.warmUpDelayMs,
    private val startDelayMs: Int = holdConfirmDelayMs,
) {
    private var warmUpId: Int? = null
    private var startId: Int? = null
    private var warmed = false
    private var started = false

    fun press() {
        if (warmUpId != null || startId != null || started) return
        warmUpId = schedule(warmUpDelayMs) {
            warmUpId = null
            warmed = true
            onWarmUp()
        }
        startId = schedule(startDelayMs) {
            startId = null
            started = true
            onStart()
        }
    }

    fun release() {
        val wasStarted = started
        val wasWarmed = warmed
        cancelPending()
        started = false
        warmed = false
        when {
            wasStarted -> onStop()
            wasWarmed -> onWarmUpCancel()
        }
    }

    fun cancel() = release()

    private fun cancelPending() {
        warmUpId?.let(cancelScheduled)
        startId?.let(cancelScheduled)
        warmUpId = null
        startId = null
    }
}

/**
 * Long-lived browser ASR session.
 *
 * Warm-up opens mic + AudioWorklet + WebSocket without showing listening UI.
 * Turns can reuse the connection for [socketIdleTtlMs] after release.
 * PCM is sent as raw binary frames; the server wraps them for Qwen.
 */
internal class BrowserVoiceInput(
    private var language: String,
    private val onValueChange: (String) -> Unit,
    private val onStateChange: (VoiceInputUiState) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var transcript: VoiceTranscript? = null
    private var socket: WebSocket? = null
    private var mediaStream: dynamic = null
    private var audioContext: dynamic = null
    private var audioSource: dynamic = null
    private var captureNode: dynamic = null
    private var muteGain: dynamic = null
    private var workletLoaded = false
    private var micRequestInFlight = false
    /** Cached Permissions API / prior getUserMedia success — avoids Connecting flash. */
    private var micPermissionGranted = false
    private var listening = false
    /** True while we should capture PCM (warm-up pre-roll or active listen). */
    private var capturing = false
    private var sessionReady = false
    private var sessionLanguage: String? = null
    private var disposed = false
    private var idleTimer: Int? = null
    private var pendingPcm = js("[]")
    private var pendingBytes = 0
    private var openedAtMs = 0.0
    private var firstAudioSent = false

    fun warmUp(language: String = this.language) {
        if (disposed) return
        this.language = language
        clearIdleTimer()
        // Start mic + capture graph immediately so the first hold can queue audio
        // before the websocket handshake finishes.
        capturing = true
        probeMicrophonePermission()
        ensureMicrophone()
        ensureSocket()
    }

    fun beginListening(initialValue: String, language: String = this.language) {
        if (disposed) return
        this.language = language
        transcript = VoiceTranscript(initialValue)
        listening = true
        capturing = true
        firstAudioSent = false
        clearIdleTimer()
        // Never flash "Connecting..." once the mic is already available — queue audio
        // and open the socket in the background.
        probeMicrophonePermission()
        onStateChange(voiceUiStateForStart(microphoneReady = isMicrophoneReady()))
        ensureMicrophone()
        ensureSocket()
        flushPendingIfReady()
    }

    fun endListening() {
        if (disposed) return
        if (!listening && !capturing) {
            scheduleIdleClose()
            return
        }
        // Hard-cancel: drop the turn, discard queued audio, ignore any further ASR text.
        cancelActiveTurn(closeSession = false)
        onStateChange(VoiceInputUiState.Idle)
        scheduleIdleClose()
    }

    /** Keep mic/socket warm after an aborted warm-up (hold released before confirm). */
    fun park() {
        if (disposed || listening) return
        capturing = false
        clearPendingPcm()
        scheduleIdleClose()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        cancelActiveTurn(closeSession = true)
        clearIdleTimer()
        stopAudioGraph(stopTracks = true)
        finishAndCloseSocket()
        onStateChange(VoiceInputUiState.Idle)
    }

    private fun isMicrophoneReady(): Boolean = mediaStream != null || micPermissionGranted

    private fun probeMicrophonePermission() {
        if (micPermissionGranted || mediaStream != null) return
        val permissions = window.navigator.asDynamic().permissions ?: return
        runCatching {
            permissions.query(js("{name:'microphone'}")).then(
                { status: dynamic ->
                    val granted = (status.state as? String) == "granted"
                    if (granted) {
                        micPermissionGranted = true
                        if (listening) onStateChange(VoiceInputUiState.Listening)
                    }
                    status.onchange = {
                        micPermissionGranted = (status.state as? String) == "granted"
                    }
                    null
                },
                { null },
            )
        }
    }

    private fun cancelActiveTurn(closeSession: Boolean) {
        listening = false
        capturing = false
        transcript = null
        clearPendingPcm()
        firstAudioSent = false
        if (closeSession) return
        // Drop any audio already sitting in the provider buffer without requesting a final transcript.
        val activeSocket = socket
        if (activeSocket != null && activeSocket.readyState == webSocketOpen && sessionReady) {
            runCatching { activeSocket.send("""{"type":"input_audio_buffer.clear"}""") }
        }
    }

    private fun clearPendingPcm() {
        pendingPcm = js("[]")
        pendingBytes = 0
    }

    private fun ensureMicrophone() {
        if (disposed || mediaStream != null || micRequestInFlight) {
            if (mediaStream != null) ensureAudioGraph(mediaStream)
            return
        }
        val mediaDevices = window.navigator.asDynamic().mediaDevices
        if (mediaDevices == null) {
            fail("Microphone access is not supported by this browser")
            return
        }
        micRequestInFlight = true
        val constraints = js("({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true},video:false})")
        mediaDevices.getUserMedia(constraints).then(
            { stream: dynamic ->
                micRequestInFlight = false
                if (disposed) {
                    stopTracks(stream)
                    return@then null
                }
                mediaStream = stream
                micPermissionGranted = true
                ensureAudioGraph(stream)
                // Mic permission resolved — leave Connecting immediately if still holding.
                if (listening) onStateChange(VoiceInputUiState.Listening)
                null
            },
            { cause: dynamic ->
                micRequestInFlight = false
                fail(cause?.message?.toString() ?: "Microphone permission was denied")
                null
            },
        )
    }

    private fun ensureSocket() {
        if (disposed) return
        val existing = socket
        if (existing != null && existing.readyState < 2) {
            if (existing.readyState == webSocketOpen && sessionLanguage != language) {
                existing.send(sessionUpdateEvent(language))
                sessionLanguage = language
                sessionReady = false
                // Language switch re-hands the session; keep Listening if mic is already up.
                if (listening) onStateChange(voiceUiStateForStart(microphoneReady = isMicrophoneReady()))
            }
            return
        }
        sessionReady = false
        openedAtMs = window.performance.now()
        val activeSocket = WebSocket(chatAsrWebSocketUrl(language))
        // Binary PCM frames are sent as ArrayBuffer.
        activeSocket.asDynamic().binaryType = "arraybuffer"
        socket = activeSocket
        activeSocket.onopen = {
            console.info("[asr] browser socket open in ${msSince(openedAtMs)}ms")
            activeSocket.send(sessionUpdateEvent(language))
            sessionLanguage = language
        }
        activeSocket.onmessage = { event: MessageEvent ->
            val raw = event.data
            if (raw is String) {
                val data = runCatching { JSON.parse<dynamic>(raw) }.getOrNull()
                if (data != null) {
                    when (data.type as? String) {
                        "session.updated" -> {
                            sessionReady = true
                            console.info("[asr] session ready in ${msSince(openedAtMs)}ms")
                            if (listening) onStateChange(VoiceInputUiState.Listening)
                            // Flush whatever was queued while the socket/session warmed up.
                            flushPendingIfReady()
                        }
                        "conversation.item.input_audio_transcription.text" -> {
                            // Release cancels the turn: ignore late partials/finals.
                            if (listening) {
                                transcript?.let {
                                    onValueChange(
                                        it.partial(
                                            data.text as? String ?: "",
                                            data.stash as? String ?: "",
                                        ),
                                    )
                                }
                            }
                        }
                        "conversation.item.input_audio_transcription.delta" -> {
                            if (listening) {
                                transcript?.let {
                                    onValueChange(it.partial(data.delta as? String ?: "", ""))
                                }
                            }
                        }
                        "conversation.item.input_audio_transcription.completed" -> {
                            if (listening) {
                                transcript?.let {
                                    onValueChange(it.completed(data.transcript as? String ?: ""))
                                }
                            }
                        }
                        "session.finished" -> {
                            sessionReady = false
                            closeSocketOnly()
                        }
                        "error" -> {
                            if (listening) fail(data.error?.message?.toString() ?: "Voice recognition failed")
                        }
                    }
                }
            }
        }
        activeSocket.onerror = {
            if (!disposed && listening) fail("Voice recognition connection failed")
        }
        activeSocket.onclose = {
            if (socket === activeSocket) {
                socket = null
                sessionReady = false
                sessionLanguage = null
                clearPendingPcm()
                if (listening) {
                    listening = false
                    capturing = false
                    transcript = null
                    onStateChange(VoiceInputUiState.Idle)
                }
            }
        }
    }

    private fun ensureAudioGraph(stream: dynamic) {
        if (disposed) return
        val existing = audioContext
        if (existing != null) {
            runCatching { existing.resume() }
            return
        }
        val context = js("new (window.AudioContext || window.webkitAudioContext)()")
        audioContext = context
        runCatching { context.resume() }
        val source = context.createMediaStreamSource(stream)
        audioSource = source
        val gain = context.createGain()
        gain.gain.value = 0
        muteGain = gain
        gain.connect(context.destination)

        val useWorklet = context.audioWorklet != null && js("typeof AudioWorkletNode !== 'undefined'") as Boolean
        if (useWorklet) {
            installWorkletCapture(context, source, gain)
        } else {
            installScriptProcessorCapture(context, source, gain)
        }
    }

    private fun installWorkletCapture(context: dynamic, source: dynamic, gain: dynamic) {
        if (workletLoaded || captureNode != null) return
        val sourceCode = asrWorkletSource
        val blob = js("new Blob([sourceCode], {type:'application/javascript'})")
        val url = js("URL.createObjectURL(blob)") as String
        context.audioWorklet.addModule(url).then(
            {
                runCatching { js("URL.revokeObjectURL(url)") }
                if (disposed || audioContext !== context) return@then null
                workletLoaded = true
                val node = js("new AudioWorkletNode(context, 'asr-pcm-processor')")
                captureNode = node
                node.port.onmessage = { event: dynamic ->
                    onPcmFrame(event.data)
                }
                source.connect(node)
                node.connect(gain)
                null
            },
            { cause: dynamic ->
                runCatching { js("URL.revokeObjectURL(url)") }
                console.warn("AudioWorklet unavailable, falling back to ScriptProcessor", cause)
                installScriptProcessorCapture(context, source, gain)
                null
            },
        )
    }

    private fun installScriptProcessorCapture(context: dynamic, source: dynamic, gain: dynamic) {
        if (captureNode != null) return
        val processor = context.createScriptProcessor(scriptProcessorBufferSize, 1, 1)
        captureNode = processor
        processor.onaudioprocess = { event: dynamic ->
            if (capturing || listening) {
                val samples = event.inputBuffer.getChannelData(0)
                val pcm = resamplePcm16(samples, (context.sampleRate as Number).toInt())
                onPcmFrame(pcm.buffer)
            }
        }
        source.connect(processor)
        processor.connect(gain)
    }

    private fun onPcmFrame(buffer: dynamic) {
        if (disposed) return
        // Queue during warm-up pre-roll and active listening; drop once cancelled.
        if (!capturing && !listening) return
        val bytes = (buffer.byteLength as? Number)?.toInt() ?: return
        if (bytes <= 0) return
        val activeSocket = socket
        // Only stream live audio while the user is still holding. Warm-up pre-roll stays
        // buffered until beginListening + session ready flush it.
        if (listening && sessionReady && activeSocket?.readyState == webSocketOpen) {
            sendBinary(activeSocket, buffer)
        } else {
            bufferPcm(buffer, bytes)
        }
    }

    private fun bufferPcm(buffer: dynamic, bytes: Int) {
        while (pendingBytes + bytes > maxBufferedPcmBytes && (pendingPcm.length as Int) > 0) {
            val dropped = pendingPcm.shift()
            pendingBytes -= (dropped.byteLength as Number).toInt()
        }
        // Copy so the worklet can reuse its transfer buffer.
        val copy = js("buffer.slice(0)")
        pendingPcm.push(copy)
        pendingBytes += bytes
    }

    private fun flushPendingIfReady() {
        if (!listening) return
        val activeSocket = socket
        if (!sessionReady || activeSocket?.readyState != webSocketOpen) return
        val count = pendingPcm.length as Int
        for (index in 0 until count) {
            sendBinary(activeSocket, pendingPcm[index])
        }
        clearPendingPcm()
    }

    private fun sendBinary(activeSocket: WebSocket, buffer: dynamic) {
        if (!listening) return
        if (!firstAudioSent) {
            firstAudioSent = true
            console.info("[asr] first audio sent at ${msSince(openedAtMs)}ms")
        }
        activeSocket.asDynamic().send(buffer)
    }

    private fun stopAudioGraph(stopTracks: Boolean) {
        val node = captureNode
        if (node != null) runCatching { node.disconnect() }
        val source = audioSource
        if (source != null) runCatching { source.disconnect() }
        val gain = muteGain
        if (gain != null) runCatching { gain.disconnect() }
        val context = audioContext
        if (context != null) runCatching { context.close() }
        if (stopTracks) {
            val stream = mediaStream
            if (stream != null) runCatching { stopTracks(stream) }
            mediaStream = null
        }
        captureNode = null
        audioSource = null
        muteGain = null
        audioContext = null
        workletLoaded = false
    }

    private fun scheduleIdleClose() {
        clearIdleTimer()
        idleTimer = window.setTimeout({
            idleTimer = null
            if (disposed || listening || capturing) return@setTimeout
            // Keep the socket warm for a minute, then fully tear down.
            finishAndCloseSocket()
            stopAudioGraph(stopTracks = true)
        }, socketIdleTtlMs)
    }

    private fun clearIdleTimer() {
        idleTimer?.let(window::clearTimeout)
        idleTimer = null
    }

    private fun finishAndCloseSocket() {
        val activeSocket = socket
        if (activeSocket != null && activeSocket.readyState == webSocketOpen) {
            runCatching { activeSocket.send("""{"type":"session.finish"}""") }
        }
        closeSocketOnly()
    }

    private fun closeSocketOnly() {
        val activeSocket = socket
        socket = null
        sessionReady = false
        sessionLanguage = null
        clearPendingPcm()
        if (activeSocket != null && activeSocket.readyState < 2) {
            runCatching { activeSocket.close() }
        }
    }

    private fun fail(message: String) {
        if (disposed) return
        cancelActiveTurn(closeSession = true)
        clearIdleTimer()
        stopAudioGraph(stopTracks = true)
        closeSocketOnly()
        onStateChange(VoiceInputUiState.Idle)
        onError(message)
    }

    private fun msSince(start: Double): Int =
        max(0, (window.performance.now() - start).toInt())
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

/**
 * Average-downsample float32 samples to 16 kHz PCM16. Used by the ScriptProcessor fallback.
 */
internal fun resamplePcm16(samples: dynamic, inputSampleRate: Int): Int16Array {
    val ratio = inputSampleRate.toDouble() / targetSampleRate
    val sampleCount = samples.length as Int
    val outputSize = max(1, floor(sampleCount / ratio).toInt())
    val output = Int16Array(outputSize)
    for (index in 0 until outputSize) {
        val start = floor(index * ratio).toInt()
        val end = min(sampleCount, max(start + 1, ceil((index + 1) * ratio).toInt()))
        var sum = 0.0
        for (sampleIndex in start until end) sum += (samples[sampleIndex] as Number).toDouble()
        val sample = (sum / (end - start)).coerceIn(-1.0, 1.0)
        output.asDynamic()[index] = if (sample < 0) (sample * 32768).toInt() else (sample * 32767).toInt()
    }
    return output
}

internal fun sessionUpdateEvent(language: String): String =
    """{"type":"session.update","session":{"modalities":["text"],"input_audio_format":"pcm","sample_rate":16000,"input_audio_transcription":{"language":"$language"},"turn_detection":null}}"""

internal val sessionUpdateEvent: String get() = sessionUpdateEvent("en")

// language=JavaScript
private const val asrWorkletSource: String = """
class AsrPcmProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.ratio = sampleRate / 16000;
    this.frameTarget = 640; // 40ms at 16 kHz
    this.out = new Int16Array(this.frameTarget);
    this.outIndex = 0;
    this.readOffset = 0;
  }
  process(inputs) {
    const input = inputs[0];
    if (!input || !input[0]) return true;
    const channel = input[0];
    while (this.readOffset + this.ratio <= channel.length) {
      const start = this.readOffset | 0;
      const end = Math.min(channel.length, Math.ceil(this.readOffset + this.ratio));
      let sum = 0;
      for (let i = start; i < end; i++) sum += channel[i];
      const sample = sum / Math.max(1, end - start);
      const clamped = sample < -1 ? -1 : sample > 1 ? 1 : sample;
      this.out[this.outIndex++] = clamped < 0 ? (clamped * 0x8000) : (clamped * 0x7FFF);
      if (this.outIndex >= this.frameTarget) {
        const copy = this.out.slice(0);
        this.port.postMessage(copy.buffer, [copy.buffer]);
        this.out = new Int16Array(this.frameTarget);
        this.outIndex = 0;
      }
      this.readOffset += this.ratio;
    }
    this.readOffset -= channel.length;
    if (this.readOffset < 0) this.readOffset = 0;
    return true;
  }
}
registerProcessor('asr-pcm-processor', AsrPcmProcessor);
"""
