package com.example.core.mixer

import com.example.core.model.AudioChannelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AudioMixer(
    val sampleRate: Int = 44100,
    val channels: Int = 2
) {

    private val channelConfigs = ConcurrentHashMap<String, AudioChannelConfig>()
    private val _channelsState = MutableStateFlow<List<AudioChannelConfig>>(emptyList())
    val channelsState = _channelsState.asStateFlow()

    init {
        // Default channels: Mic and Device Audio
        channelConfigs["mic"] = AudioChannelConfig(id = "mic", name = "Microphone", volume = 1.0f)
        channelConfigs["device"] = AudioChannelConfig(id = "device", name = "Desktop Audio", volume = 1.0f)
        updateState()
    }

    private fun updateState() {
        _channelsState.value = channelConfigs.values.toList()
    }

    fun setVolume(channelId: String, volume: Float) {
        val curr = channelConfigs[channelId] ?: return
        channelConfigs[channelId] = curr.copy(volume = volume.coerceIn(0f, 1.5f))
        updateState()
    }

    fun setMute(channelId: String, isMuted: Boolean) {
        val curr = channelConfigs[channelId] ?: return
        channelConfigs[channelId] = curr.copy(isMuted = isMuted)
        updateState()
    }

    fun setSolo(channelId: String, isSolo: Boolean) {
        val curr = channelConfigs[channelId] ?: return
        channelConfigs[channelId] = curr.copy(isSolo = isSolo)
        updateState()
    }

    fun updateLevels(channelId: String, rmsDb: Float, peakDb: Float) {
        val curr = channelConfigs[channelId] ?: return
        channelConfigs[channelId] = curr.copy(currentRmsDb = rmsDb, peakDb = peakDb)
        updateState()
    }

    /**
     * Mixes multiple PCM 16-bit buffers into a single output buffer.
     * Takes channel volumes, mute, solo into account and clamps to prevent integer clipping.
     */
    fun mixPcm(
        micPcm: ByteArray?,
        micLength: Int,
        devicePcm: ByteArray?,
        deviceLength: Int,
        outputBuffer: ByteArray
    ): Int {
        val micConfig = channelConfigs["mic"] ?: AudioChannelConfig(id = "mic", name = "Mic")
        val deviceConfig = channelConfigs["device"] ?: AudioChannelConfig(id = "device", name = "Device")

        val hasSolo = micConfig.isSolo || deviceConfig.isSolo

        val micGain = if (micConfig.isMuted || (hasSolo && !micConfig.isSolo)) 0f else micConfig.volume
        val devGain = if (deviceConfig.isMuted || (hasSolo && !deviceConfig.isSolo)) 0f else deviceConfig.volume

        val maxLength = max(
            if (micPcm != null && micGain > 0f) micLength else 0,
            if (devicePcm != null && devGain > 0f) deviceLength else 0
        )
        val clampedMax = min(maxLength, outputBuffer.size)

        var micSum = 0.0
        var micPeak = 0.0
        var devSum = 0.0
        var devPeak = 0.0

        for (i in 0 until clampedMax - 1 step 2) {
            var mixedSample = 0

            if (micPcm != null && i + 1 < micLength && micGain > 0f) {
                val sample = ((micPcm[i + 1].toInt() shl 8) or (micPcm[i].toInt() and 0xFF)).toShort()
                val absS = kotlin.math.abs(sample.toDouble())
                if (absS > micPeak) micPeak = absS
                micSum += sample * sample
                mixedSample += (sample * micGain).toInt()
            }

            if (devicePcm != null && i + 1 < deviceLength && devGain > 0f) {
                val sample = ((devicePcm[i + 1].toInt() shl 8) or (devicePcm[i].toInt() and 0xFF)).toShort()
                val absS = kotlin.math.abs(sample.toDouble())
                if (absS > devPeak) devPeak = absS
                devSum += sample * sample
                mixedSample += (sample * devGain).toInt()
            }

            // Hard clamp 16-bit PCM: -32768 to 32767
            val clamped = mixedSample.coerceIn(-32768, 32767).toShort()
            outputBuffer[i] = (clamped.toInt() and 0xFF).toByte()
            outputBuffer[i + 1] = ((clamped.toInt() shr 8) and 0xFF).toByte()
        }

        // Update meters
        val samples = clampedMax / 2
        if (samples > 0) {
            val micRms = if (micSum > 0) (20.0 * log10(sqrt(micSum / samples) / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
            val devRms = if (devSum > 0) (20.0 * log10(sqrt(devSum / samples) / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
            val micPk = if (micPeak > 0) (20.0 * log10(micPeak / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
            val devPk = if (devPeak > 0) (20.0 * log10(devPeak / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f

            channelConfigs["mic"] = micConfig.copy(currentRmsDb = micRms, peakDb = micPk)
            channelConfigs["device"] = deviceConfig.copy(currentRmsDb = devRms, peakDb = devPk)
            updateState()
        }

        return clampedMax
    }
}
