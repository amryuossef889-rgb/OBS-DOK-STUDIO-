package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.diagnostics.StudioDiagnostics
import com.example.core.model.DeviceCapability
import com.example.core.model.StreamProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    streamProfile: StreamProfile?,
    capability: DeviceCapability,
    diagnostics: StudioDiagnostics,
    onSaveStreamProfile: (serverUrl: String, streamKey: String) -> Unit,
    onBack: () -> Unit
) {
    var serverUrl by remember(streamProfile) { mutableStateOf(streamProfile?.serverUrl ?: "rtmp://live.twitch.tv/app/") }
    var streamKey by remember(streamProfile) { mutableStateOf(streamProfile?.streamKey ?: "") }
    var hideKey by remember { mutableStateOf(true) }

    val logs by diagnostics.logsFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Studio Settings & Stream", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_from_settings")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F141F))
            )
        },
        containerColor = Color(0xFF0A0D14)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. RTMP Stream Configuration
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141A29))
                    .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Cast, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RTMP / RTMPS Live Broadcast", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL (e.g. rtmp://, rtmps://)", color = Color(0xFF90A4AE)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("server_url_input")
                )

                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("Stream Key", color = Color(0xFF90A4AE)) },
                    singleLine = true,
                    visualTransformation = if (hideKey) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { hideKey = !hideKey }) {
                            Icon(
                                imageVector = if (hideKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("stream_key_input")
                )

                Button(
                    onClick = { onSaveStreamProfile(serverUrl.trim(), streamKey.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    modifier = Modifier.align(Alignment.End).testTag("save_stream_settings_button")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Stream Key")
                }
            }

            // 2. Hardware Capability Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141A29))
                    .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Device Hardware Capabilities", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }

                Text("Encoder: ${capability.hardwareEncoderName}", fontSize = 12.sp, color = Color(0xFFCFD8DC))
                Text("Max Resolution: ${capability.maxWidth} x ${capability.maxHeight}", fontSize = 12.sp, color = Color(0xFFCFD8DC))
                Text("Max Framerate: ${capability.maxFps} FPS", fontSize = 12.sp, color = Color(0xFFCFD8DC))
                Text("HEVC (H.265) Support: ${if (capability.hevcSupported) "Yes (Hardware)" else "No"}", fontSize = 12.sp, color = Color(0xFFCFD8DC))
                Text("Bitrate Range: ${capability.supportedBitrateRange.lower / 1000} - ${capability.supportedBitrateRange.upper / 1000} kbps", fontSize = 12.sp, color = Color(0xFFCFD8DC))
            }

            // 3. Diagnostics & Event Log Viewer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141A29))
                    .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Notes, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Studio Diagnostics & Event Logs", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF090D14))
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text("No events logged yet", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            logs.forEach { log ->
                                val col = when (log.level) {
                                    "ERROR" -> Color(0xFFFF5252)
                                    "WARN" -> Color(0xFFFFB74D)
                                    else -> Color(0xFF81C784)
                                }
                                Text(
                                    text = "[${log.timestamp}] [${log.level}] ${log.message}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = col
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
