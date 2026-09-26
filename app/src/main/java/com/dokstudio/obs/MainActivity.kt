package com.dokstudio.obs

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dokstudio.obs.engine.StudioState
import com.dokstudio.obs.permissions.PermissionCoordinator
import com.dokstudio.obs.recording.EncoderCapabilities
import com.dokstudio.obs.recording.RecordingController
import com.dokstudio.obs.service.StudioForegroundService

class MainActivity : ComponentActivity() {
    private lateinit var recording: RecordingController
    private var projectionResult: Int? = null
    private var projectionData: Intent? = null

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                projectionResult = result.resultCode
                projectionData = result.data
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recording = RecordingController(this)
        setContent {
            MaterialTheme {
                StudioScreen(this, projectionLauncher)
            }
        }
    }

    fun requestCapturePermissions() {
        PermissionCoordinator(this).requestCapturePermissions()
    }

    fun requestProjectionConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    fun startRecording(
        vm: com.dokstudio.obs.ui.StudioViewModel,
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
            onError("Camera and microphone permissions are required for capture sources.")
            return
        }

        try {
            val serviceIntent = Intent(this, StudioForegroundService::class.java)
                .setAction(StudioForegroundService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            recording.start(
                code = code,
                data = data,
                width = 1280,
                height = 720,
                fps = 30,
                bitrate = 6_000_000,
                onStarted = { vm.engine.markRecording() },
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

    Column(
        Modifier.fillMaxSize().background(Color(0xFF101114)).padding(16.dp),
    ) {
        Text("OBS Dok Studio", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = studio == StudioState.IDLE,
                onClick = {
                    error = null
                    vm.engine.prepare()
                    vm.engine.ready()
                },
            ) { Text("Prepare") }

            Button(
                enabled = studio == StudioState.READY,
                onClick = {
                    error = null
                    activity.requestCapturePermissions()
                    activity.requestProjectionConsent()
                },
            ) { Text("Screen Consent") }

            Button(
                enabled = studio == StudioState.READY,
                onClick = { activity.startRecording(vm) { error = it } },
            ) { Text("Record") }

            Button(
                enabled = studio == StudioState.RECORDING,
                onClick = { activity.stopRecording(vm) },
            ) { Text("Stop") }
        }

        Spacer(Modifier.height(8.dp))
        Text("State: " + studio, color = Color.White)

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
        Text("Encoder profiles", color = Color.White)
        EncoderCapabilities.profiles().forEach { entry ->
            val profile = entry.first
            val supported = entry.second
            Text(
                profile.name + " — " + profile.width + "x" + profile.height + " @ " +
                    profile.fps + ": " + if (supported) "supported" else "unsupported",
                color = Color.LightGray,
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF202226)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Preview pipeline will be rendered by the native compositor.",
                color = Color.Gray,
            )
        }

        scenes.firstOrNull()?.let { scene ->
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.addSource(scene.id, "SCREEN") }) { Text("Screen Source") }
                Button(onClick = { vm.addSource(scene.id, "CAMERA") }) { Text("Camera Source") }
                Button(onClick = { vm.addSource(scene.id, "MICROPHONE") }) { Text("Mic Source") }
            }
        }

        LazyColumn {
            items(scenes) { scene ->
                Text(scene.name, color = Color.White, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
