package com.dokstudio.obs.streaming

import android.content.Context
import com.dokstudio.obs.data.StreamProfileEntity
import com.dokstudio.obs.data.AppDatabase
import kotlinx.coroutines.flow.Flow

class StreamProfileStore(
    context: Context,
    private val database: AppDatabase,
) {
    private val cipher = StreamKeyCipher(context)

    fun observe(): Flow<List<StreamProfileEntity>> =
        database.streamProfileDao().observe()

    suspend fun save(
        id: String,
        name: String,
        serverUrl: String,
        streamKey: String,
        bitrate: Int,
        fps: Int,
        resolution: String,
    ) {
        require(serverUrl.startsWith("rtmp://") || serverUrl.startsWith("rtmps://")) {
            "Only RTMP and RTMPS server URLs are supported"
        }
        database.streamProfileDao().upsert(
            StreamProfileEntity(
                id = id,
                name = name,
                serverUrl = serverUrl,
                encryptedStreamKey = cipher.encrypt(streamKey),
                bitrate = bitrate,
                fps = fps,
                resolution = resolution,
            ),
        )
    }

    fun decryptStreamKey(profile: StreamProfileEntity): String =
        cipher.decrypt(profile.encryptedStreamKey)
}
