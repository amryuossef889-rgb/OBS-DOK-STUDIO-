package com.example.core.muxer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

class RecordingMuxer(private val context: Context) {

    private var mediaMuxer: MediaMuxer? = null
    var outputFile: File? = null
        private set

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false

    private val lock = Any()
    private val pendingFrames = ConcurrentLinkedQueue<PendingFrame>()

    var startTimeMs = 0L
        private set

    data class PendingFrame(
        val isVideo: Boolean,
        val buffer: ByteBuffer,
        val info: MediaCodec.BufferInfo
    )

    fun startRecording(targetFile: File? = null): Boolean {
        synchronized(lock) {
            try {
                videoTrackIndex = -1
                audioTrackIndex = -1
                isMuxerStarted = false
                pendingFrames.clear()

                val file = targetFile ?: run {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                    File(storageDir, "OBS_Dok_${timeStamp}.mp4")
                }
                outputFile = file

                mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                startTimeMs = System.currentTimeMillis()

                Log.i("RecordingMuxer", "Muxer initialized for file: ${file.absolutePath}")
                return true
            } catch (e: Exception) {
                Log.e("RecordingMuxer", "Failed to start MediaMuxer: ${e.message}", e)
                return false
            }
        }
    }

    fun setVideoFormat(format: MediaFormat) {
        synchronized(lock) {
            val muxer = mediaMuxer ?: return
            if (videoTrackIndex != -1) return

            videoTrackIndex = muxer.addTrack(format)
            Log.i("RecordingMuxer", "Added video track (index=$videoTrackIndex)")
            checkStartMuxer()
        }
    }

    fun setAudioFormat(format: MediaFormat) {
        synchronized(lock) {
            val muxer = mediaMuxer ?: return
            if (audioTrackIndex != -1) return

            audioTrackIndex = muxer.addTrack(format)
            Log.i("RecordingMuxer", "Added audio track (index=$audioTrackIndex)")
            checkStartMuxer()
        }
    }

    private fun checkStartMuxer() {
        val muxer = mediaMuxer ?: return
        if (videoTrackIndex != -1 && audioTrackIndex != -1 && !isMuxerStarted) {
            muxer.start()
            isMuxerStarted = true
            Log.i("RecordingMuxer", "MediaMuxer started successfully!")

            // Drain queued frames
            while (pendingFrames.isNotEmpty()) {
                val frame = pendingFrames.poll() ?: break
                val track = if (frame.isVideo) videoTrackIndex else audioTrackIndex
                muxer.writeSampleData(track, frame.buffer, frame.info)
            }
        }
    }

    fun writeVideoFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        writeFrame(true, buffer, info)
    }

    fun writeAudioFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        writeFrame(false, buffer, info)
    }

    private fun writeFrame(isVideo: Boolean, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            val muxer = mediaMuxer ?: return

            if (!isMuxerStarted) {
                // Buffer frame copy until muxer has all tracks
                val copy = ByteBuffer.allocateDirect(info.size)
                val dup = buffer.duplicate()
                dup.position(info.offset)
                dup.limit(info.offset + info.size)
                copy.put(dup)
                copy.flip()

                val infoCopy = MediaCodec.BufferInfo().apply {
                    set(0, info.size, info.presentationTimeUs, info.flags)
                }
                pendingFrames.add(PendingFrame(isVideo, copy, infoCopy))
                return
            }

            val track = if (isVideo) videoTrackIndex else audioTrackIndex
            if (track >= 0 && info.size > 0) {
                muxer.writeSampleData(track, buffer, info)
            }
        }
    }

    fun stopRecording(): File? {
        synchronized(lock) {
            val muxer = mediaMuxer ?: return null
            try {
                if (isMuxerStarted) {
                    muxer.stop()
                }
                muxer.release()
                Log.i("RecordingMuxer", "MediaMuxer stopped and released successfully")
            } catch (e: Exception) {
                Log.w("RecordingMuxer", "Error stopping MediaMuxer: ${e.message}")
            } finally {
                mediaMuxer = null
                isMuxerStarted = false
                videoTrackIndex = -1
                audioTrackIndex = -1
                pendingFrames.clear()
            }
            return outputFile
        }
    }
}
