package com.dokstudio.obs

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
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
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recording = RecordingController(this, this)
        setContent { MaterialTheme { StudioScreen(this, projectionLauncher) } }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)

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

    fun stopRecording(vm: com.dokstudio.obs.ui.StudioViewModel) {
        runCatching { vm.engine.stopping() }
        recording.stop()
        stopForegroundCaptureService()
        vm.engine.idle()
    }

    private fun stopForegroundCaptureService() {
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
    projectionLauncher: ActivityResultLauncher<Intent>,
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

    Column(Modifier.fillMaxSize().background(Color(0xFF18181B))) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF202124)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("OBS Dok Studio", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                selectedProfile.width.toString() + "×" + selectedProfile.height + " • " +
                    selectedProfile.fps + " FPS",
                color = Color.LightGray,
            )
            Spacer(Modifier.width(10.dp))
            Button(
                enabled = studio == StudioState.IDLE,
                onClick = { error = null; activity.beginRecording { activity.startRecording(vm, selectedProfile, frameShape, cameraScale, cameraX, cameraY, cornerRadius, borderWidth) { error = it } } },
            ) { Text("Start Capture") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = studio == StudioState.RECORDING,
                onClick = { activity.stopRecording(vm) },
            ) { Text("Stop") }
        }

        Box(Modifier.fillMaxWidth().weight(1f).padding(10.dp).background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceView(context).also { view ->
                        view.holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                activity.setPreviewSurface(holder.surface)
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                activity.setPreviewSurface(holder.surface)
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                activity.setPreviewSurface(null)
                            }
                        })
                    }
                },
                update = {},
            )
            Text(
                if (studio == StudioState.RECORDING) "● REC" else "PREVIEW",
                color = if (studio == StudioState.RECORDING) Color.Red else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().height(225.dp).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Scenes", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = vm::addScene) { Text("+") }
                    }
                    LazyColumn {
                        items(scenes) { scene ->
                            Text(
                                scene.name,
                                color = if (vm.selectedScene.value == scene.id) Color.White else Color.LightGray,
                                modifier = Modifier.fillMaxWidth().padding(7.dp).clickable {
                                    vm.selectScene(scene.id)
                                },
                            )
                        }
                    }
                }
            }

            Card(Modifier.weight(1.2f).fillMaxHeight()) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sources", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = vm.selectedScene.value != null,
                            onClick = {
                                vm.selectedScene.value?.let { vm.addSource(it, "SCREEN") }
                            },
                        ) { Text("+ Screen") }
                    }
                    if (sources.isEmpty()) {
                        Text("No sources in selected scene", color = Color.Gray)
                    } else {
                        LazyColumn {
                            items(sources) { source ->
                                Text(source.name, color = Color.White, modifier = Modifier.padding(7.dp))
                            }
                        }
                    }
                }
            }

            Card(Modifier.weight(1.4f).fillMaxHeight()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Audio Mixer", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Microphone", color = Color.White)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Real AudioRecord input", color = Color.Gray)
                    Text("Recording state: " + studio, color = Color.White)
                }
            }

            Card(Modifier.weight(1.25f).fillMaxHeight()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Controls", style = MaterialTheme.typography.titleMedium)
                    Button(
                        enabled = studio == StudioState.IDLE,
                        onClick = { activity.beginRecording { activity.startRecording(vm, selectedProfile, frameShape, cameraScale, cameraX, cameraY, cornerRadius, borderWidth) { error = it } } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Start Recording") }

                    OutlinedButton(
                        enabled = studio == StudioState.IDLE,
                        onClick = { showQualityDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Quality") }

                    Box {
                        OutlinedButton(
                            enabled = studio == StudioState.IDLE,
                            onClick = { showCameraMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (!activity.isCameraEnabled()) "Camera: OFF"
                                else "Camera: " + activity.cameraLens().name
                            )
                        }
                        DropdownMenu(
                            expanded = showCameraMenu,
                            onDismissRequest = { showCameraMenu = false },
                        ) {
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
                            DropdownMenuItem(
                                text = { Text("Camera OFF") },
                                onClick = { showCameraMenu = false },
                            )
                        }
                    }

                    Box {
                        OutlinedButton(
                            enabled = studio == StudioState.IDLE && activity.isCameraEnabled(),
                            onClick = { showFrameMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Camera Frame") }
                        DropdownMenu(
                            expanded = showFrameMenu,
                            onDismissRequest = { showFrameMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rounded") },
                                onClick = {
                                    frameShape = SceneCompositor.FrameShape.ROUNDED
                                    showFrameMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rectangle") },
                                onClick = {
                                    frameShape = SceneCompositor.FrameShape.RECTANGLE
                                    showFrameMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Circle") },
                                onClick = {
                                    frameShape = SceneCompositor.FrameShape.CIRCLE
                                    showFrameMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Size", color = Color.White)
            Slider(value = cameraScale, onValueChange = { cameraScale = it }, valueRange = 0.15f..0.7f, modifier = Modifier.width(110.dp))
            Text("X", color = Color.White)
            Slider(value = cameraX, onValueChange = { cameraX = it }, valueRange = -0.85f..0.85f, modifier = Modifier.width(100.dp))
            Text("Y", color = Color.White)
            Slider(value = cameraY, onValueChange = { cameraY = it }, valueRange = -0.85f..0.85f, modifier = Modifier.width(100.dp))
            Text("Corner", color = Color.White)
            Slider(
                value = cornerRadius,
                onValueChange = { cornerRadius = it },
                valueRange = 0f..0.45f,
                modifier = Modifier.width(150.dp),
            )
            Text("Border", color = Color.White)
            Slider(
                value = borderWidth,
                onValueChange = { borderWidth = it },
                valueRange = 0f..0.08f,
                modifier = Modifier.width(150.dp),
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(10.dp))
        }
        if (recordings.isNotEmpty()) {
            Text(
                "Recordings: " + recordings.size,
                color = Color.Gray,
                modifier = Modifier.padding(10.dp),
            )
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
                                profile.name + " — " + profile.width + "×" + profile.height +
                                    " @ " + profile.fps + " FPS" +
                                    if (supported) "" else " (unsupported)"
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Close") }
            },
        )
    }
}
