package com.dokstudio.obs.mixer

import kotlin.math.abs
import kotlin.math.max

data class MixerChannelState(
    var volume: Float = 1f,
    var muted: Boolean = false,
    var solo: Boolean = false,
    var peak: Float = 0f,
)

class AudioMixer {
    private val channels = mutableMapOf<String, MixerChannelState>()

    @Synchronized
    fun configure(sourceId: String, volume: Float = 1f, muted: Boolean = false, solo: Boolean = false) {
        channels.getOrPut(sourceId) { MixerChannelState() }.apply {
            this.volume = volume.coerceIn(0f, 2f)
            this.muted = muted
            this.solo = solo
        }
    }

    @Synchronized
    fun setVolume(sourceId: String, volume: Float) {
        channels.getOrPut(sourceId) { MixerChannelState() }.volume = volume.coerceIn(0f, 2f)
    }

    @Synchronized
    fun setMuted(sourceId: String, muted: Boolean) {
        channels.getOrPut(sourceId) { MixerChannelState() }.muted = muted
    }

    @Synchronized
    fun setSolo(sourceId: String, solo: Boolean) {
        channels.getOrPut(sourceId) { MixerChannelState() }.solo = solo
    }

    @Synchronized
    fun process(sourceId: String, pcm: ByteArray): Float {
        val state = channels.getOrPut(sourceId) { MixerChannelState() }
        val anySolo = channels.values.any { it.solo }
        val enabled = !state.muted && (!anySolo || state.solo)
        var peak = 0f
        if (enabled) {
            var i = 0
            while (i + 1 < pcm.size) {
                val sample = (pcm[i].toInt() and 0xff) or (pcm[i + 1].toInt() shl 8)
                val signed = sample.toShort().toInt()
                val scaled = (signed * state.volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcm[i] = (scaled and 0xff).toByte()
                pcm[i + 1] = ((scaled shr 8) and 0xff).toByte()
                peak = max(peak, abs(scaled / 32768f))
                i += 2
            }
        } else {
            pcm.fill(0)
        }
        state.peak = peak
        return peak
    }

    @Synchronized
    fun peak(sourceId: String): Float = channels[sourceId]?.peak ?: 0f

    @Synchronized
    fun resetPeaks() {
        channels.values.forEach { it.peak = 0f }
    }
}
