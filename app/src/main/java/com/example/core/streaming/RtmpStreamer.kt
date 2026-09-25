package com.example.core.streaming

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.example.core.model.StreamProfile
import com.example.core.model.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.util.Random
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

class RtmpStreamer {

    private val _streamState = MutableStateFlow(StreamState.OFFLINE)
    val streamState = _streamState.asStateFlow()

    @Volatile
    var currentBitrateKbps: Int = 0
        private set

    @Volatile
    var droppedFrames: Long = 0L
        private set

    private val totalBytesSent = AtomicLong(0L)
    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var socket: Socket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null

    private val packetQueue = LinkedBlockingQueue<StreamPacket>(120)
    private val isStreaming = AtomicBoolean(false)

    private var videoSpsPps: ByteArray? = null
    private var audioSpecificConfig: ByteArray? = null
    private var streamStartTime = 0L

    data class StreamPacket(
        val isVideo: Boolean,
        val isKeyFrame: Boolean,
        val timestamp: Int,
        val payload: ByteArray
    )

    fun startStreaming(profile: StreamProfile, width: Int, height: Int, fps: Int) {
        if (isStreaming.get()) return
        isStreaming.set(true)
        _streamState.value = StreamState.CONNECTING

        streamJob = scope.launch {
            runCatching {
                connectAndStream(profile, width, height, fps)
            }.onFailure { e ->
                Log.e("RtmpStreamer", "Streaming failed: ${e.message}")
                _streamState.value = StreamState.OFFLINE
                isStreaming.set(false)
            }
        }
    }

    private suspend fun connectAndStream(profile: StreamProfile, width: Int, height: Int, fps: Int) {
        val parsedUri = runCatching { URI(profile.serverUrl) }.getOrNull()
        val host = parsedUri?.host ?: "127.0.0.1"
        val isSecure = profile.serverUrl.startsWith("rtmps://", ignoreCase = true)
        val port = if (parsedUri?.port != null && parsedUri.port != -1) parsedUri.port else if (isSecure) 443 else 1935
        val appName = parsedUri?.path?.trim('/') ?: "live"

        Log.i("RtmpStreamer", "Connecting to RTMP server at $host:$port (secure=$isSecure, app=$appName)")

        val rawSocket = if (isSecure) {
            SSLSocketFactory.getDefault().createSocket()
        } else {
            Socket()
        }
        socket = rawSocket
        rawSocket.connect(InetSocketAddress(host, port), 10000)
        rawSocket.tcpNoDelay = true

        val out = BufferedOutputStream(rawSocket.getOutputStream(), 65536)
        val inp = rawSocket.getInputStream()
        outStream = out
        inStream = inp

        // 1. RTMP Handshake
        performHandshake(inp, out)

        // 2. Connect Command
        sendConnect(out, appName, profile.serverUrl)

        // 3. CreateStream Command
        sendCreateStream(out)

        // 4. Publish Command
        sendPublish(out, profile.streamKey)

        // 5. Send Metadata
        sendMetadata(out, width, height, fps)

        _streamState.value = StreamState.LIVE
        Log.i("RtmpStreamer", "RTMP Stream is LIVE!")
        streamStartTime = System.currentTimeMillis()

        // Send headers if already available
        videoSpsPps?.let { sendVideoSequenceHeader(out, it) }
        audioSpecificConfig?.let { sendAudioSequenceHeader(out, it) }

        // Start throughput monitor
        var lastBytes = 0L
        var lastCheck = System.currentTimeMillis()

        // 6. Packet transmission loop
        while (isStreaming.get() && scope.isActive) {
            val packet = packetQueue.poll()
            if (packet != null) {
                sendPacket(out, packet)
                totalBytesSent.addAndGet(packet.payload.size.toLong())
            } else {
                Thread.sleep(5)
            }

            val now = System.currentTimeMillis()
            if (now - lastCheck >= 1000L) {
                val bytesDiff = totalBytesSent.get() - lastBytes
                currentBitrateKbps = ((bytesDiff * 8) / 1000).toInt()
                lastBytes = totalBytesSent.get()
                lastCheck = now
            }
        }
    }

