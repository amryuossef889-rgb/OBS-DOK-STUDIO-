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

    fun setPreviewSurface(surface: android.view.Surface?) {
        recording.setPreviewSurface(surface)
    }

    fun startRecording(
        vm: com.dokstudio.obs.ui.StudioViewModel,
        profile: QualityProfile,
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
    val studio by vm.engine.studio.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var showQualityDialog by remember { mutableStateOf(false) }
    val profiles = remember { EncoderCapabilities.profiles() }
    var selectedProfile by remember {
        mutableStateOf(
            profiles.firstOrNull { it.second }?.first
                ?: QualityProfile("Balanced", 1280, 720, 30, 6_000_000),
        )
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF101114)).padding(16.dp)) {
        Text("OBS Dok Studio", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = studio == StudioState.IDLE,
                onClick = { error = null; activity.requestCapturePermissions() },
            ) { Text("Screen Consent") }

            Button(
                enabled = studio == StudioState.IDLE,
                onClick = { activity.startRecording(vm, selectedProfile) { error = it } },
            ) { Text("Record") }

            Button(
                enabled = studio == StudioState.IDLE,
                onClick = { showQualityDialog = true },
            ) { Text("Quality") }

            Button(
                enabled = studio == StudioState.RECORDING,
                onClick = { activity.stopRecording(vm) },
            ) { Text("Stop") }
        }

        Spacer(Modifier.height(8.dp))
        Text("State: " + studio, color = Color.White)
        Text("Selected quality: " + selectedProfile.name, color = Color.White)

        OutlinedButton(
            enabled = studio == StudioState.IDLE,
            onClick = { activity.toggleCamera() },
        ) {
            Text(if (activity.isCameraEnabled()) "Camera: ON" else "Camera: OFF")
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(260.dp).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceView(context).also { view ->
                        view.holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                activity.setPreviewSurface(holder.surface)
                            }
                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) {
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
        }

        Spacer(Modifier.height(12.dp))
        Text("Encoder capabilities", color = Color.White)
        profiles.forEach { entry ->
            val profile = entry.first
            val supported = entry.second
            Text(
                profile.name + " — " + profile.width + "x" + profile.height + " @ " + profile.fps +
                    ": " + if (supported) "supported" else "unsupported",
                color = Color.LightGray,
            )
        }

        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(scenes) { scene ->
                Text(scene.name, color = Color.White, modifier = Modifier.padding(8.dp))
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
                                showQualityDialog = false
                            },
                        ) {
                            Text(
                                profile.name + " — " + profile.width + "x" + profile.height + " @ " + profile.fps +
                                    if (supported) "" else " (unsupported)",
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
