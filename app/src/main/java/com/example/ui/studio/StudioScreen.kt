package com.example.ui.studio

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.model.SourceRef
import com.example.core.model.SourceType
import com.example.core.model.StreamState
import com.example.core.model.StudioState
import com.example.ui.components.AddSourceDialog
import com.example.ui.components.PreviewCanvas
import com.example.ui.components.QualityDialog
import com.example.ui.components.SourcePropertiesDialog
import com.example.ui.components.StudioTopBar
import com.example.ui.mixer.MixerBottomSheet
import com.example.ui.recordings.RecordingsScreen
import com.example.ui.scenes.ScenesBottomSheet
import com.example.ui.settings.SettingsScreen
import com.example.ui.sources.SourcesBottomSheet

enum class StudioSheet {
    NONE,
    SCENES,
    SOURCES,
    MIXER
}

enum class StudioDestination {
    STUDIO,
    RECORDINGS,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val studioState by viewModel.studioState.collectAsState()
    val streamState by viewModel.streamState.collectAsState()
    val qualitySettings by viewModel.qualitySettings.collectAsState()
    val metrics by viewModel.diagnostics.metrics.collectAsState()
    val activeScene by viewModel.activeScene.collectAsState()
    val scenes by viewModel.scenes.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val audioChannels by viewModel.audioChannels.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val streamProfile by viewModel.streamProfile.collectAsState()

