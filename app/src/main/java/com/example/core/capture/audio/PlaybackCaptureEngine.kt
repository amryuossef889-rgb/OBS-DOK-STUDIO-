package com.example.core.capture.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

class PlaybackCaptureEngine(
    private val sampleRate: Int = 44100,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_STEREO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var captureThread: Thread? = null

    @Volatile
    var currentRmsDb: Float = -60f
        private set

    @Volatile
    var peakDb: Float = -60f
        private set

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @SuppressLint("MissingPermission")
    fun start(mediaProjection: MediaProjection, onPcmData: (ByteArray, Int) -> Unit): Boolean {
        if (!isSupported) {
            Log.w("PlaybackCaptureEngine", "AudioPlaybackCapture requires Android 10 (API 29)+")
            return false
        }
        if (isRecording) return true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    // Note: Android system explicitly disallows USAGE_VOICE_COMMUNICATION or ALARM capture for privacy
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()

                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                audioRecord = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBuf * 2)
                    .build()

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("PlaybackCaptureEngine", "Playback AudioRecord not initialized")
                    audioRecord?.release()
                    audioRecord = null
                    return false
                }

                audioRecord?.startRecording()
                isRecording = true

                captureThread = Thread({
                    val buffer = ByteArray(minBuf)
                    while (isRecording && audioRecord != null) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            calculateLevels(buffer, read)
                            onPcmData(buffer, read)
                        }
                    }
                }, "PlaybackCaptureThread")
                captureThread?.start()

                Log.i("PlaybackCaptureEngine", "AudioPlaybackCapture started")
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e("PlaybackCaptureEngine", "Failed to start AudioPlaybackCapture: ${e.message}", e)
            stop()
            return false
        }
    }

    private fun calculateLevels(buffer: ByteArray, length: Int) {
        var sum = 0.0
        var peak = 0.0
        val sampleCount = length / 2
        for (i in 0 until length - 1 step 2) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            val absSample = kotlin.math.abs(sample.toDouble())
            if (absSample > peak) peak = absSample
            sum += sample * sample
        }
        val rms = if (sampleCount > 0) sqrt(sum / sampleCount) else 0.0
        val rmsDb = if (rms > 0.0) (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
        val pDb = if (peak > 0.0) (20.0 * log10(peak / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f

        currentRmsDb = rmsDb
        if (pDb > peakDb) {
            peakDb = pDb
        } else {
            peakDb = (peakDb * 0.92f).coerceAtLeast(-60f)
        }
    }

    fun stop() {
        isRecording = false
        try {
            captureThread?.join(500)
            captureThread = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            currentRmsDb = -60f
            peakDb = -60f
            Log.i("PlaybackCaptureEngine", "AudioPlaybackCapture stopped")
        } catch (e: Exception) {
            Log.w("PlaybackCaptureEngine", "Error stopping playback audio: ${e.message}")
        }
    }
}
