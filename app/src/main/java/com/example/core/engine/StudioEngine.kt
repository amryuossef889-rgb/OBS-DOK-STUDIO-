package com.example.core.engine

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.lifecycle.LifecycleOwner
import com.example.core.capability.CapabilityDiscovery
import com.example.core.capture.audio.AudioCaptureEngine
import com.example.core.capture.audio.PlaybackCaptureEngine
import com.example.core.capture.camera.CameraCaptureEngine
import com.example.core.capture.screen.ScreenCaptureEngine
import com.example.core.compositor.StudioCompositor
import com.example.core.diagnostics.StudioDiagnostics
import com.example.core.encoder.AudioEncoder
import com.example.core.encoder.VideoEncoder
import com.example.core.mixer.AudioMixer
import com.example.core.model.DeviceCapability
import com.example.core.model.QualitySettings
import com.example.core.model.QualityTier
import com.example.core.model.RecordingItem
import com.example.core.model.Scene
import com.example.core.model.StreamProfile
import com.example.core.model.StreamState
import com.example.core.model.StudioState
import com.example.core.muxer.RecordingMuxer
import com.example.core.streaming.RtmpStreamer
import com.example.core.thermal.ThermalAction
import com.example.core.thermal.ThermalMonitor
import com.example.core.thermal.ThermalStatus
import com.example.data.repository.StudioRepository
import com.example.service.StudioForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class StudioEngine(
    val context: Context,
    val repository: StudioRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    val diagnostics = StudioDiagnostics()

    // Capability & Quality
    val deviceCapability: DeviceCapability = CapabilityDiscovery.discoverCapabilities()
    private val _qualitySettings = MutableStateFlow(CapabilityDiscovery.getAutoQuality(deviceCapability))
    val qualitySettings = _qualitySettings.asStateFlow()

    // State
    private val _studioState = MutableStateFlow(StudioState.IDLE)
    val studioState = _studioState.asStateFlow()

    val streamState: kotlinx.coroutines.flow.StateFlow<StreamState>
        get() = streamer.streamState

    // Core Pipelines
    val compositor = StudioCompositor(
        outputWidth = _qualitySettings.value.width,
        outputHeight = _qualitySettings.value.height,
        targetFps = _qualitySettings.value.fps
    )

    val cameraEngine = CameraCaptureEngine(context)
    val screenEngine = ScreenCaptureEngine(context)
    val audioCaptureEngine = AudioCaptureEngine()
    val playbackCaptureEngine = PlaybackCaptureEngine()
    val audioMixer = AudioMixer()

    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    private val recordingMuxer = RecordingMuxer(context)
    private val streamer = RtmpStreamer()
    private val thermalMonitor = ThermalMonitor(context)

    private var activeScene: Scene? = null
    private var activeLifecycleOwner: LifecycleOwner? = null
    private var statsJob: Job? = null

    init {
        diagnostics.log("INFO", "Studio Engine initialized on ${deviceCapability.hardwareEncoderName}")
        setupThermalMonitoring()
        startStatsLoop()
    }

    private fun setupThermalMonitoring() {
        thermalMonitor.start(context.mainExecutor)
        scope.launch {
            thermalMonitor.actions.collect { action ->
                when (action) {
                    is ThermalAction.Warning -> {
                        diagnostics.log("WARN", "Thermal Warning: Device is warming up")
                    }
                    is ThermalAction.DowngradeQuality -> {
                        diagnostics.log("WARN", "Thermal SEVERE: Automatic quality downgrade requested")
                        requestQualityDowngrade("Thermal Throttling")
                    }
                    is ThermalAction.ForceStop -> {
                        diagnostics.log("ERROR", "Thermal CRITICAL: Emergency stop to prevent shutdown")
                        forceEmergencyStop("Device Overheating")
                    }
                }
            }
        }
    }

    fun initializeStudio(lifecycleOwner: LifecycleOwner) {
        activeLifecycleOwner = lifecycleOwner
        compositor.startRendering()

        cameraEngine.initialize {
            val tex = compositor.cameraSurfaceTexture
            if (tex != null) {
                cameraEngine.startCamera(
                    lifecycleOwner = lifecycleOwner,
                    surfaceTexture = tex,
                    width = _qualitySettings.value.width,
                    height = _qualitySettings.value.height,
                    useFrontCamera = false,
                    onError = { err -> diagnostics.log("ERROR", "Camera error: $err") }
                )
            }
        }

        // Start mic capture to audio mixer
        audioCaptureEngine.start { buffer, read ->
            val mixed = ByteArray(read)
            val mixedLen = audioMixer.mixPcm(buffer, read, null, 0, mixed)
            audioEncoder?.feedPcm(mixed, mixedLen)
        }

        _studioState.value = StudioState.READY
        diagnostics.log("INFO", "Studio is ready with camera and audio pipelines active")
    }

    fun setPreviewSurface(surface: Surface?) {
        compositor.setPreviewSurface(surface)
    }

    fun setScene(scene: Scene) {
        activeScene = scene
        compositor.setScene(scene)
        diagnostics.log("INFO", "Scene switched: ${scene.name}")
    }

    fun updateQualitySettings(newSettings: QualitySettings) {
        if (_studioState.value == StudioState.RECORDING || streamState.value == StreamState.LIVE) {
            diagnostics.log("WARN", "Cannot change base resolution/codec while session is active")
            return
        }
        _qualitySettings.value = newSettings
        diagnostics.log("INFO", "Quality updated: ${newSettings.width}x${newSettings.height} @ ${newSettings.fps}fps")
    }

    fun switchCamera() {
        val owner = activeLifecycleOwner ?: return
        cameraEngine.switchCamera(owner) { err ->
            diagnostics.log("ERROR", "Camera switch failed: $err")
        }
    }

    fun handleScreenCaptureResult(resultCode: Int, data: Intent) {
        val inputSurface = compositor.screenInputSurface ?: return
        val success = screenEngine.startCapture(
            resultCode = resultCode,
            resultData = data,
            surface = inputSurface,
            width = _qualitySettings.value.width,
            height = _qualitySettings.value.height,
            densityDpi = context.resources.displayMetrics.densityDpi,
            onStopped = {
                diagnostics.log("WARN", "Screen capture stopped by system")
            }
        )
        if (success) {
            diagnostics.log("INFO", "Screen capture active and rendered into compositor")
        } else {
            diagnostics.log("ERROR", "Screen capture authorization failed")
        }
    }

    fun stopScreenCapture() {
        screenEngine.stopCapture()
    }

    fun startRecording(): Boolean {
        if (_studioState.value == StudioState.RECORDING) return false
        _studioState.value = StudioState.PREPARING

        val settings = _qualitySettings.value
        val startedMuxer = recordingMuxer.startRecording()
        if (!startedMuxer) {
            _studioState.value = StudioState.READY
            diagnostics.log("ERROR", "Failed to start recording muxer")
            return false
        }

        // Start Foreground Service
        StudioForegroundService.start(
            context,
            hasCamera = cameraEngine.isRunning,
            hasMic = true,
            hasScreen = screenEngine.isCapturing
        )

        // Initialize encoders if not already running for stream
        initEncoders(settings)

        _studioState.value = StudioState.RECORDING
        diagnostics.log("INFO", "Recording started successfully: ${settings.width}x${settings.height}")
        return true
    }

    fun stopRecording() {
        if (_studioState.value != StudioState.RECORDING) return
        _studioState.value = StudioState.STOPPING

        val recordedFile = recordingMuxer.stopRecording()

        if (streamState.value != StreamState.LIVE) {
            stopEncoders()
            StudioForegroundService.stop(context)
        }

        _studioState.value = StudioState.READY

        if (recordedFile != null && recordedFile.exists()) {
            val duration = System.currentTimeMillis() - recordingMuxer.startTimeMs
            val size = recordedFile.length()
            val item = RecordingItem(
                id = UUID.randomUUID().toString(),
                title = recordedFile.name,
                filePath = recordedFile.absolutePath,
                durationMs = duration,
                sizeBytes = size,
                width = _qualitySettings.value.width,
                height = _qualitySettings.value.height
            )
            scope.launch {
                repository.addRecording(item)
            }
            diagnostics.log("INFO", "Recording saved: ${recordedFile.name} (${size / 1024} KB)")
        }
    }

    fun startLive(profile: StreamProfile) {
        if (streamState.value == StreamState.LIVE || streamState.value == StreamState.CONNECTING) return
        val settings = _qualitySettings.value

        StudioForegroundService.start(
            context,
            hasCamera = cameraEngine.isRunning,
            hasMic = true,
            hasScreen = screenEngine.isCapturing
        )

        initEncoders(settings)
        streamer.startStreaming(profile, settings.width, settings.height, settings.fps)
        diagnostics.log("INFO", "Connecting to live stream: ${profile.serverUrl}")
    }

    fun stopLive() {
        streamer.stopStreaming()
        if (_studioState.value != StudioState.RECORDING) {
            stopEncoders()
            StudioForegroundService.stop(context)
        }
        diagnostics.log("INFO", "Live broadcast ended")
    }

    private fun initEncoders(settings: QualitySettings) {
        if (videoEncoder != null && audioEncoder != null) return

        videoEncoder = VideoEncoder(
            qualitySettings = settings,
            onOutputFormatChanged = { format ->
                recordingMuxer.setVideoFormat(format)
                streamer.onVideoFormat(format)
            },
            onEncodedFrame = { buffer, info ->
                recordingMuxer.writeVideoFrame(buffer, info)
                streamer.sendVideoFrame(buffer, info)
            }
        ).also { encoder ->
            encoder.start()
            compositor.setEncoderSurface(encoder.inputSurface)
        }

        audioEncoder = AudioEncoder(
            sampleRate = 44100,
            channelCount = 2,
            bitrate = 128000,
            onOutputFormatChanged = { format ->
                recordingMuxer.setAudioFormat(format)
                streamer.onAudioFormat(format)
            },
            onEncodedFrame = { buffer, info ->
                recordingMuxer.writeAudioFrame(buffer, info)
                streamer.sendAudioFrame(buffer, info)
            }
        ).also { it.start() }
    }

    private fun stopEncoders() {
        compositor.setEncoderSurface(null)
        videoEncoder?.stop()
        videoEncoder = null
        audioEncoder?.stop()
        audioEncoder = null
    }

    private fun requestQualityDowngrade(reason: String) {
        val currTier = _qualitySettings.value.selectedTier
        val fallback = CapabilityDiscovery.getFallbackTier(currTier, deviceCapability)
        if (fallback != null) {
            val newSettings = CapabilityDiscovery.getTierSettings(fallback, deviceCapability)
            _qualitySettings.value = newSettings
            diagnostics.log("WARN", "Quality downgraded to ${fallback.displayName} ($reason)")
        }
    }

    private fun forceEmergencyStop(reason: String) {
        diagnostics.log("ERROR", "Emergency Stop Triggered: $reason")
        if (_studioState.value == StudioState.RECORDING) {
            stopRecording()
        }
        if (streamState.value == StreamState.LIVE) {
            stopLive()
        }
    }

    private fun startStatsLoop() {
        statsJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val renderFps = compositor.currentRenderFps
                val encFps = videoEncoder?.encodedFramesCount?.toFloat() ?: 0f
                val drops = (videoEncoder?.droppedFramesCount ?: 0L) + streamer.droppedFrames
                val bitrate = streamer.currentBitrateKbps
                val thermalText = thermalMonitor.thermalStatus.value.name

                diagnostics.updateMetrics(
                    renderFps = renderFps,
                    encoderFps = encFps,
                    droppedFrames = drops,
                    bitrateKbps = bitrate,
                    thermalStatus = thermalText
                )
            }
        }
    }

    fun release() {
        statsJob?.cancel()
        thermalMonitor.stop()
        stopRecording()
        stopLive()
        cameraEngine.stopCamera()
        screenEngine.stopCapture()
        audioCaptureEngine.stop()
        playbackCaptureEngine.stop()
        compositor.release()
        diagnostics.log("INFO", "Studio Engine shut down cleanly")
    }
}
