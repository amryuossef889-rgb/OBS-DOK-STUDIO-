package com.example.data.repository

import com.example.core.model.Project
import com.example.core.model.RecordingItem
import com.example.core.model.Scene
import com.example.core.model.SourceRef
import com.example.core.model.SourceType
import com.example.core.model.StreamProfile
import com.example.core.model.Transform
import com.example.core.model.TransitionConfig
import com.example.data.database.ObsDatabase
import com.example.data.database.entity.ProjectEntity
import com.example.data.database.entity.RecordingEntity
import com.example.data.database.entity.SceneEntity
import com.example.data.database.entity.SourceEntity
import com.example.data.database.entity.StreamProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class StudioRepository(private val database: ObsDatabase) {

    private val projectDao = database.projectDao()
    private val sceneDao = database.sceneDao()
    private val sourceDao = database.sourceDao()
    private val recordingDao = database.recordingDao()
    private val streamProfileDao = database.streamProfileDao()

    fun getProject(projectId: String): Flow<Project?> {
        return projectDao.getProjectById(projectId).map { entity ->
            entity?.let {
                Project(
                    id = it.id,
                    name = it.name,
                    activeSceneId = it.activeSceneId,
                    createdAt = it.createdAt
                )
            }
        }
    }

    suspend fun saveProject(project: Project) {
        projectDao.insertProject(
            ProjectEntity(
                id = project.id,
                name = project.name,
                activeSceneId = project.activeSceneId,
                createdAt = project.createdAt
            )
        )
    }

    fun getScenes(projectId: String): Flow<List<Scene>> {
        return sceneDao.getScenesForProject(projectId).map { list ->
            list.map { entity ->
                Scene(
                    id = entity.id,
                    projectId = entity.projectId,
                    name = entity.name,
                    transition = TransitionConfig(
                        type = entity.transitionType,
                        durationMs = entity.transitionDurationMs
                    )
                )
            }
        }
    }

    suspend fun saveScene(scene: Scene, sortOrder: Int = 0) {
        sceneDao.insertScene(
            SceneEntity(
                id = scene.id,
                projectId = scene.projectId,
                name = scene.name,
                transitionType = scene.transition.type,
                transitionDurationMs = scene.transition.durationMs,
                sortOrder = sortOrder
            )
        )
    }

    suspend fun deleteScene(sceneId: String) {
        sourceDao.deleteSourcesForScene(sceneId)
        sceneDao.deleteSceneById(sceneId)
    }

    fun getSources(sceneId: String): Flow<List<SourceRef>> {
        return sourceDao.getSourcesForScene(sceneId).map { list ->
            list.map { entity ->
                val type = runCatching { SourceType.valueOf(entity.type) }.getOrDefault(SourceType.COLOR)
                SourceRef(
                    id = entity.id,
                    sceneId = entity.sceneId,
                    name = entity.name,
                    type = type,
                    visible = entity.visible,
                    zIndex = entity.zIndex,
                    transform = Transform(
                        x = entity.posX,
                        y = entity.posY,
                        scaleX = entity.scaleX,
                        scaleY = entity.scaleY,
                        rotation = entity.rotation,
                        opacity = entity.opacity,
                        cropLeft = entity.cropLeft,
                        cropTop = entity.cropTop,
                        cropRight = entity.cropRight,
                        cropBottom = entity.cropBottom
                    ),
                    audioEnabled = entity.audioEnabled
                )
            }
        }
    }

    suspend fun saveSource(source: SourceRef) {
        sourceDao.insertSource(
            SourceEntity(
                id = source.id,
                sceneId = source.sceneId,
                name = source.name,
                type = source.type.name,
                visible = source.visible,
                zIndex = source.zIndex,
                posX = source.transform.x,
                posY = source.transform.y,
                scaleX = source.transform.scaleX,
                scaleY = source.transform.scaleY,
                rotation = source.transform.rotation,
                opacity = source.transform.opacity,
                cropLeft = source.transform.cropLeft,
                cropTop = source.transform.cropTop,
                cropRight = source.transform.cropRight,
                cropBottom = source.transform.cropBottom,
                audioEnabled = source.audioEnabled
            )
        )
    }

    suspend fun deleteSource(sourceId: String) {
        sourceDao.deleteSourceById(sourceId)
    }

    fun getAllRecordings(): Flow<List<RecordingItem>> {
        return recordingDao.getAllRecordings().map { list ->
            list.map {
                RecordingItem(
                    id = it.id,
                    title = it.title,
                    filePath = it.filePath,
                    durationMs = it.durationMs,
                    sizeBytes = it.sizeBytes,
                    width = it.width,
                    height = it.height,
                    timestamp = it.timestamp
                )
            }
        }
    }

    suspend fun addRecording(item: RecordingItem) {
        recordingDao.insertRecording(
            RecordingEntity(
                id = item.id,
                title = item.title,
                filePath = item.filePath,
                durationMs = item.durationMs,
                sizeBytes = item.sizeBytes,
                width = item.width,
                height = it.height,
                timestamp = item.timestamp
            )
        )
    }

    suspend fun deleteRecording(id: String, filePath: String) {
        runCatching {
            val file = File(filePath)
            if (file.exists()) file.delete()
        }
        recordingDao.deleteRecording(id)
    }

    fun getStreamProfile(): Flow<StreamProfile?> {
        return streamProfileDao.getActiveProfile().map { entity ->
            entity?.let {
                StreamProfile(
                    id = it.id,
                    title = it.title,
                    serverUrl = it.serverUrl,
                    streamKey = it.streamKey,
                    authRequired = it.authRequired,
                    username = it.username,
                    password = it.passwordEncrypted
                )
            }
        }
    }

    suspend fun saveStreamProfile(profile: StreamProfile) {
        streamProfileDao.saveProfile(
            StreamProfileEntity(
                id = profile.id,
                title = profile.title,
                serverUrl = profile.serverUrl,
                streamKey = profile.streamKey,
                authRequired = profile.authRequired,
                username = profile.username,
                passwordEncrypted = profile.password
            )
        )
    }
}
