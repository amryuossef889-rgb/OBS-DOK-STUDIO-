package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activeSceneId: String,
    val createdAt: Long
)

@Entity(tableName = "scenes")
data class SceneEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val transitionType: String,
    val transitionDurationMs: Long,
    val sortOrder: Int = 0
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val sceneId: String,
    val name: String,
    val type: String,
    val visible: Boolean,
    val zIndex: Int,
    val posX: Float,
    val posY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val opacity: Float,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val propertiesJson: String = "{}",
    val audioEnabled: Boolean = false
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val timestamp: Long
)

@Entity(tableName = "stream_profiles")
data class StreamProfileEntity(
    @PrimaryKey val id: String,
    val title: String,
    val serverUrl: String,
    val streamKey: String,
    val authRequired: Boolean,
    val username: String,
    val passwordEncrypted: String
)
