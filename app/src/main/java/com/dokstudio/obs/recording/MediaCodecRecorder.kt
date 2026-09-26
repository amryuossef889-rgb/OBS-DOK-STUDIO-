package com.dokstudio.obs.recording

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

class MediaCodecRecorder(private val context: Context) {
    data class Config(
        val width: Int,
        val height: Int,
        val fps: Int,
        val bitrate: Int,
        val sampleRate: Int = 48_000,
        val audioBitrate: Int = 128_000,
    )

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private var outputUri: Uri? = null
    private var outputFd: android.os.ParcelFileDescriptor? = null
    private val stopped = AtomicBoolean(false)
    private var lastAudioPtsUs = 0L

    fun start(config: Config): Surface {
        require(config.width > 0 && config.height > 0 && config.fps > 0 && config.bitrate > 0)

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "OBS_Dok_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OBS Dok Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        try {
            outputUri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: error("MediaStore insert failed")

            outputFd = context.contentResolver.openFileDescriptor(outputUri!!, "rw")
                ?: error("Cannot open output")

            muxer = MediaMuxer(
                outputFd!!.fileDescriptor,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )

            val videoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                config.width,
                config.height,
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }

            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                it.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                config.sampleRate,
                1,
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate)
            }

            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
                it.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            val inputSurface = videoCodec!!.createInputSurface()
            videoCodec!!.start()
            audioCodec!!.start()
            return inputSurface
        } catch (t: Throwable) {
            cleanupFailedStart()
            throw t
        }
    }

    fun drainVideo(timeoutUs: Long = 0): Boolean {
        val codec = videoCodec ?: return false
        val info = MediaCodec.BufferInfo()
        var didWork = false

        while (true) {
            when (val index = codec.dequeueOutputBuffer(info, timeoutUs)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return didWork
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(videoTrack < 0) { "Video output format changed twice" }
                    videoTrack = muxer!!.addTrack(codec.outputFormat)
                    startMuxerIfReady()
                    didWork = true
                }
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> {
                    if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null && info.size > 0 && muxerStarted && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer!!.writeSampleData(videoTrack, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        didWork = true
                    }
                }
            }
        }
    }

    fun outputUri(): Uri? = outputUri

    fun queueAudio(pcm: ByteArray, ptsUs: Long) {
        val codec = audioCodec ?: return
        lastAudioPtsUs = maxOf(lastAudioPtsUs, ptsUs)
        var offset = 0
        while (offset < pcm.size) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index < 0) return
            val buffer = codec.getInputBuffer(index) ?: return
            buffer.clear()
            val count = minOf(buffer.remaining(), pcm.size - offset)
            buffer.put(pcm, offset, count)
            codec.queueInputBuffer(index, 0, count, ptsUs, 0)
            offset += count
        }
        drainAudio()
    }

    private fun drainAudio() {
        val codec = audioCodec ?: return
        val info = MediaCodec.BufferInfo()

        while (true) {
            when (val index = codec.dequeueOutputBuffer(info, 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(audioTrack < 0) { "Audio output format changed twice" }
                    audioTrack = muxer!!.addTrack(codec.outputFormat)
                    startMuxerIfReady()
                }
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0 && muxerStarted && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer!!.writeSampleData(audioTrack, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    private fun startMuxerIfReady() {
        if (!muxerStarted && videoTrack >= 0 && audioTrack >= 0) {
            muxer!!.start()
            muxerStarted = true
        }
    }

    fun stop(): Uri? {
        if (!stopped.compareAndSet(false, true)) return outputUri

        try {
            videoCodec?.signalEndOfInputStream()
            drainUntilEnd(videoCodec)
            audioCodec?.let { codec ->
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    codec.queueInputBuffer(
                        index,
                        0,
                        0,
                        maxOf(lastAudioPtsUs, System.nanoTime() / 1_000),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                }
                drainUntilEnd(codec)
            }

            videoCodec?.stop()
            audioCodec?.stop()

            if (muxerStarted) muxer?.stop()
            muxer?.release()
            outputFd?.close()

            if (Build.VERSION.SDK_INT >= 29 && outputUri != null) {
                context.contentResolver.update(
                    outputUri!!,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
        } catch (t: Throwable) {
            outputUri?.let {
                runCatching { context.contentResolver.delete(it, null, null) }
            }
            throw t
        } finally {
            videoCodec?.release()
            audioCodec?.release()
            videoCodec = null
            audioCodec = null
            muxer = null
            outputFd = null
        }

        return outputUri
    }

    private fun drainUntilEnd(codec: MediaCodec?) {
        if (codec == null) return
        val info = MediaCodec.BufferInfo()
        var eos = false
        repeat(200) {
            if (eos) return
            when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (codec === videoCodec) {
                        if (videoTrack < 0) videoTrack = muxer!!.addTrack(codec.outputFormat)
                    } else if (audioTrack < 0) {
                        audioTrack = muxer!!.addTrack(codec.outputFormat)
                    }
                    startMuxerIfReady()
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0 && muxerStarted && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer!!.writeSampleData(if (codec === videoCodec) videoTrack else audioTrack, buffer, info)
                    }
                    eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
            }
        }
    }

    private fun cleanupFailedStart() {
        runCatching { videoCodec?.release() }
        runCatching { audioCodec?.release() }
        runCatching { muxer?.release() }
        runCatching { outputFd?.close() }
        outputUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        videoCodec = null
        audioCodec = null
        muxer = null
        outputFd = null
        outputUri = null
    }
}