    private fun performHandshake(inp: InputStream, out: OutputStream) {
        val c1 = ByteArray(1536)
        Random().nextBytes(c1)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0 // timestamp
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0 // zeros

        // Send C0 (1 byte: 0x03) + C1 (1536 bytes)
        out.write(0x03)
        out.write(c1)
        out.flush()

        // Read S0 (1 byte) + S1 (1536 bytes)
        val s0 = inp.read()
        val s1 = ByteArray(1536)
        readFully(inp, s1)

        // Send C2 (1536 bytes echoing S1)
        out.write(s1)
        out.flush()

        // Read S2 (1536 bytes)
        val s2 = ByteArray(1536)
        readFully(inp, s2)
        Log.i("RtmpStreamer", "RTMP Handshake complete")
    }

    private fun readFully(inp: InputStream, target: ByteArray) {
        var readTotal = 0
        while (readTotal < target.size) {
            val r = inp.read(target, readTotal, target.size - readTotal)
            if (r < 0) throw IllegalStateException("Stream closed during handshake")
            readTotal += r
        }
    }

    private fun sendConnect(out: OutputStream, appName: String, tcUrl: String) {
        val amf = ByteArrayOutputStream()
        RtmpPacketizer.writeAmfString(amf, "connect")
        RtmpPacketizer.writeAmfNumber(amf, 1.0) // Transaction ID
        val connObj = mapOf(
            "app" to appName,
            "flashVer" to "FMLE/3.0 (compatible; OBS Dok)",
            "tcUrl" to tcUrl,
            "fpad" to false,
            "capabilities" to 15.0,
            "audioCodecs" to 3191.0,
            "videoCodecs" to 252.0,
            "videoFunction" to 1.0
        )
        RtmpPacketizer.writeAmfObject(amf, connObj)

        val body = amf.toByteArray()
        val header = RtmpPacketizer.buildChunkHeader(3, 0, body.size, 0x14, 0)
        out.write(header)
        out.write(body)
        out.flush()
    }

    private fun sendCreateStream(out: OutputStream) {
        val amf = ByteArrayOutputStream()
        RtmpPacketizer.writeAmfString(amf, "createStream")
        RtmpPacketizer.writeAmfNumber(amf, 2.0)
        amf.write(0x05) // AMF0 Null marker

        val body = amf.toByteArray()
        val header = RtmpPacketizer.buildChunkHeader(3, 0, body.size, 0x14, 0)
        out.write(header)
        out.write(body)
        out.flush()
    }

    private fun sendPublish(out: OutputStream, streamKey: String) {
        val amf = ByteArrayOutputStream()
        RtmpPacketizer.writeAmfString(amf, "publish")
        RtmpPacketizer.writeAmfNumber(amf, 3.0)
        amf.write(0x05) // Null
        RtmpPacketizer.writeAmfString(amf, streamKey)
        RtmpPacketizer.writeAmfString(amf, "live")

        val body = amf.toByteArray()
        val header = RtmpPacketizer.buildChunkHeader(3, 0, body.size, 0x14, 1)
        out.write(header)
        out.write(body)
        out.flush()
    }

    private fun sendMetadata(out: OutputStream, width: Int, height: Int, fps: Int) {
        val amf = ByteArrayOutputStream()
        RtmpPacketizer.writeAmfString(amf, "@setDataFrame")
        RtmpPacketizer.writeAmfString(amf, "onMetaData")
        val meta = mapOf(
            "width" to width.toDouble(),
            "height" to height.toDouble(),
            "framerate" to fps.toDouble(),
            "videocodecid" to 7.0,
            "audiocodecid" to 10.0,
            "audiosamplerate" to 44100.0,
            "audiosamplesize" to 16.0,
            "stereo" to true
        )
        RtmpPacketizer.writeAmfObject(amf, meta)

        val body = amf.toByteArray()
        val header = RtmpPacketizer.buildChunkHeader(4, 0, body.size, 0x12, 1)
        out.write(header)
        out.write(body)
        out.flush()
    }

    private fun sendVideoSequenceHeader(out: OutputStream, spsPps: ByteArray) {
        val payload = RtmpPacketizer.buildVideoPayload(isKeyFrame = true, isHeader = true, cts = 0, nalu = spsPps)
        val header = RtmpPacketizer.buildChunkHeader(6, 0, payload.size, 0x09, 1)
        out.write(header)
        out.write(payload)
        out.flush()
    }

