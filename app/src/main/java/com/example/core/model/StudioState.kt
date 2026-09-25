package com.example.core.model

import android.util.Range

enum class StudioState {
    IDLE,
    PREPARING,
    READY,
    RECORDING,
    STOPPING
}

enum class StreamState {
    OFFLINE,
    CONNECTING,
    LIVE,
    RECONNECTING,
    STOPPING
}

enum class QualityTier(val displayName: String, val description: String) {
    DATA_SAVER("Data Saver", "480p · 30fps · ~2 Mbps"),
    BALANCED("Balanced", "720p · 30fps · ~3.5 Mbps"),
    HIGH_QUALITY("High Quality", "1080p · 30fps · ~6 Mbps"),
    HIGH_FPS("High FPS", "1080p · 60fps · ~9 Mbps"),
    ULTRA_HEVC("Ultra (HEVC)", "1080p · 60fps · High Efficiency")
}

data class DeviceCapability(
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFpsAtMaxRes: Int,
    val maxFps: Int,
    val supportedBitrateRange: Range<Int>,
    val hevcSupported: Boolean,
    val hardwareEncoderName: String,
    val thermalHeadroomOk: Boolean = true
)

data class QualitySettings(
    val isAuto: Boolean = true,
    val selectedTier: QualityTier = QualityTier.BALANCED,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrateKbps: Int = 3500,
    val codecMime: String = "video/avc", // video/avc or video/hevc
    val keyframeIntervalSeconds: Int = 2
)

data class AudioChannelConfig(
    val id: String,
    val name: String,
    val volume: Float = 1.0f,     // 0.0 to 1.5 (gain)
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val currentRmsDb: Float = -60f, // -60dB to 0dB
    val peakDb: Float = -60f
)

data class StreamProfile(
    val id: String = "default_stream",
    val title: String = "RTMP Server",
    val serverUrl: String = "rtmp://live.twitch.tv/app/",
    val streamKey: String = "",
    val authRequired: Boolean = false,
    val username: String = "",
    val password: String = ""
)

data class RecordingItem(
    val id: String,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis()
)
