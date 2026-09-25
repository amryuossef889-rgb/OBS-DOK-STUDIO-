package com.example.core.capability

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.util.Range
import com.example.core.model.DeviceCapability
import com.example.core.model.QualitySettings
import com.example.core.model.QualityTier

object CapabilityDiscovery {

    private const val TAG = "CapabilityDiscovery"
    private var cachedCapability: DeviceCapability? = null

    fun discoverCapabilities(): DeviceCapability {
        cachedCapability?.let { return it }

        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val avcInfo = codecList.codecInfos.firstOrNull {
            it.isEncoder && it.supportedTypes.any { t -> t.equals("video/avc", ignoreCase = true) }
        } ?: codecList.codecInfos.firstOrNull { it.isEncoder }

        val hevcInfo = codecList.codecInfos.firstOrNull {
            it.isEncoder && it.supportedTypes.any { t -> t.equals("video/hevc", ignoreCase = true) }
        }

        var maxWidth = 1920
        var maxHeight = 1080
        var maxFpsAtMax = 30
        var maxFps = 30
        var bitrateRange = Range(256_000, 20_000_000)
        var encoderName = "default_encoder"

        if (avcInfo != null) {
            encoderName = avcInfo.name
            try {
                val caps = avcInfo.getCapabilitiesForType("video/avc")
                val videoCaps = caps.videoCapabilities
                if (videoCaps != null) {
                    maxWidth = videoCaps.supportedWidths.upper
                    maxHeight = videoCaps.supportedHeights.upper
                    maxFpsAtMax = runCatching {
                        videoCaps.getSupportedFrameRatesFor(maxWidth, maxHeight).upper.toInt()
                    }.getOrDefault(30)

                    val fpsAt1080 = runCatching {
                        videoCaps.getSupportedFrameRatesFor(1920, 1080).upper.toInt()
                    }.getOrDefault(maxFpsAtMax)

                    maxFps = maxOf(maxFpsAtMax, fpsAt1080)
                    bitrateRange = videoCaps.bitrateRange
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying AVC video capabilities: ${e.message}")
            }
        }

        val capability = DeviceCapability(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxFpsAtMaxRes = maxFpsAtMax,
            maxFps = maxFps,
            supportedBitrateRange = bitrateRange,
            hevcSupported = (hevcInfo != null),
            hardwareEncoderName = encoderName,
            thermalHeadroomOk = true
        )
        cachedCapability = capability
        return capability
    }

    fun isTierSupported(tier: QualityTier, cap: DeviceCapability): Boolean {
        return when (tier) {
            QualityTier.DATA_SAVER -> true
            QualityTier.BALANCED -> cap.maxWidth >= 1280 && cap.maxHeight >= 720
            QualityTier.HIGH_QUALITY -> cap.maxWidth >= 1920 && cap.maxHeight >= 1080
            QualityTier.HIGH_FPS -> cap.maxWidth >= 1920 && cap.maxHeight >= 1080 && cap.maxFps >= 60
            QualityTier.ULTRA_HEVC -> cap.hevcSupported && cap.maxHeight >= 1080
        }
    }

    fun getTierSettings(tier: QualityTier, cap: DeviceCapability): QualitySettings {
        return when (tier) {
            QualityTier.DATA_SAVER -> QualitySettings(
                isAuto = false,
                selectedTier = tier,
                width = 854,
                height = 480,
                fps = 30,
                bitrateKbps = cap.supportedBitrateRange.clamp(2000 * 1000) / 1000,
                codecMime = "video/avc",
                keyframeIntervalSeconds = 2
            )
            QualityTier.BALANCED -> QualitySettings(
                isAuto = false,
                selectedTier = tier,
                width = 1280,
                height = 720,
                fps = 30,
                bitrateKbps = cap.supportedBitrateRange.clamp(3500 * 1000) / 1000,
                codecMime = "video/avc",
                keyframeIntervalSeconds = 2
            )
            QualityTier.HIGH_QUALITY -> QualitySettings(
                isAuto = false,
                selectedTier = tier,
                width = 1920,
                height = 1080,
                fps = 30,
                bitrateKbps = cap.supportedBitrateRange.clamp(6000 * 1000) / 1000,
                codecMime = "video/avc",
                keyframeIntervalSeconds = 2
            )
            QualityTier.HIGH_FPS -> QualitySettings(
                isAuto = false,
                selectedTier = tier,
                width = 1920,
                height = 1080,
                fps = 60,
                bitrateKbps = cap.supportedBitrateRange.clamp(9000 * 1000) / 1000,
                codecMime = "video/avc",
                keyframeIntervalSeconds = 2
            )
            QualityTier.ULTRA_HEVC -> QualitySettings(
                isAuto = false,
                selectedTier = tier,
                width = 1920,
                height = 1080,
                fps = if (cap.maxFps >= 60) 60 else 30,
                bitrateKbps = cap.supportedBitrateRange.clamp(6000 * 1000) / 1000,
                codecMime = if (cap.hevcSupported) "video/hevc" else "video/avc",
                keyframeIntervalSeconds = 2
            )
        }
    }

    fun getAutoQuality(cap: DeviceCapability): QualitySettings {
        val targetTier = when {
            cap.hevcSupported && cap.maxHeight >= 1080 && cap.maxFps >= 60 -> QualityTier.ULTRA_HEVC
            cap.maxWidth >= 1920 && cap.maxHeight >= 1080 && cap.maxFps >= 60 -> QualityTier.HIGH_FPS
            cap.maxWidth >= 1920 && cap.maxHeight >= 1080 -> QualityTier.HIGH_QUALITY
            cap.maxWidth >= 1280 && cap.maxHeight >= 720 -> QualityTier.BALANCED
            else -> QualityTier.DATA_SAVER
        }
        return getTierSettings(targetTier, cap).copy(isAuto = true)
    }

    fun getFallbackTier(current: QualityTier, cap: DeviceCapability): QualityTier? {
        return when (current) {
            QualityTier.ULTRA_HEVC -> QualityTier.HIGH_FPS.takeIf { isTierSupported(it, cap) } ?: QualityTier.HIGH_QUALITY
            QualityTier.HIGH_FPS -> QualityTier.HIGH_QUALITY.takeIf { isTierSupported(it, cap) } ?: QualityTier.BALANCED
            QualityTier.HIGH_QUALITY -> QualityTier.BALANCED
            QualityTier.BALANCED -> QualityTier.DATA_SAVER
            QualityTier.DATA_SAVER -> null
        }
    }
}
