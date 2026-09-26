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
import com.dokstudio.obs.data.SourceEntity
import com.dokstudio.obs.compositor.SceneCompositor
import com.dokstudio.obs.mixer.AudioMixer

class RecordingController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private var recorder: MediaCodecRecorder? = null
    private var compositor: SceneCompositor? = null
    private var screen: ScreenCaptureManager? = null
    private var camera: CameraCaptureManager? = null
    private var audio: AudioCaptureManager? = null
    private val mixer = AudioMixer()
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
        includeMicrophone: Boolean = true,
        cameraLens: CameraCaptureManager.Lens = CameraCaptureManager.Lens.BACK,
        cameraScale: Float = 0.32f,
        cameraX: Float = 0.62f,
        cameraY: Float = -0.62f,
        cameraFrameShape: SceneCompositor.FrameShape = SceneCompositor.FrameShape.ROUNDED,
        cameraCornerRadius: Float = 0.14f,
        cameraBorderWidth: Float = 0.018f,
        sceneSources: List<SourceEntity> = emptyList(),
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
            recorder = encoder

            val gpuCompositor = SceneCompositor()
            gpuCompositor.initialize(encoderSurface)
            compositor = gpuCompositor
            gpuCompositor.setPreviewSurface(previewSurface)

            val screenEnabled = sceneSources.isEmpty() || sceneSources.any { it.type.equals("SCREEN", true) && it.visible }
            val screenInput = if (screenEnabled) gpuCompositor.createInputSurface(width, height) else null
            screenInput?.let { gpuCompositor.updateLayer(it, z = 0) }

            val screenCapture = ScreenCaptureManager(context)
            screen = screenCapture
            if (screenInput != null) screenCapture.startDirect(
                code = code,
                data = data,
                surface = screenInput.surface,
                width = width,
                height = height,
                dpi = context.resources.displayMetrics.densityDpi,
                onStopped = {
                    stop()
                    onError(IllegalStateException("MediaProjection session ended"))
                },
            )

            if (includeMicrophone) {
                mixer.configure("microphone")
                val microphone = AudioCaptureManager()
                audio = microphone
                microphone.start(
                    onPcm = { pcm, ptsUs ->
                        mixer.process("microphone", pcm)
                        encoder.queueAudio(pcm, ptsUs)
                    },
                    onLevel = {},
                    onError = { t -> stop(); onError(t) },
                )
            }

            val cameraRequired = includeCamera
            val cameraCapture = if (cameraRequired) {
                val manager = CameraCaptureManager(context, lifecycleOwner)
                camera = manager
                val cameraInput = gpuCompositor.createInputSurface(width, height)
                gpuCompositor.updateLayer(
                    cameraInput,
                    z = 1,
                    scaleX = cameraScale,
                    scaleY = cameraScale,
                    translateX = cameraX,
                    translateY = cameraY,
                )
                gpuCompositor.setFrameStyle(
                    cameraInput,
                    shape = cameraFrameShape,
                    borderWidth = cameraBorderWidth,
                    cornerRadius = cameraCornerRadius,
                )
                manager.start(
                    target = cameraInput.surface,
                    width = width,
                    height = height,
                    lens = cameraLens,
                    onStarted = onStarted,
                    onError = { t -> stop(); onError(t) },
                )
                manager
            } else {
                null
            }

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

            if (!cameraRequired) onStarted()
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
