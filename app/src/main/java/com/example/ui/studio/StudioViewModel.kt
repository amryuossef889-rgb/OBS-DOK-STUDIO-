package com.example.ui.studio

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.ObsDokApplication
import com.example.core.capability.CapabilityDiscovery
import com.example.core.engine.StudioEngine
import com.example.core.model.AudioChannelConfig
import com.example.core.model.DeviceCapability
import com.example.core.model.Project
import com.example.core.model.QualitySettings
import com.example.core.model.QualityTier
import com.example.core.model.RecordingItem
import com.example.core.model.Scene
import com.example.core.model.SourceRef
import com.example.core.model.SourceType
import com.example.core.model.StreamProfile
import com.example.core.model.StreamState
import com.example.core.model.StudioState
import com.example.core.model.Transform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObsDokApplication
    val repository = app.repository

    val engine = StudioEngine(application, repository)

    val studioState: StateFlow<StudioState> = engine.studioState
    val streamState: StateFlow<StreamState> = engine.streamState
    val qualitySettings: StateFlow<QualitySettings> = engine.qualitySettings
    val deviceCapability: DeviceCapability = engine.deviceCapability
    val diagnostics = engine.diagnostics

    val audioChannels: StateFlow<List<AudioChannelConfig>> = engine.audioMixer.channelsState

    private val _scenes = MutableStateFlow<List<Scene>>(emptyList())
    val scenes: StateFlow<List<Scene>> = _scenes.asStateFlow()

    private val _activeScene = MutableStateFlow<Scene?>(null)
    val activeScene: StateFlow<Scene?> = _activeScene.asStateFlow()

    private val _selectedSource = MutableStateFlow<SourceRef?>(null)
    val selectedSource: StateFlow<SourceRef?> = _selectedSource.asStateFlow()

    val recordings: StateFlow<List<RecordingItem>> = repository.getAllRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streamProfile: StateFlow<StreamProfile?> = repository.getStreamProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadInitialProject()
    }

    private fun loadInitialProject() {
        viewModelScope.launch {
            repository.getScenes("default_project").collect { list ->
                if (list.isEmpty()) {
                    createDefaultProject()
                } else {
                    _scenes.value = list
                    if (_activeScene.value == null) {
                        selectScene(list.first())
                    }
                }
            }
        }
    }

    private suspend fun createDefaultProject() {
        val proj = Project(id = "default_project", name = "OBS Dok Main Project")
        repository.saveProject(proj)

        val defaultScene = Scene(
            id = "scene_main",
            projectId = "default_project",
            name = "Live Gameplay & Cam"
        )
        repository.saveScene(defaultScene, 0)

        // Default camera source (PiP style bottom right)
        val cameraSource = SourceRef(
            id = "source_cam_1",
            sceneId = "scene_main",
            name = "Facecam",
            type = SourceType.CAMERA,
            zIndex = 2,
            transform = Transform(x = 0.75f, y = 0.75f, scaleX = 0.4f, scaleY = 0.4f)
        )
        // Default screen source (fullscreen)
        val screenSource = SourceRef(
            id = "source_screen_1",
            sceneId = "scene_main",
            name = "Display Capture",
            type = SourceType.SCREEN,
            zIndex = 1,
            transform = Transform(x = 0.5f, y = 0.5f, scaleX = 1.0f, scaleY = 1.0f)
        )
        // Text overlay source
        val textSource = SourceRef(
            id = "source_text_1",
            sceneId = "scene_main",
            name = "OBS Dok Studio",
            type = SourceType.TEXT,
            zIndex = 3,
            transform = Transform(x = 0.25f, y = 0.1f, scaleX = 0.45f, scaleY = 0.15f)
        )

        repository.saveSource(screenSource)
        repository.saveSource(cameraSource)
        repository.saveSource(textSource)

        // Default Stream Profile
        repository.saveStreamProfile(
            StreamProfile(
                id = "default_stream",
                title = "Twitch / YouTube RTMP",
                serverUrl = "rtmp://live.twitch.tv/app/",
                streamKey = ""
            )
        )
    }

    fun initializeEngine(lifecycleOwner: LifecycleOwner) {
        engine.initializeStudio(lifecycleOwner)
        _activeScene.value?.let { engine.setScene(it) }
    }

    fun selectScene(scene: Scene) {
        _activeScene.value = scene
        engine.setScene(scene)
        viewModelScope.launch {
            repository.getSources(scene.id).collect { sources ->
                val updatedScene = scene.copy(sources = sources)
                _activeScene.value = updatedScene
                engine.setScene(updatedScene)
            }
        }
    }

    fun createScene(name: String) {
        val newScene = Scene(name = name)
        viewModelScope.launch {
            repository.saveScene(newScene, _scenes.value.size)
            selectScene(newScene)
        }
    }

    fun deleteScene(scene: Scene) {
        if (_scenes.value.size <= 1) return
        viewModelScope.launch {
            repository.deleteScene(scene.id)
            val remaining = _scenes.value.filter { it.id != scene.id }
            if (remaining.isNotEmpty()) {
                selectScene(remaining.first())
            }
        }
    }

    fun addSource(sceneId: String, name: String, type: SourceType) {
        val count = _activeScene.value?.sources?.size ?: 0
        val transform = when (type) {
            SourceType.CAMERA -> Transform(x = 0.75f, y = 0.75f, scaleX = 0.4f, scaleY = 0.4f)
            SourceType.TEXT -> Transform(x = 0.5f, y = 0.15f, scaleX = 0.5f, scaleY = 0.15f)
            SourceType.COLOR -> Transform(x = 0.5f, y = 0.5f, scaleX = 0.8f, scaleY = 0.8f)
            else -> Transform(x = 0.5f, y = 0.5f, scaleX = 1f, scaleY = 1f)
        }
        val source = SourceRef(
            sceneId = sceneId,
            name = name,
            type = type,
            zIndex = count + 1,
            transform = transform
        )
        viewModelScope.launch {
            repository.saveSource(source)
            selectSource(source)
        }
    }

    fun selectSource(source: SourceRef?) {
        _selectedSource.value = source
    }

    fun updateSourceTransform(sourceId: String, update: (Transform) -> Transform) {
        val currScene = _activeScene.value ?: return
        val src = currScene.sources.find { it.id == sourceId } ?: return
        val newTransform = update(src.transform)
        val updatedSrc = src.copy(transform = newTransform)
        viewModelScope.launch {
            repository.saveSource(updatedSrc)
        }
    }

    fun toggleSourceVisibility(source: SourceRef) {
        val updated = source.copy(visible = !source.visible)
        viewModelScope.launch {
            repository.saveSource(updated)
        }
    }

    fun deleteSource(sourceId: String) {
        viewModelScope.launch {
            repository.deleteSource(sourceId)
            if (_selectedSource.value?.id == sourceId) {
                _selectedSource.value = null
            }
        }
    }

    fun setQualityTier(tier: QualityTier) {
        val newSettings = CapabilityDiscovery.getTierSettings(tier, deviceCapability)
        engine.updateQualitySettings(newSettings)
    }

    fun setAutoQuality() {
        val newSettings = CapabilityDiscovery.getAutoQuality(deviceCapability)
        engine.updateQualitySettings(newSettings)
    }

    fun setManualQuality(width: Int, height: Int, fps: Int, bitrateKbps: Int, codecMime: String, keyframeSec: Int) {
        val newSettings = QualitySettings(
            isAuto = false,
            selectedTier = QualityTier.BALANCED,
            width = width,
            height = height,
            fps = fps,
            bitrateKbps = bitrateKbps,
            codecMime = codecMime,
            keyframeIntervalSeconds = keyframeSec
        )
        engine.updateQualitySettings(newSettings)
    }

    fun toggleRecording() {
        if (studioState.value == StudioState.RECORDING) {
            engine.stopRecording()
        } else {
            engine.startRecording()
        }
    }

    fun toggleLive() {
        if (streamState.value == StreamState.LIVE || streamState.value == StreamState.CONNECTING) {
            engine.stopLive()
        } else {
            val prof = streamProfile.value ?: StreamProfile()
            engine.startLive(prof)
        }
    }

    fun saveStreamProfile(serverUrl: String, streamKey: String) {
        val current = streamProfile.value ?: StreamProfile()
        val updated = current.copy(serverUrl = serverUrl, streamKey = streamKey)
        viewModelScope.launch {
            repository.saveStreamProfile(updated)
        }
    }

    fun handleScreenCaptureResult(resultCode: Int, data: Intent) {
        engine.handleScreenCaptureResult(resultCode, data)
    }

    fun switchCamera() {
        engine.switchCamera()
    }

    fun deleteRecording(recording: RecordingItem) {
        viewModelScope.launch {
            repository.deleteRecording(recording.id, recording.filePath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}
