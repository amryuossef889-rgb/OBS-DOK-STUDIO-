package com.example.core.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class AudioEncoder(
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 2,
    private val bitrate: Int = 128000,
    private val onOutputFormatChanged: (MediaFormat) -> Unit,
    private val onEncodedFrame: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
) {

    private var mediaCodec: MediaCodec? = null
    private val isRunning = AtomicBoolean(false)
    private var drainThread: Thread? = null

    private var presentationTimeUs: Long = 0L

    fun start(): Boolean {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            mediaCodec = codec

            isRunning.set(true)
            drainThread = Thread({ drainEncoder() }, "AudioEncoderDrainThread").apply { start() }

            Log.i("AudioEncoder", "Audio encoder started (AAC $sampleRate Hz, $bitrate bps)")
            return true
        } catch (e: Exception) {
            Log.e("AudioEncoder", "Failed to start AudioEncoder: ${e.message}", e)
            stop()
            return false
        }
    }

    fun feedPcm(buffer: ByteArray, length: Int) {
        val codec = mediaCodec ?: return
        if (!isRunning.get() || length <= 0) return

        try {
            val inputIndex = codec.dequeueInputBuffer(10000L)
            if (inputIndex >= 0) {
                val inputBuf = codec.getInputBuffer(inputIndex)
                if (inputBuf != null) {
                    inputBuf.clear()
                    inputBuf.put(buffer, 0, length)

                    // Calculate presentation time based on audio samples
                    val samples = length / (2 * channelCount)
                    val pts = presentationTimeUs
                    presentationTimeUs += (samples * 1_000_000L) / sampleRate

                    codec.queueInputBuffer(inputIndex, 0, length, pts, 0)
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEncoder", "Error queuing PCM to AudioEncoder: ${e.message}")
        }
    }

    private fun drainEncoder() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (isRunning.get()) {
            val status = codec.dequeueOutputBuffer(bufferInfo, 10000L)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Waiting for more data
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    Log.i("AudioEncoder", "Audio encoder format changed: $newFormat")
                    onOutputFormatChanged(newFormat)
                }
                status >= 0 -> {
                    val encodedBuffer = codec.getOutputBuffer(status)
                    if (encodedBuffer != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0) {
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            onEncodedFrame(encodedBuffer, bufferInfo)
                        }
                    }
                    codec.releaseOutputBuffer(status, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            drainThread?.join(500)
            drainThread = null
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            Log.i("AudioEncoder", "AudioEncoder stopped")
        } catch (e: Exception) {
            Log.w("AudioEncoder", "Error stopping AudioEncoder: ${e.message}")
        }
    }
}
