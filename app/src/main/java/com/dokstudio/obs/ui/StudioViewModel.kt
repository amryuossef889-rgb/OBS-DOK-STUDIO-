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
            if (scenes.first().isEmpty()) {
                application.repository.save(
                    SceneEntity(
                        UUID.randomUUID().toString(),
                        "Main Scene",
                        0,
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun setQuality(profile: QualityProfile) {
        selectedWidth = profile.width
        selectedHeight = profile.height
        selectedFps = profile.fps
        selectedBitrate = profile.bitrate
    }

    fun addScene() {
        viewModelScope.launch {
            val n = scenes.value.size
            application.repository.save(
                SceneEntity(
                    UUID.randomUUID().toString(),
                    "Scene " + (n + 1),
                    n,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                ),
            )
        }
    }

    fun addSource(scene: String, type: String) {
        viewModelScope.launch {
            application.repository.save(
                SourceEntity(
                    UUID.randomUUID().toString(),
                    scene,
                    type,
                    type.replaceFirstChar { it.uppercase() },
                    zIndex = System.currentTimeMillis().toInt(),
                ),
            )
        }
    }

    fun saveRecording(recording: RecordingEntity) {
        viewModelScope.launch { application.repository.save(recording) }
    }
}
