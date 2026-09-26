package com.dokstudio.obs.recording

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.dokstudio.obs.capture.AudioCaptureManager
import com.dokstudio.obs.capture.ScreenCaptureManager
import com.dokstudio.obs.compositor.SceneCompositor

class RecordingController(private val context: Context) {
    private var recorder: MediaCodecRecorder? = null
    private var compositor: SceneCompositor? = null
    private var screen: ScreenCaptureManager? = null
    private var audio: AudioCaptureManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var drainLoop: Runnable? = null

    fun start(
        code: Int,
        data: Intent,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        onStarted: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (recorder != null) {
            onError(IllegalStateException("Recording is already active"))
            return
        }

        try {
            val encoder = MediaCodecRecorder(context)
            val encoderSurface = encoder.start(
                MediaCodecRecorder.Config(
                    width = width,
                    height = height,
                    fps = fps,
                    bitrate = bitrate,
                ),
            )

            val gpuCompositor = SceneCompositor()
            gpuCompositor.initialize(encoderSurface)
            val screenInput = gpuCompositor.createInputSurface()

            val screenCapture = ScreenCaptureManager(context)
            screenCapture.startDirect(
                code = code,
                data = data,
                surface = screenInput.surface,
                width = width,
                height = height,
                dpi = context.resources.displayMetrics.densityDpi,
                onStopped = {
                    onError(IllegalStateException("MediaProjection session ended"))
                },
            )

            val microphone = AudioCaptureManager()
            microphone.start(
                onPcm = { pcm, ptsUs -> encoder.queueAudio(pcm, ptsUs) },
                onLevel = {},
                onError = onError,
            )

            recorder = encoder
            compositor = gpuCompositor
            screen = screenCapture
            audio = microphone

            drainLoop = object : Runnable {
                override fun run() {
                    try {
                        encoder.drainVideo()
                        handler.postDelayed(this, 10L)
                    } catch (t: Throwable) {
                        onError(t)
                        stop()
                    }
                }
            }.also(handler::post)

            onStarted()
        } catch (t: Throwable) {
            stop()
            onError(t)
        }
    }

    fun stop() {
        drainLoop?.let(handler::removeCallbacks)
        drainLoop = null

        screen?.stop()
        screen = null

        audio?.stop()
        audio = null

        compositor?.release()
        compositor = null

        recorder?.let { runCatching { it.stop() } }
        recorder = null
    }
}
