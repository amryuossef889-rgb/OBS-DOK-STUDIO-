package com.dokstudio.obs

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dokstudio.obs.engine.StudioState
import com.dokstudio.obs.permissions.PermissionCoordinator
import com.dokstudio.obs.recording.EncoderCapabilities
import com.dokstudio.obs.recording.QualityProfile
import com.dokstudio.obs.recording.RecordingController
import com.dokstudio.obs.capture.CameraCaptureManager
import com.dokstudio.obs.data.SourceEntity
import com.dokstudio.obs.compositor.SceneCompositor
import com.dokstudio.obs.service.StudioForegroundService
import com.dokstudio.obs.data.RecordingEntity
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var recording: RecordingController
    private var projectionResult: Int? = null
    private var projectionData: Intent? = null

    private val capturePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val audioGranted = result[android.Manifest.permission.RECORD_AUDIO] == true
            val notificationGranted =
                Build.VERSION.SDK_INT < 33 || result[android.Manifest.permission.POST_NOTIFICATIONS] == true
            if (audioGranted && notificationGranted) requestProjectionConsent()
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraEnabled = granted
        }

    private var cameraEnabled by mutableStateOf(false)
    private var cameraLens by mutableStateOf(CameraCaptureManager.Lens.BACK)
    private var pendingStart: (() -> Unit)? = null
    private var recordingStartedAt = 0L

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                projectionResult = result.resultCode
                projectionData = result.data
                val action = pendingStart
                pendingStart = null
                action?.invoke()
            } else {
                pendingStart = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recording = RecordingController(this, this)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8B5CF6), secondary = Color(0xFFB39DDB), background = Color(0xFF111216), surface = Color(0xFF1B1C20), surfaceVariant = Color(0xFF24252B), onBackground = Color.White, onSurface = Color.White)) { StudioScreen(this) } }
    }

    fun requestCapturePermissions() {
        val missing = PermissionCoordinator(this).missingCapturePermissions()
        if (missing.isEmpty()) requestProjectionConsent()
        else capturePermissionLauncher.launch(missing.toTypedArray())
    }

    fun beginRecording(start: () -> Unit) {
        pendingStart = start
        val missing = PermissionCoordinator(this).missingCapturePermissions()
        if (missing.isNotEmpty()) { capturePermissionLauncher.launch(missing.toTypedArray()); return }
        requestProjectionConsent()
    }

    private fun requestProjectionConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    fun toggleCamera() {
        if (PermissionCoordinator(this).missingCameraPermission()) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        } else {
            cameraEnabled = !cameraEnabled
        }
    }

    fun isCameraEnabled(): Boolean = cameraEnabled

    fun setCameraLensSelection(lens: CameraCaptureManager.Lens) { cameraLens = lens }
    fun cameraLens(): CameraCaptureManager.Lens = cameraLens

    fun setPreviewSurface(surface: android.view.Surface?) {
        recording.setPreviewSurface(surface)
    }

    fun startRecording(
        vm: com.dokstudio.obs.ui.StudioViewModel,
        profile: QualityProfile,
        cameraFrameShape: SceneCompositor.FrameShape,
        cameraScale: Float,
        cameraX: Float,
        cameraY: Float,
        cameraCornerRadius: Float,
        cameraBorderWidth: Float,
        onError: (String) -> Unit,
    ) {
        val code = projectionResult
        val data = projectionData
        if (code == null || data == null) {
            onError("Screen Capture consent is required before recording.")
            return
        }
        if (!PermissionCoordinator(this).capturePermissionsGranted()) {
            requestCapturePermissions()
            onError("Microphone permission is required for the recording audio track.")
            return
        }

        try {
            vm.engine.prepare()
            val serviceIntent = Intent(this, StudioForegroundService::class.java)
                .setAction(StudioForegroundService.ACTION_START)
                .putExtra(StudioForegroundService.EXTRA_CAMERA, cameraEnabled)
            startForegroundCaptureService(serviceIntent) {
                try {
                    recording.start(
                code = code,
                data = data,
                width = profile.width,
                height = profile.height,
                fps = profile.fps,
                bitrate = profile.bitrate,
                includeCamera = cameraEnabled,
                cameraLens = cameraLens,
                cameraScale = cameraScale,
                cameraX = cameraX,
                cameraY = cameraY,
                cameraFrameShape = cameraFrameShape,
                cameraCornerRadius = cameraCornerRadius,
                cameraBorderWidth = cameraBorderWidth,
                sceneSources = vm.selectedSources.value,
                onStarted = {
                    vm.engine.ready()
                    vm.engine.markRecording()
                },
                    onError = {
                        stopForegroundCaptureService()
                        vm.engine.idle()
                        onError(it.message ?: "Recording failed")
                    },
                )
                } catch (t: Throwable) {
                    stopForegroundCaptureService()
                    vm.engine.idle()
                    onError(t.message ?: "Recording failed")
                }
            }
        } catch (t: Throwable) {
            stopForegroundCaptureService()
            vm.engine.idle()
            onError(t.message ?: "Recording failed")
        }
    }

    fun stopRecording(vm: com.dokstudio.obs.ui.StudioViewModel) {
        runCatching { vm.engine.stopping() }
        recording.stop()
        stopForegroundCaptureService()
        vm.engine.idle()
    }

    private var foregroundServiceBound = false
    private var pendingForegroundReady: (() -> Unit)? = null
    private val foregroundServiceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            foregroundServiceBound = true
            val callback = pendingForegroundReady
            if (callback != null) {
                (service as? StudioForegroundService.LocalBinder)?.awaitReady {
                    pendingForegroundReady = null
                    callback()
                }
            }
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            foregroundServiceBound = false
        }
    }

    private fun startForegroundCaptureService(intent: Intent, onReady: () -> Unit) {
        pendingForegroundReady = onReady
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        bindService(
            Intent(this, StudioForegroundService::class.java),
            foregroundServiceConnection,
            BIND_AUTO_CREATE,
        )
    }

    private fun stopForegroundCaptureService() {
        if (foregroundServiceBound) {
            runCatching { unbindService(foregroundServiceConnection) }
            foregroundServiceBound = false
        }
        startService(
            Intent(this, StudioForegroundService::class.java)
                .setAction(StudioForegroundService.ACTION_STOP),
        )
    }

    override fun onDestroy() {
        recording.stop()
        super.onDestroy()
    }
}


