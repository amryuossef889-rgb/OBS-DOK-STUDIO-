package com.dokstudio.obs.recording

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import com.dokstudio.obs.capture.AudioCaptureManager
import com.dokstudio.obs.capture.CameraCaptureManager
import com.dokstudio.obs.capture.ScreenCaptureManager
import com.dokstudio.obs.compositor.SceneCompositor

class RecordingController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private var recorder: MediaCodecRecorder? = null
    private var compositor: SceneCompositor? = null
    private var screen: ScreenCaptureManager? = null
    private var camera: CameraCaptureManager? = null
    private var audio: AudioCaptureManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var drainLoop: Runnable? = null
    @Volatile private var previewSurface: Surface? = null

    fun setPreviewSurface(surface: Surface?) {
        previewSurface = surface
        compositor?.setPreviewSurface(surface)
    }

    fun start(
        code: Int,
        data: Intent,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        includeCamera: Boolean,
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
                MediaCodecRecorder.Config(width = width, height = height, fps = fps, bitrate = bitrate),
            )

            val gpuCompositor = SceneCompositor()
            gpuCompositor.initialize(encoderSurface)
            gpuCompositor.setPreviewSurface(previewSurface)

            val screenInput = gpuCompositor.createInputSurface()
            gpuCompositor.updateLayer(screenInput, z = 0)

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

            val cameraCapture = if (includeCamera) {
                val manager = CameraCaptureManager(context, lifecycleOwner)
                val cameraInput = gpuCompositor.createInputSurface()
                gpuCompositor.updateLayer(
                    cameraInput,
                    z = 1,
                    scaleX = 0.32f,
                    scaleY = 0.32f,
                    translateX = 0.62f,
                    translateY = -0.62f,
                )
                manager.start(
                    target = cameraInput.surface,
                    width = width,
                    height = height,
                    onError = onError,
                )
                manager
            } else {
                null
            }

            recorder = encoder
            compositor = gpuCompositor
            screen = screenCapture
            camera = cameraCapture
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
        camera?.stop()
        camera = null
        audio?.stop()
        audio = null
        compositor?.release()
        compositor = null
        recorder?.let { runCatching { it.stop() } }
        recorder = null
    }
}
