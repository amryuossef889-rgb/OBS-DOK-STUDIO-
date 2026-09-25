package com.example.core.streaming

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

object RtmpPacketizer {

    fun writeAmfString(out: ByteArrayOutputStream, str: String) {
        out.write(0x02) // AMF0 string marker
        val bytes = str.toByteArray(Charsets.UTF_8)
        out.write((bytes.size shr 8) and 0xFF)
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    fun writeAmfNumber(out: ByteArrayOutputStream, num: Double) {
        out.write(0x00) // AMF0 number marker
        val bits = java.lang.Double.doubleToRawLongBits(num)
        for (i in 7 downTo 0) {
            out.write(((bits shr (i * 8)) and 0xFF).toInt())
        }
    }

    fun writeAmfBoolean(out: ByteArrayOutputStream, value: Boolean) {
        out.write(0x01) // AMF0 boolean marker
        out.write(if (value) 1 else 0)
    }

    fun writeAmfObject(out: ByteArrayOutputStream, properties: Map<String, Any>) {
        out.write(0x03) // AMF0 object marker
        for ((key, value) in properties) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            out.write((keyBytes.size shr 8) and 0xFF)
            out.write(keyBytes.size and 0xFF)
            out.write(keyBytes)
            when (value) {
                is String -> writeAmfString(out, value)
                is Number -> writeAmfNumber(out, value.toDouble())
                is Boolean -> writeAmfBoolean(out, value)
            }
        }
        // Object end marker: 0x00, 0x00, 0x09
        out.write(0x00)
        out.write(0x00)
        out.write(0x09)
    }

    fun buildChunkHeader(
        channelId: Int,
        timestamp: Int,
        messageLength: Int,
        messageTypeId: Int,
        streamId: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // Format 0 (11 bytes header)
        out.write((0 shl 6) or (channelId and 0x3F))

        // 3 bytes timestamp
        out.write((timestamp shr 16) and 0xFF)
        out.write((timestamp shr 8) and 0xFF)
        out.write(timestamp and 0xFF)

        // 3 bytes message length
        out.write((messageLength shr 16) and 0xFF)
        out.write((messageLength shr 8) and 0xFF)
        out.write(messageLength and 0xFF)

        // 1 byte message type
        out.write(messageTypeId and 0xFF)

        // 4 bytes message stream ID (little-endian)
        out.write(streamId and 0xFF)
        out.write((streamId shr 8) and 0xFF)
        out.write((streamId shr 16) and 0xFF)
        out.write((streamId shr 24) and 0xFF)

        return out.toByteArray()
    }

    fun buildVideoPayload(
        isKeyFrame: Boolean,
        isHeader: Boolean,
        cts: Int,
        nalu: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // Frame Type (1 = keyframe, 2 = interframe) + Codec ID (7 = AVC)
        val frameTypeAndCodec = ((if (isKeyFrame) 1 else 2) shl 4) or 7
        out.write(frameTypeAndCodec)

        // AVCPacketType (0 = sequence header, 1 = NALU)
        out.write(if (isHeader) 0 else 1)

        // Composition Time (3 bytes)
        out.write((cts shr 16) and 0xFF)
        out.write((cts shr 8) and 0xFF)
        out.write(cts and 0xFF)

        out.write(nalu)
        return out.toByteArray()
    }

    fun buildAudioPayload(
        isHeader: Boolean,
        aacData: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // Format (10 = AAC) | 44kHz (3 << 2) | 16-bit (1 << 1) | Stereo (1) = 0xAF
        out.write(0xAF)
        // AACPacketType (0 = sequence header, 1 = raw AAC frame)
        out.write(if (isHeader) 0 else 1)
        out.write(aacData)
        return out.toByteArray()
    }
}