@Composable
private fun StudioScreen(
    activity: MainActivity,
    vm: com.dokstudio.obs.ui.StudioViewModel = viewModel(),
) {
    val scenes by vm.scenes.collectAsState()
    val sources by vm.selectedSources.collectAsState()
    val recordings by vm.recordings.collectAsState()
    val studio by vm.engine.studio.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCameraMenu by remember { mutableStateOf(false) }
    var showFrameMenu by remember { mutableStateOf(false) }
    var cameraScale by remember { mutableFloatStateOf(0.32f) }
    var cameraX by remember { mutableFloatStateOf(0.62f) }
    var cameraY by remember { mutableFloatStateOf(-0.62f) }
    var cornerRadius by remember { mutableFloatStateOf(0.14f) }
    var borderWidth by remember { mutableFloatStateOf(0.018f) }
    var frameShape by remember { mutableStateOf(SceneCompositor.FrameShape.ROUNDED) }
    val profiles = remember { EncoderCapabilities.profiles() }
    var selectedProfile by remember {
        mutableStateOf(
            profiles.firstOrNull { it.first.fps == 60 && it.second }?.first
                ?: profiles.firstOrNull { it.second }?.first
                ?: QualityProfile("Balanced", 1280, 720, 30, 6_000_000),
        )
    }
    val isRecording = studio == StudioState.RECORDING
    val isBusy = studio != StudioState.IDLE

    Column(Modifier.fillMaxSize().background(Color(0xFF0D0E11))) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).background(Color(0xFF17181C)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("OBS Dok Studio", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Surface(color = if (isRecording) Color(0xFF3B1518) else Color(0xFF202126), shape = MaterialTheme.shapes.small) {
                Text(
                    if (isRecording) "● RECORDING" else "● READY",
                    color = if (isRecording) Color(0xFFFF6B6B) else Color(0xFFBDBEC5),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${selectedProfile.width}×${selectedProfile.height}  •  ${selectedProfile.fps} FPS", color = Color(0xFFB7B8C0))
            Spacer(Modifier.width(14.dp))
            Text(
                if (recordings.isEmpty()) "No recordings yet" else "${recordings.size} recordings",
                color = Color(0xFF777982),
            )
        }

        Row(
            Modifier.fillMaxWidth().weight(1f).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Left dock: scenes, sources and controls.
            Surface(
                Modifier.width(320.dp).fillMaxHeight(),
                color = Color(0xFF17181C),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        "STUDIO",
                        color = Color(0xFF858792),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(6.dp),
                    )

                    Surface(
                        Modifier.fillMaxWidth().height(155.dp),
                        color = Color(0xFF202126),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Scenes", color = Color.White, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                TextButton(enabled = !isBusy, onClick = vm::addScene) { Text("+ Add") }
                            }
                            LazyColumn {
                                items(scenes) { scene ->
                                    val selected = vm.selectedScene.value == scene.id
                                    Surface(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(enabled = !isBusy) { vm.selectScene(scene.id) },
                                        color = if (selected) Color(0xFF34234E) else Color.Transparent,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    ) {
                                        Text(
                                            scene.name,
                                            color = if (selected) Color.White else Color(0xFFBFC0C7),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        Modifier.fillMaxWidth().weight(1f),
                        color = Color(0xFF202126),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Sources", color = Color.White, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    enabled = !isBusy && vm.selectedScene.value != null,
                                    onClick = { vm.selectedScene.value?.let { vm.addSource(it, "SCREEN") } },
                                ) { Text("+ Screen") }
                                TextButton(
                                    enabled = !isBusy && vm.selectedScene.value != null,
                                    onClick = { vm.selectedScene.value?.let { vm.addSource(it, "CAMERA") } },
                                ) { Text("+ Camera") }
                            }
                            if (sources.isEmpty()) {
                                Text("No sources in this scene", color = Color(0xFF6F7078), modifier = Modifier.padding(top = 12.dp))
                            } else {
                                LazyColumn {
                                    items(sources) { source ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(if (source.type == "CAMERA") "◉" else "▣", color = Color(0xFF9B6BFF), modifier = Modifier.width(24.dp))
                                            Text(source.name, color = Color(0xFFD8D8DD))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        Modifier.fillMaxWidth().wrapContentHeight(),
                        color = Color(0xFF202126),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Controls", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Button(
                                enabled = !isBusy,
                                onClick = {
                                    error = null
                                    activity.beginRecording {
                                        activity.startRecording(
                                            vm, selectedProfile, frameShape,
                                            cameraScale, cameraX, cameraY, cornerRadius, borderWidth
                                        ) { error = it }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                            ) { Text("●  Start Recording") }

                            OutlinedButton(
                                enabled = isRecording,
                                onClick = { activity.stopRecording(vm) },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                            ) { Text("Stop Recording") }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = { showQualityDialog = true },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Quality") }

                                Box(Modifier.weight(1f)) {
                                    OutlinedButton(
                                        enabled = !isBusy,
                                        onClick = { showCameraMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (activity.isCameraEnabled()) "Camera ON" else "Camera OFF")
                                    }
                                    DropdownMenu(expanded = showCameraMenu, onDismissRequest = { showCameraMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Front Camera") },
                                            onClick = {
                                                activity.setCameraLensSelection(CameraCaptureManager.Lens.FRONT)
                                                if (!activity.isCameraEnabled()) activity.toggleCamera()
                                                showCameraMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Back Camera") },
                                            onClick = {
                                                activity.setCameraLensSelection(CameraCaptureManager.Lens.BACK)
                                                if (!activity.isCameraEnabled()) activity.toggleCamera()
                                                showCameraMenu = false
                                            },
                                        )
                                        DropdownMenuItem(text = { Text("Camera OFF") }, onClick = { showCameraMenu = false })
                                    }
                                }
                            }

                            Box {
                                OutlinedButton(
                                    enabled = !isBusy && activity.isCameraEnabled(),
                                    onClick = { showFrameMenu = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Camera Frame: ${frameShape.name.lowercase().replaceFirstChar { it.uppercase() }}")
                                }
                                DropdownMenu(expanded = showFrameMenu, onDismissRequest = { showFrameMenu = false }) {
                                    DropdownMenuItem(text = { Text("Rounded") }, onClick = {
                                        frameShape = SceneCompositor.FrameShape.ROUNDED
                                        showFrameMenu = false
                                    })
                                    DropdownMenuItem(text = { Text("Rectangle") }, onClick = {
                                        frameShape = SceneCompositor.FrameShape.RECTANGLE
                                        showFrameMenu = false
                                    })
                                    DropdownMenuItem(text = { Text("Circle") }, onClick = {
                                        frameShape = SceneCompositor.FrameShape.CIRCLE
                                        showFrameMenu = false
                                    })
                                }
                            }
                        }
                    }
                }
            }

            // Main canvas: preview gets the dominant area.
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    Modifier.fillMaxWidth().weight(1f),
                    color = Color.Black,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                SurfaceView(context).also { view ->
                                    view.holder.addCallback(object : SurfaceHolder.Callback {
                                        override fun surfaceCreated(holder: SurfaceHolder) { activity.setPreviewSurface(holder.surface) }
                                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { activity.setPreviewSurface(holder.surface) }
                                        override fun surfaceDestroyed(holder: SurfaceHolder) { activity.setPreviewSurface(null) }
                                    })
                                }
                            },
                            update = {},
                        )
                        Surface(
                            color = if (isRecording) Color(0xFF8F1D24) else Color(0xCC17181C),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        ) {
                            Text(
                                if (isRecording) "● REC" else "PREVIEW",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            )
                        }
                        Text(
                            "${selectedProfile.width} × ${selectedProfile.height}",
                            color = Color(0xFF8A8B94),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                        )
                    }
                }

                Surface(
                    Modifier.fillMaxWidth().height(112.dp),
                    color = Color(0xFF17181C),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Audio Mixer", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Microphone", color = Color(0xFFD5D5DA), modifier = Modifier.width(88.dp))
                                LinearProgressIndicator(
                                    progress = { if (isRecording) 0.45f else 0f },
                                    modifier = Modifier.weight(1f).height(5.dp),
                                )
                            }
                            Text(
                                if (isRecording) "AudioRecord • active" else "AudioRecord • ready",
                                color = Color(0xFF777982),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        VerticalDivider(Modifier.height(64.dp))
                        Column(Modifier.width(270.dp)) {
                            Text("Session", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                when (studio) {
                                    StudioState.IDLE -> "IDLE — ready to capture"
                                    StudioState.PREPARING -> "PREPARING — initializing pipeline"
                                    StudioState.READY -> "READY"
                                    StudioState.RECORDING -> "RECORDING — file being written"
                                    StudioState.STOPPING -> "STOPPING — finalizing MP4"
                                },
                                color = if (isRecording) Color(0xFFFF7070) else Color(0xFF9B9CA5),
                            )
                        }
                    }
                }

                error?.let {
                    Surface(color = Color(0xFF3A181B), shape = MaterialTheme.shapes.small) {
                        Text(it, color = Color(0xFFFF9A9A), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }

        // Camera transform controls are kept in a compact footer.
        Surface(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color(0xFF17181C),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Camera Layout", color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Size", color = Color(0xFF9A9BA4))
                Slider(value = cameraScale, onValueChange = { cameraScale = it }, valueRange = 0.15f..0.7f, enabled = !isBusy, modifier = Modifier.width(150.dp))
                Text("X", color = Color(0xFF9A9BA4))
                Slider(value = cameraX, onValueChange = { cameraX = it }, valueRange = -0.85f..0.85f, enabled = !isBusy, modifier = Modifier.width(150.dp))
                Text("Y", color = Color(0xFF9A9BA4))
                Slider(value = cameraY, onValueChange = { cameraY = it }, valueRange = -0.85f..0.85f, enabled = !isBusy, modifier = Modifier.width(150.dp))
                Text("Corner", color = Color(0xFF9A9BA4))
                Slider(value = cornerRadius, onValueChange = { cornerRadius = it }, valueRange = 0f..0.45f, enabled = !isBusy, modifier = Modifier.width(140.dp))
                Text("Border", color = Color(0xFF9A9BA4))
                Slider(value = borderWidth, onValueChange = { borderWidth = it }, valueRange = 0f..0.08f, enabled = !isBusy, modifier = Modifier.width(140.dp))
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Recording quality") },
            text = {
                Column {
                    profiles.forEach { entry ->
                        val profile = entry.first
                        val supported = entry.second
                        TextButton(
                            enabled = supported,
                            onClick = {
                                selectedProfile = profile
                                vm.setQuality(profile)
                                showQualityDialog = false
                            },
                        ) {
                            Text(
                                "${profile.name} — ${profile.width}×${profile.height} @ ${profile.fps} FPS" +
                                    if (supported) "" else " (unsupported)"
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text("Close") } },
        )
    }
}
