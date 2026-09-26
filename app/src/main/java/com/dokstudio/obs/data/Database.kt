package com.dokstudio.obs.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "sources", indices = [Index("sceneId")])
data class SourceEntity(
    @PrimaryKey val id: String,
    val sceneId: String,
    val type: String,
    val name: String,
    val visible: Boolean = true,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val zIndex: Int = 0,
    val configurationJson: String = "{}",
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val fileUri: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val createdAt: Long,
    val fileSize: Long,
)

@Entity(tableName = "stream_profiles")
data class StreamProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serverUrl: String,
    val encryptedStreamKey: String,
    val bitrate: Int,
    val fps: Int,
    val resolution: String,
)

@Dao
interface SceneDao {
    @Query("SELECT * FROM scenes ORDER BY orderIndex")
    fun observe(): Flow<List<SceneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(x: SceneEntity)

    @Delete
    suspend fun delete(x: SceneEntity)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE sceneId=:sceneId ORDER BY zIndex")
    fun observe(sceneId: String): Flow<List<SourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(x: SourceEntity)

    @Delete
    suspend fun delete(x: SourceEntity)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observe(): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(x: RecordingEntity)

    @Delete
    suspend fun delete(x: RecordingEntity)
}

@Database(
    entities = [SceneEntity::class, SourceEntity::class, RecordingEntity::class, StreamProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sceneDao(): SceneDao
    abstract fun sourceDao(): SourceDao
    abstract fun recordingDao(): RecordingDao
}

class SceneRepository(
    private val scenes: SceneDao,
    private val sources: SourceDao,
    private val recordings: RecordingDao,
) {
    val sceneFlow = scenes.observe()

    suspend fun save(x: SceneEntity) = scenes.upsert(x)
    suspend fun delete(x: SceneEntity) = scenes.delete(x)

    fun sources(id: String) = sources.observe(id)
    suspend fun save(x: SourceEntity) = sources.upsert(x)
    suspend fun delete(x: SourceEntity) = sources.delete(x)

    val recordings = recordings.observe()
    suspend fun save(x: RecordingEntity) = recordings.upsert(x)
    suspend fun delete(x: RecordingEntity) = recordings.delete(x)
}
