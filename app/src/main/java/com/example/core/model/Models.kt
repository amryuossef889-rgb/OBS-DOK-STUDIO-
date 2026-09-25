package com.example.core.model

import java.util.UUID

enum class SourceType {
    SCREEN,
    CAMERA,
    MICROPHONE,
    DEVICE_AUDIO,
    IMAGE,
    TEXT,
    COLOR
}

data class Transform(
    val x: Float = 0f,         // Offset X in normalized [0..1] or canvas dp
    val y: Float = 0f,         // Offset Y in normalized [0..1]
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,  // in degrees
    val opacity: Float = 1f,   // [0..1]
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f
)

data class SourceRef(
    val id: String = UUID.randomUUID().toString(),
    val sceneId: String,
    val name: String,
    val type: SourceType,
    val visible: Boolean = true,
    val zIndex: Int = 0,
    val transform: Transform = Transform(),
    val properties: Map<String, String> = emptyMap(),
    val audioEnabled: Boolean = (type == SourceType.MICROPHONE || type == SourceType.DEVICE_AUDIO)
)

data class TransitionConfig(
    val type: String = "CUT", // CUT, FADE
    val durationMs: Long = 300L
)

data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "default_project",
    val name: String,
    val sources: List<SourceRef> = emptyList(),
    val transition: TransitionConfig = TransitionConfig()
)

data class Project(
    val id: String = "default_project",
    val name: String = "Main Studio Project",
    val activeSceneId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
