package com.example.core.capture.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

class AudioCaptureEngine(
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

    @SuppressLint("MissingPermission")
    fun start(onPcmData: (ByteArray, Int) -> Unit): Boolean {
        if (isRecording) return true

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize <= 0) {
            Log.e("AudioCaptureEngine", "Invalid buffer size: $bufferSize")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioCaptureEngine", "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            captureThread = Thread({
                val buffer = ByteArray(bufferSize)
                while (isRecording && audioRecord != null) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        calculateLevels(buffer, read)
                        onPcmData(buffer, read)
                    }
                }
            }, "AudioCaptureThread")
            captureThread?.start()

            Log.i("AudioCaptureEngine", "Microphone capture started successfully")
            return true
        } catch (e: Exception) {
            Log.e("AudioCaptureEngine", "Failed to start AudioRecord: ${e.message}", e)
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
            Log.i("AudioCaptureEngine", "AudioRecord stopped")
        } catch (e: Exception) {
            Log.w("AudioCaptureEngine", "Error stopping AudioRecord: ${e.message}")
        }
    }
}
