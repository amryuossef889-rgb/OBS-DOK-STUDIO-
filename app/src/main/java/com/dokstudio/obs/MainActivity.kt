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
            val cameraGranted = android.Manifest.permission.CAMERA !in result ||
                result[android.Manifest.permission.CAMERA] == true
            cameraEnabled = cameraGranted && PermissionCoordinator(this).cameraPermissionGranted()
            if (audioGranted && notificationGranted && cameraGranted) requestProjectionConsent()
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

    fun beginRecording(needsCamera: Boolean, start: () -> Unit) {
        pendingStart = start
        val missing = PermissionCoordinator(this).missingCapturePermissions().toMutableList()
        if (needsCamera && PermissionCoordinator(this).missingCameraPermission()) {
            missing += android.Manifest.permission.CAMERA
        }
        if (missing.isNotEmpty()) { capturePermissionLauncher.launch(missing.distinct().toTypedArray()); return }
        if (needsCamera) cameraEnabled = true
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
                includeCamera = vm.selectedSources.value.any { it.type.equals("CAMERA", true) && it.visible },
                includeMicrophone = vm.selectedSources.value.any { it.type.equals("MICROPHONE", true) && it.visible },
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
    var cameraScale by remember { mutableFloatStateOf(0.32f) }
    var cameraX by remember { mutableFloatStateOf(0.62f) }
    var cameraY by remember { mutableFloatStateOf(-0.62f) }
    var cornerRadius by remember { mutableFloatStateOf(0.14f) }
    var borderWidth by remember { mutableFloatStateOf(0.018f) }
    var frameShape by remember { mutableStateOf(SceneCompositor.FrameShape.ROUNDED) }
    var transitionName by remember { mutableStateOf("Fade") }
    var transitionDuration by remember { mutableIntStateOf(300) }

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
    val bg = Color(0xFF171A22)
    val panel = Color(0xFF252832)
    val panelHeader = Color(0xFF303440)
    val border = Color(0xFF3B3F4C)
    val selectedBlue = Color(0xFF245DCC)
    val textPrimary = Color(0xFFE6E8ED)
    val textSecondary = Color(0xFFB6BAC5)

    Column(Modifier.fillMaxSize().background(bg)) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).background(Color(0xFF11131A)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◉", color = Color(0xFFDDDEE4), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(7.dp))
            listOf("File", "Edit", "View", "Docks", "Profile", "Scene Collection", "Tools", "Help").forEach {
                Text(it, color = textPrimary, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("OBS Dok Studio", color = Color(0xFF8F95A2), style = MaterialTheme.typography.labelSmall)
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).background(Color(0xFF171A22)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                Modifier.fillMaxWidth(0.64f).fillMaxHeight(0.88f),
                color = Color.Black,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
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
            }
        }

        Row(
            Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF20232C)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("No source selected", color = textSecondary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(start = 4.dp))
            OutlinedButton(enabled = false, onClick = {}, modifier = Modifier.width(138.dp).height(40.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)) { Text("⚙  Properties", color = Color(0xFFB9BDC7)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(enabled = false, onClick = {}, modifier = Modifier.width(112.dp).height(40.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)) { Text("▣  Filters", color = Color(0xFFB9BDC7)) }
            Spacer(Modifier.width(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().height(292.dp).padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ObsDock(
                title = "Scenes", modifier = Modifier.weight(1f), panel = panel, header = panelHeader, border = border,
            ) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(scenes) { scene ->
                        val selected = vm.selectedScene.value == scene.id
                        Surface(
                            Modifier.fillMaxWidth().height(38.dp).clickable(enabled = !isBusy) { vm.selectScene(scene.id) },
                            color = if (selected) selectedBlue else Color.Transparent,
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(scene.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                DockButtons(onAdd = { vm.addScene() }, addEnabled = !isBusy)
            }

            ObsDock(
                title = "Sources", modifier = Modifier.weight(1.08f), panel = panel, header = panelHeader, border = border,
            ) {
                if (sources.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("?", color = Color(0xFF8D929E), style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(8.dp))
                            Text("You don't have any sources.", color = textSecondary)
                            Text("Click the + button below", color = textSecondary)
                            Text("or right click here to add one.", color = textSecondary)
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(sources) { source ->
                            Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(if (source.type == "CAMERA") "◉" else "▣", color = Color(0xFFCFD3DD), modifier = Modifier.width(25.dp))
                                Text(source.name, color = textPrimary)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                    var showSourceMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            enabled = !isBusy && vm.selectedScene.value != null,
                            onClick = { showSourceMenu = true },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) { Text("+", fontSize = MaterialTheme.typography.titleLarge.fontSize, color = Color.White) }

                        DropdownMenu(
                            expanded = showSourceMenu,
                            onDismissRequest = { showSourceMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Display Capture") },
                                onClick = {
                                    vm.selectedScene.value?.let { vm.addSource(it, "SCREEN") }
                                    showSourceMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Camera") },
                                onClick = {
                                    vm.selectedScene.value?.let { vm.addSource(it, "CAMERA") }
                                    showSourceMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Microphone") },
                                onClick = {
                                    vm.selectedScene.value?.let { vm.addSource(it, "MICROPHONE") }
                                    showSourceMenu = false
                                },
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("⚙", color = Color(0xFFBFC3CD), modifier = Modifier.padding(horizontal = 10.dp))
                    Text("▲", color = Color(0xFFBFC3CD), modifier = Modifier.padding(horizontal = 7.dp))
                    Text("▼", color = Color(0xFFBFC3CD), modifier = Modifier.padding(horizontal = 7.dp))
                }
            }

            ObsDock(
                title = "Audio Mixer", modifier = Modifier.weight(1.42f), panel = panel, header = panelHeader, border = border,
            ) {
                MixerRow("Desktop Audio", "-∞ dB")
                MixerRow("Mic/Aux", "0.0 dB")
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚙", color = Color(0xFFBFC3CD), modifier = Modifier.padding(horizontal = 12.dp))
                    Text("⋮", color = Color(0xFFBFC3CD), style = MaterialTheme.typography.titleLarge)
                }
            }

            ObsDock(
                title = "Scene Transitions", modifier = Modifier.weight(1.05f), panel = panel, header = panelHeader, border = border,
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Transition", color = textSecondary, modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = !isBusy, onClick = { transitionName = if (transitionName == "Fade") "Cut" else "Fade" },
                        modifier = Modifier.width(112.dp).height(40.dp)) { Text(transitionName) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Duration", color = textSecondary, modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = !isBusy, onClick = { transitionDuration = if (transitionDuration == 300) 500 else 300 },
                        modifier = Modifier.width(112.dp).height(40.dp)) { Text("${transitionDuration} ms") }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = Color.White, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                    Text("▣", color = Color(0xFFBFC3CD), modifier = Modifier.padding(horizontal = 12.dp))
                    Text("⋮", color = Color(0xFFBFC3CD), style = MaterialTheme.typography.titleLarge)
                }
            }

            ObsDock(
                title = "Controls", modifier = Modifier.weight(1.12f), panel = panel, header = panelHeader, border = border,
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3E49), disabledContainerColor = Color(0xFF3A3E49))) {
                        Text("Start Streaming", color = Color(0xFFE0E2E7))
                    }
                    Button(enabled = !isBusy, onClick = {
                        error = null
                        val sceneNeedsCamera = sources.any { it.type.equals("CAMERA", true) && it.visible }
                        activity.beginRecording(sceneNeedsCamera) {
                            activity.startRecording(vm, selectedProfile, frameShape, cameraScale, cameraX, cameraY, cornerRadius, borderWidth) { error = it }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3E49))) { Text("Start Recording") }

                    Button(enabled = isRecording, onClick = { activity.stopRecording(vm) },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3E49))) { Text("Stop Recording") }

                    Button(enabled = false, onClick = {}, modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3E49), disabledContainerColor = Color(0xFF3A3E49))) {
                        Text("Start Virtual Camera")
                    }

                    OutlinedButton(enabled = !isBusy, onClick = { showQualityDialog = true },
                        modifier = Modifier.fillMaxWidth().height(40.dp)) { Text("Settings") }

                    Box {
                        OutlinedButton(enabled = !isBusy, onClick = { showCameraMenu = true },
                            modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            Text(if (activity.isCameraEnabled()) "Camera: ON" else "Camera: OFF")
                        }
                        DropdownMenu(expanded = showCameraMenu, onDismissRequest = { showCameraMenu = false }) {
                            DropdownMenuItem(text = { Text("Front Camera") }, onClick = {
                                activity.setCameraLensSelection(CameraCaptureManager.Lens.FRONT)
                                if (!activity.isCameraEnabled()) activity.toggleCamera()
                                showCameraMenu = false
                            })
                            DropdownMenuItem(text = { Text("Back Camera") }, onClick = {
                                activity.setCameraLensSelection(CameraCaptureManager.Lens.BACK)
                                if (!activity.isCameraEnabled()) activity.toggleCamera()
                                showCameraMenu = false
                            })
                            DropdownMenuItem(text = { Text("Camera OFF") }, onClick = { showCameraMenu = false })
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(28.dp).background(Color(0xFF151821)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (isRecording) "REC • recording" else "LIVE: OFFLINE",
                color = if (isRecording) Color(0xFFE45B63) else Color(0xFFB7BBC5),
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("${selectedProfile.fps} FPS  •  ${selectedProfile.width}×${selectedProfile.height}",
                color = Color(0xFF8E94A1), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(12.dp))
            Text(if (recordings.isEmpty()) "No recordings" else "${recordings.size} recording(s)",
                color = Color(0xFF8E94A1), style = MaterialTheme.typography.labelSmall)
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
                        TextButton(enabled = supported, onClick = {
                            selectedProfile = profile
                            vm.setQuality(profile)
                            showQualityDialog = false
                        }) {
                            Text("${profile.name} — ${profile.width}×${profile.height} @ ${profile.fps} FPS" +
                                if (supported) "" else " (unsupported)")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text("Close") } },
        )
    }

    error?.let {
        Surface(color = Color(0xFF5B2025), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(10.dp)) {
            Text(it, color = Color.White, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
private fun ObsDock(
    title: String,
    modifier: Modifier,
    panel: Color,
    header: Color,
    border: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxHeight(), color = panel, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(38.dp).background(header).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("▣", color = Color(0xFFB9BDC7))
            }
            HorizontalDivider(color = border, thickness = 1.dp)
            content()
        }
    }
}

@Composable
private fun DockButtons(
    onAdd: () -> Unit,
    addEnabled: Boolean,
) {
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = addEnabled, onClick = onAdd, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text("+", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.weight(1f))
        Text("▣", color = Color(0xFFBFC3CD), modifier = Modifier.padding(horizontal = 8.dp))
        Text("▲", color = Color(0xFFBFC3CD), modifier = Modifier.padding(horizontal = 7.dp))
        Text("▼", color = Color(0xFFBFC3CD), modifier = Modifier.padding(horizontal = 7.dp))
    }
}

@Composable
private fun MixerRow(name: String, db: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color(0xFFDDE0E7), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(db, color = Color(0xFFDDE0E7), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFF4D7E38)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("-60", "-48", "-36", "-24", "-12", "-6", "0", "3", "6").forEach {
                Text(it, color = Color(0xFF9BA0AA), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