    private fun sendAudioSequenceHeader(out: OutputStream, config: ByteArray) {
        val payload = RtmpPacketizer.buildAudioPayload(isHeader = true, aacData = config)
        val header = RtmpPacketizer.buildChunkHeader(5, 0, payload.size, 0x08, 1)
        out.write(header)
        out.write(payload)
        out.flush()
    }

    private fun sendPacket(out: OutputStream, packet: StreamPacket) {
        val channelId = if (packet.isVideo) 6 else 5
        val typeId = if (packet.isVideo) 0x09 else 0x08
        val header = RtmpPacketizer.buildChunkHeader(channelId, packet.timestamp, packet.payload.size, typeId, 1)
        out.write(header)
        out.write(packet.payload)
        out.flush()
    }

    fun onVideoFormat(format: MediaFormat) {
        val csd0 = format.getByteBuffer("csd-0") // SPS
        val csd1 = format.getByteBuffer("csd-1") // PPS
        if (csd0 != null && csd1 != null) {
            val sps = ByteArray(csd0.remaining()).also { csd0.get(it); csd0.rewind() }
            val pps = ByteArray(csd1.remaining()).also { csd1.get(it); csd1.rewind() }

            val avcConfig = ByteArrayOutputStream()
            avcConfig.write(0x01) // configurationVersion
            avcConfig.write(sps[1].toInt()) // profile_indication
            avcConfig.write(sps[2].toInt()) // profile_compatibility
            avcConfig.write(sps[3].toInt()) // level_indication
            avcConfig.write(0xFF) // 6 bits reserved + 2 bits lengthSizeMinusOne (3)
            avcConfig.write(0xE1) // 3 bits reserved + 5 bits numOfSPS (1)
            avcConfig.write((sps.size shr 8) and 0xFF)
            avcConfig.write(sps.size and 0xFF)
            avcConfig.write(sps)
            avcConfig.write(0x01) // numOfPPS (1)
            avcConfig.write((pps.size shr 8) and 0xFF)
            avcConfig.write(pps.size and 0xFF)
            avcConfig.write(pps)

            videoSpsPps = avcConfig.toByteArray()
        }
    }

    fun onAudioFormat(format: MediaFormat) {
        val csd0 = format.getByteBuffer("csd-0")
        if (csd0 != null) {
            val conf = ByteArray(csd0.remaining())
            csd0.get(conf)
            csd0.rewind()
            audioSpecificConfig = conf
        }
    }

    fun sendVideoFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!isStreaming.get()) return

        val isKey = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        val timestamp = (info.presentationTimeUs / 1000).toInt()

        val nalu = ByteArray(info.size)
        buffer.get(nalu)

        val payload = RtmpPacketizer.buildVideoPayload(isKeyFrame = isKey, isHeader = false, cts = 0, nalu = nalu)
        val packet = StreamPacket(isVideo = true, isKeyFrame = isKey, timestamp = timestamp, payload = payload)

        if (!packetQueue.offer(packet)) {
            droppedFrames++
        }
    }

    fun sendAudioFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (!isStreaming.get()) return

        val timestamp = (info.presentationTimeUs / 1000).toInt()
        val aac = ByteArray(info.size)
        buffer.get(aac)

        val payload = RtmpPacketizer.buildAudioPayload(isHeader = false, aacData = aac)
        val packet = StreamPacket(isVideo = false, isKeyFrame = false, timestamp = timestamp, payload = payload)

        if (!packetQueue.offer(packet)) {
            droppedFrames++
        }
    }

    fun stopStreaming() {
        if (!isStreaming.compareAndSet(true, false)) return
        _streamState.value = StreamState.STOPPING

        scope.launch {
            runCatching {
                outStream?.close()
                inStream?.close()
                socket?.close()
            }
            packetQueue.clear()
            _streamState.value = StreamState.OFFLINE
            currentBitrateKbps = 0
            Log.i("RtmpStreamer", "Streaming stopped cleanly")
        }
    }
}
