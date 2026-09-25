package com.example.core.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.core.model.QualitySettings
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoder(
    private val qualitySettings: QualitySettings,
    private val onOutputFormatChanged: (MediaFormat) -> Unit,
    private val onEncodedFrame: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
) {

    private var mediaCodec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    private val isRunning = AtomicBoolean(false)
    private var drainThread: Thread? = null

    @Volatile
    var encodedFramesCount: Long = 0L
        private set
    @Volatile
    var droppedFramesCount: Long = 0L
        private set

    fun start(): Boolean {
        try {
            val mime = qualitySettings.codecMime
            val format = MediaFormat.createVideoFormat(mime, qualitySettings.width, qualitySettings.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, qualitySettings.bitrateKbps * 1000)
                setInteger(MediaFormat.KEY_FRAME_RATE, qualitySettings.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, qualitySettings.keyframeIntervalSeconds)
            }

            val codec = MediaCodec.createEncoderByType(mime)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()
            mediaCodec = codec

            isRunning.set(true)
            drainThread = Thread({ drainEncoder() }, "VideoEncoderDrainThread").apply { start() }

            Log.i("VideoEncoder", "Video encoder started: ${qualitySettings.width}x${qualitySettings.height} @ ${qualitySettings.fps}fps (${qualitySettings.bitrateKbps}kbps)")
            return true
        } catch (e: Exception) {
            Log.e("VideoEncoder", "Failed to start VideoEncoder: ${e.message}", e)
            stop()
            return false
        }
    }

    private fun drainEncoder() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (isRunning.get()) {
            val status = codec.dequeueOutputBuffer(bufferInfo, 10000L)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output buffer ready yet
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    Log.i("VideoEncoder", "Encoder output format changed: $newFormat")
                    onOutputFormatChanged(newFormat)
                }
                status >= 0 -> {
                    val encodedBuffer = codec.getOutputBuffer(status)
                    if (encodedBuffer != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // Codec configuration data (SPS/PPS)
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0) {
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            onEncodedFrame(encodedBuffer, bufferInfo)
                            encodedFramesCount++
                        }
                    }
                    codec.releaseOutputBuffer(status, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.i("VideoEncoder", "End of stream reached on video encoder")
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
            inputSurface?.release()
            inputSurface = null
            Log.i("VideoEncoder", "VideoEncoder stopped")
        } catch (e: Exception) {
            Log.w("VideoEncoder", "Error stopping VideoEncoder: ${e.message}")
        }
    }
}
