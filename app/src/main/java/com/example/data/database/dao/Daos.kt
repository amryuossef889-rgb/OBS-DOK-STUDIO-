package com.example.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entity.ProjectEntity
import com.example.data.database.entity.RecordingEntity
import com.example.data.database.entity.SceneEntity
import com.example.data.database.entity.SourceEntity
import com.example.data.database.entity.StreamProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProjectById(id: String): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)
}

@Dao
interface SceneDao {
    @Query("SELECT * FROM scenes WHERE projectId = :projectId ORDER BY sortOrder ASC")
    fun getScenesForProject(projectId: String): Flow<List<SceneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScene(scene: SceneEntity)

    @Update
    suspend fun updateScene(scene: SceneEntity)

    @Query("DELETE FROM scenes WHERE id = :sceneId")
    suspend fun deleteSceneById(sceneId: String)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources WHERE sceneId = :sceneId ORDER BY zIndex ASC")
    fun getSourcesForScene(sceneId: String): Flow<List<SourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: SourceEntity)

    @Update
    suspend fun updateSource(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :sourceId")
    suspend fun deleteSourceById(sourceId: String)

    @Query("DELETE FROM sources WHERE sceneId = :sceneId")
    suspend fun deleteSourcesForScene(sceneId: String)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :recordingId")
    suspend fun deleteRecording(recordingId: String)
}

@Dao
interface StreamProfileDao {
    @Query("SELECT * FROM stream_profiles LIMIT 1")
    fun getActiveProfile(): Flow<StreamProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: StreamProfileEntity)
}