    var currentScreen by remember { mutableStateOf(StudioDestination.STUDIO) }
    var activeSheet by remember { mutableStateOf(StudioSheet.NONE) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var propertySource by remember { mutableStateOf<SourceRef?>(null) }
    var showGuides by remember { mutableStateOf(true) }

    // Screen Capture Intent Launcher
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.handleScreenCaptureResult(result.resultCode, result.data!!)
        }
    }

    // Camera & Audio Runtime Permissions Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA] ?: false
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted || audioGranted) {
            viewModel.initializeEngine(lifecycleOwner)
        }
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    BackHandler(enabled = currentScreen != StudioDestination.STUDIO || activeSheet != StudioSheet.NONE) {
        if (activeSheet != StudioSheet.NONE) {
            activeSheet = StudioSheet.NONE
        } else if (currentScreen != StudioDestination.STUDIO) {
            currentScreen = StudioDestination.STUDIO
        }
    }

    when (currentScreen) {
        StudioDestination.RECORDINGS -> {
            RecordingsScreen(
                recordings = recordings,
                onDeleteRecording = { viewModel.deleteRecording(it) },
                onBack = { currentScreen = StudioDestination.STUDIO }
            )
        }
        StudioDestination.SETTINGS -> {
            SettingsScreen(
                streamProfile = streamProfile,
                capability = viewModel.deviceCapability,
                diagnostics = viewModel.diagnostics,
                onSaveStreamProfile = { url, key -> viewModel.saveStreamProfile(url, key) },
                onBack = { currentScreen = StudioDestination.STUDIO }
            )
        }
        StudioDestination.STUDIO -> {
            Scaffold(
                topBar = {
                    StudioTopBar(
                        sceneName = activeScene?.name ?: "Studio",
                        studioState = studioState,
                        streamState = streamState,
                        qualitySettings = qualitySettings,
                        metrics = metrics,
                        onQualityClick = { showQualityDialog = true },
                        onSwitchCamera = { viewModel.switchCamera() },
                        onSettingsClick = { currentScreen = StudioDestination.SETTINGS }
                    )
                },
                containerColor = Color(0xFF090D14)
            ) { padding ->
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Preview Canvas in Center
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PreviewCanvas(
                            engine = viewModel.engine,
                            selectedSource = selectedSource,
                            onTransformChanged = { newTransform ->
                                selectedSource?.let { src ->
                                    viewModel.updateSourceTransform(src.id) { newTransform }
                                }
                            },
                            showGuides = showGuides
                        )
                    }

                    // Source selection chips strip above dock
                    val currentSources = activeScene?.sources ?: emptyList()
                    if (currentSources.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentSources) { src ->
                                val isSel = src.id == selectedSource?.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) Color(0xFF1E88E5) else Color(0xFF1A2438))
                                        .clickable {
                                            viewModel.selectSource(if (isSel) null else src)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("source_chip_${src.id}")
                                ) {
                                    Text(
                                        text = src.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color.White else Color(0xFFB0BEC5)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Control Dock (Section 2.1)
                    Surface(
                        color = Color(0xFF0F141F),
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DockItem(
                                icon = Icons.Default.Layers,
                                label = "Scenes",
                                isActive = activeSheet == StudioSheet.SCENES,
                                onClick = { activeSheet = StudioSheet.SCENES },
                                testTag = "dock_scenes"
                            )

                            DockItem(
                                icon = Icons.Default.Widgets,
                                label = "Sources",
                                isActive = activeSheet == StudioSheet.SOURCES,
                                onClick = { activeSheet = StudioSheet.SOURCES },
                                testTag = "dock_sources"
                            )

                            DockItem(
                                icon = Icons.Default.GraphicEq,
                                label = "Mixer",
                                isActive = activeSheet == StudioSheet.MIXER,
                                onClick = { activeSheet = StudioSheet.MIXER },
                                testTag = "dock_mixer"
                            )

                            // Action: Record
                            val isRecording = studioState == StudioState.RECORDING
                            DockItem(
                                icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                label = if (isRecording) "Stop Rec" else "Record",
                                isActive = isRecording,
                                activeColor = Color(0xFFE53935),
                                onClick = { viewModel.toggleRecording() },
                                testTag = "dock_record_button"
                            )

                            // Action: Go Live
                            val isLive = streamState == StreamState.LIVE || streamState == StreamState.CONNECTING
                            DockItem(
                                icon = Icons.Default.Cast,
                                label = if (isLive) "End Live" else "Go Live",
                                isActive = isLive,
                                activeColor = Color(0xFF4CAF50),
                                onClick = { viewModel.toggleLive() },
                                testTag = "dock_live_button"
                            )

                            // Recordings Library
                            DockItem(
                                icon = Icons.Default.Movie,
                                label = "Files",
                                isActive = false,
                                onClick = { currentScreen = StudioDestination.RECORDINGS },
                                testTag = "dock_recordings"
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets for Scenes, Sources, Mixer
    if (activeSheet != StudioSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = StudioSheet.NONE },
            containerColor = Color(0xFF0F141F)
        ) {
            when (activeSheet) {
                StudioSheet.SCENES -> {
                    ScenesBottomSheet(
                        scenes = scenes,
                        activeScene = activeScene,
                        onSelectScene = {
                            viewModel.selectScene(it)
                            activeSheet = StudioSheet.NONE
                        },
                        onCreateScene = { viewModel.createScene(it) },
                        onDeleteScene = { viewModel.deleteScene(it) }
                    )
                }
                StudioSheet.SOURCES -> {
                    SourcesBottomSheet(
                        sources = activeScene?.sources ?: emptyList(),
                        selectedSource = selectedSource,
                        onSelectSource = { viewModel.selectSource(it) },
                        onToggleVisibility = { viewModel.toggleSourceVisibility(it) },
                        onMoveZIndex = { src, delta ->
                            viewModel.updateSourceTransform(src.id) { it }
                            viewModel.viewModelScope.launch {
                                viewModel.repository.saveSource(src.copy(zIndex = (src.zIndex + delta).coerceAtLeast(0)))
                            }
                        },
                        onAddSourceClick = { showAddSourceDialog = true },
                        onPropertiesClick = { propertySource = it }
                    )
                }
                StudioSheet.MIXER -> {
                    MixerBottomSheet(
                        channels = audioChannels,
                        onVolumeChange = { ch, vol -> viewModel.engine.audioMixer.setVolume(ch, vol) },
                        onMuteToggle = { ch, mute -> viewModel.engine.audioMixer.setMute(ch, mute) },
                        onSoloToggle = { ch, solo -> viewModel.engine.audioMixer.setSolo(ch, solo) }
                    )
                }
                StudioSheet.NONE -> {}
            }
        }
    }

    // Section 17 Quality Dialog
    if (showQualityDialog) {
        QualityDialog(
            capability = viewModel.deviceCapability,
            currentSettings = qualitySettings,
            onAutoSelected = { viewModel.setAutoQuality() },
            onTierSelected = { viewModel.setQualityTier(it) },
            onManualConfigured = { w, h, fps, br, codec, kf ->
                viewModel.setManualQuality(w, h, fps, br, codec, kf)
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    // Add Source Dialog
    if (showAddSourceDialog) {
        val currentSceneId = activeScene?.id ?: "scene_main"
        AddSourceDialog(
            onAdd = { name, type ->
                if (type == SourceType.SCREEN) {
                    val intent = viewModel.engine.screenEngine.createScreenCaptureIntent()
                    screenCaptureLauncher.launch(intent)
                }
                viewModel.addSource(currentSceneId, name, type)
            },
            onDismiss = { showAddSourceDialog = false }
        )
    }

    // Source Properties Dialog
    propertySource?.let { src ->
        SourcePropertiesDialog(
            source = src,
            onTransformUpdated = { newTransform ->
                viewModel.updateSourceTransform(src.id) { newTransform }
            },
            onDelete = {
                viewModel.deleteSource(src.id)
                propertySource = null
            },
            onDismiss = { propertySource = null }
        )
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color(0xFF4FC3F7),
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) activeColor else Color(0xFF90A4AE),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) activeColor else Color(0xFF78909C)
        )
    }
}
