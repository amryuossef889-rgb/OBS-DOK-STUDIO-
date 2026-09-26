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
                application.repository.save(SourceEntity(UUID.randomUUID().toString(), sceneId, "SCREEN", "Screen", zIndex = 0))
                selectedScene.value = sceneId
            } else {
                selectedScene.value = current.first().id
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
            application.repository.save(SourceEntity(UUID.randomUUID().toString(), sceneId, "SCREEN", "Screen", zIndex = 0))
        }
    }

    fun addSource(scene: String, type: String) {
        viewModelScope.launch {
            val z = application.repository.sources(scene).first().maxOfOrNull { it.zIndex }?.plus(1) ?: 0
            application.repository.save(
                SourceEntity(
                    UUID.randomUUID().toString(),
                    scene,
                    type,
                    type.replaceFirstChar { it.uppercase() },
                    zIndex = z,
                ),
            )
        }
    }

    fun saveRecording(recording: RecordingEntity) {
        viewModelScope.launch { application.repository.save(recording) }
    }
}
