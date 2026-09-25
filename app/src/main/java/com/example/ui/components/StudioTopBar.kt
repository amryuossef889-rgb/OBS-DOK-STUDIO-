package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.diagnostics.DiagnosticMetrics
import com.example.core.model.QualitySettings
import com.example.core.model.StreamState
import com.example.core.model.StudioState
import java.util.Locale

@Composable
fun StudioTopBar(
    sceneName: String,
    studioState: StudioState,
    streamState: StreamState,
    qualitySettings: QualitySettings,
    metrics: DiagnosticMetrics,
    onQualityClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    Surface(
        color = Color(0xFF0F141F),
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Scene title & Project tag
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sceneName.ifEmpty { "Default Scene" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "OBS Dok Studio Native",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E99B0)
                    )
                }

                // Status Indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Recording Indicator
                    if (studioState == StudioState.RECORDING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE53935).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("recording_indicator")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935).copy(alpha = alphaAnim))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "REC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                        }
                    }

                    // Live Stream Indicator
                    if (streamState == StreamState.LIVE || streamState == StreamState.CONNECTING) {
                        val isLive = streamState == StreamState.LIVE
                        val badgeColor = if (isLive) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(badgeColor.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("live_indicator")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLive) "LIVE" else "CONN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }

                    // Camera Switch action
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("switch_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.LightGray
                        )
                    }

                    // Settings action
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Studio Settings",
                            tint = Color.LightGray
                        )
                    }
                }
            }

            // Sub-bar with real-time performance telemetry & Quality shortcut
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Real Telemetry counters
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", metrics.renderFps)} FPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (metrics.renderFps >= 25f) Color(0xFF81C784) else Color(0xFFFFB74D)
                    )
                    Text(
                        text = "Drops: ${metrics.droppedFrames}",
                        fontSize = 11.sp,
                        color = if (metrics.droppedFrames == 0L) Color(0xFF90A4AE) else Color(0xFFEF5350)
                    )
                    if (metrics.bitrateKbps > 0) {
                        Text(
                            text = "${metrics.bitrateKbps} kbps",
                            fontSize = 11.sp,
                            color = Color(0xFF64B5F6)
                        )
                    }
                    Text(
                        text = "RAM: ${metrics.memoryUsageMb}M",
                        fontSize = 10.sp,
                        color = Color(0xFF78909C)
                    )
                }

                // Dynamic Quality Quick Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E283D))
                        .clickable(onClick = onQualityClick)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("quality_selector_pill")
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = "Quality",
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${qualitySettings.height}p${qualitySettings.fps}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE1E7F0)
                    )
                }
            }
        }
    }
}
