package com.dokstudio.obs.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dokstudio.obs.DokStudioApplication
import com.dokstudio.obs.data.*
import com.dokstudio.obs.engine.StudioEngine
import com.dokstudio.obs.recording.QualityProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as DokStudioApplication
    val engine = StudioEngine(app)

    val scenes = application.repository.sceneFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordings = application.repository.recordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedScene = MutableStateFlow<String?>(null)
    val selectedSources = selectedScene.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else application.repository.sources(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedWidth: Int = 1280
        private set
    var selectedHeight: Int = 720
        private set
    var selectedFps: Int = 30
        private set
    var selectedBitrate: Int = 6_000_000
        private set

    init {
        viewModelScope.launch {
            val current = scenes.first()
            if (current.isEmpty()) {
                val sceneId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                application.repository.save(SceneEntity(sceneId, "Main Scene", 0, now, now))
                application.repository.save(SourceEntity(UUID.randomUUID().toString(), sceneId, "SCREEN", "Display Capture", zIndex = 0))
                application.repository.save(SourceEntity(UUID.randomUUID().toString(), sceneId, "MICROPHONE", "Microphone", zIndex = 1))
                selectedScene.value = sceneId
            } else {
                selectedScene.value = current.first().id
                val existingSources = application.repository.sources(current.first().id).first()
                if (existingSources.isEmpty()) {
                    application.repository.save(SourceEntity(UUID.randomUUID().toString(), current.first().id, "SCREEN", "Display Capture", zIndex = 0))
                    application.repository.save(SourceEntity(UUID.randomUUID().toString(), current.first().id, "MICROPHONE", "Microphone", zIndex = 1))
                } else if (existingSources.none { it.type.equals("MICROPHONE", true) }) {
                    val z = existingSources.maxOfOrNull { it.zIndex }?.plus(1) ?: 0
                    application.repository.save(SourceEntity(UUID.randomUUID().toString(), current.first().id, "MICROPHONE", "Microphone", zIndex = z))
                }
            }
        }
    }

    fun setQuality(profile: QualityProfile) {
        selectedWidth = profile.width
        selectedHeight = profile.height
        selectedFps = profile.fps
        selectedBitrate = profile.bitrate
    }

    fun selectScene(id: String) {
        selectedScene.value = id
    }

    fun addScene() {
        viewModelScope.launch {
            val n = scenes.value.size
            val sceneId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            application.repository.save(SceneEntity(sceneId, "Scene " + (n + 1), n, now, now))
            // Every newly-created scene owns its own independent source set.
            application.repository.save(SourceEntity(UUID.randomUUID().toString(), sceneId, "SCREEN", "Display Capture", zIndex = 0))
            application.repository.save(SourceEntity(UUID.randomUUID().toString(), sceneId, "CAMERA", "Camera", zIndex = 1))
            application.repository.save(SourceEntity(UUID.randomUUID().toString(), sceneId, "MICROPHONE", "Microphone", zIndex = 2))
            selectedScene.value = sceneId
        }
    }

    fun addSource(scene: String, type: String) {
        viewModelScope.launch {
            val current = application.repository.sources(scene).first()
            if (current.any { it.type.equals(type, true) }) return@launch
            val z = current.maxOfOrNull { it.zIndex }?.plus(1) ?: 0
            val displayName = when (type.uppercase()) {
                "SCREEN" -> "Display Capture"
                "CAMERA" -> "Camera"
                "MICROPHONE" -> "Microphone"
                else -> type.replaceFirstChar { it.uppercase() }
            }
            application.repository.save(
                SourceEntity(
                    UUID.randomUUID().toString(),
                    scene,
                    type.uppercase(),
                    displayName,
                    zIndex = z,
                ),
            )
        }
    }

    fun saveRecording(recording: RecordingEntity) {
        viewModelScope.launch { application.repository.save(recording) }
    }
}
