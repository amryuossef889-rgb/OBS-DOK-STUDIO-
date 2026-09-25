package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.capability.CapabilityDiscovery
import com.example.core.model.DeviceCapability
import com.example.core.model.QualitySettings
import com.example.core.model.QualityTier

@Composable
fun QualityDialog(
    capability: DeviceCapability,
    currentSettings: QualitySettings,
    onAutoSelected: () -> Unit,
    onTierSelected: (QualityTier) -> Unit,
    onManualConfigured: (width: Int, height: Int, fps: Int, bitrateKbps: Int, codecMime: String, keyframeSec: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var isManualMode by remember { mutableStateOf(!currentSettings.isAuto) }
    var selectedTier by remember { mutableStateOf(currentSettings.selectedTier) }

    // Manual controls state
    var selectedResolutionIndex by remember {
        mutableIntStateOf(
            when (currentSettings.height) {
                1080 -> 2
                720 -> 1
                else -> 0
            }
        )
    }
    var selectedFps by remember { mutableIntStateOf(currentSettings.fps) }
    var bitrateKbps by remember { mutableFloatStateOf(currentSettings.bitrateKbps.toFloat()) }
    var selectedCodec by remember { mutableStateOf(currentSettings.codecMime) }

    val resolutions = listOf(
        Triple(854, 480, "480p"),
        Triple(1280, 720, "720p"),
        Triple(1920, 1080, "1080p")
    ).filter { it.first <= capability.maxWidth && it.second <= capability.maxHeight }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141A29),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFF4FC3F7),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recording & Streaming Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Hardware Encoder: ${capability.hardwareEncoderName}",
                    fontSize = 11.sp,
                    color = Color(0xFF8E9BB5)
                )

                // 1. Auto Option (Recommended)
                QualityOptionCard(
                    title = "Auto (Recommended)",
                    description = "Dynamically selects the highest quality verified for your hardware",
                    isSelected = !isManualMode && currentSettings.isAuto,
                    isSupported = true,
                    onClick = {
                        isManualMode = false
                        onAutoSelected()
                    },
                    modifier = Modifier.testTag("quality_option_auto")
                )

                // 2. Preset Quality Tiers
                QualityTier.values().forEach { tier ->
                    val isSupported = CapabilityDiscovery.isTierSupported(tier, capability)
                    val reason = if (!isSupported) {
                        when (tier) {
                            QualityTier.HIGH_QUALITY -> "Requires 1080p hardware encoder"
                            QualityTier.HIGH_FPS -> "Requires 60 FPS support @ 1080p"
                            QualityTier.ULTRA_HEVC -> "Requires HEVC (H.265) hardware encoder"
                            else -> "Unsupported by hardware"
                        }
                    } else null

                    QualityOptionCard(
                        title = tier.displayName,
                        description = tier.description,
                        isSelected = !isManualMode && selectedTier == tier,
                        isSupported = isSupported,
                        unsupportedReason = reason,
                        onClick = {
                            if (isSupported) {
                                isManualMode = false
                                selectedTier = tier
                                onTierSelected(tier)
                            }
                        },
                        modifier = Modifier.testTag("quality_option_${tier.name.lowercase()}")
                    )
                }

                Divider(color = Color(0xFF232D42), modifier = Modifier.padding(vertical = 4.dp))

                // 3. Advanced / Manual Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isManualMode = !isManualMode }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isManualMode,
                        onClick = { isManualMode = true }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Advanced (Manual Controls)",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Fine-tune resolution, FPS, bitrate, and codec within hardware limits",
                            fontSize = 11.sp,
                            color = Color(0xFF8E9BB5)
                        )
                    }
                }

                // Advanced Controls Sub-panel
                AnimatedVisibility(visible = isManualMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D121D))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Resolution selection
                        Text("Resolution:", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            resolutions.forEachIndexed { index, (w, h, label) ->
                                val sel = selectedResolutionIndex == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) Color(0xFF1E88E5) else Color(0xFF1B2436))
                                        .clickable { selectedResolutionIndex = index }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = if (sel) Color.White else Color(0xFF90A4AE),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // FPS selection
                        Text("Framerate:", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30, 60).filter { it <= capability.maxFps }.forEach { fpsVal ->
                                val sel = selectedFps == fpsVal
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) Color(0xFF1E88E5) else Color(0xFF1B2436))
                                        .clickable { selectedFps = fpsVal }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$fpsVal FPS",
                                        fontSize = 12.sp,
                                        color = if (sel) Color.White else Color(0xFF90A4AE),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Bitrate Slider
                        val minBitrate = (capability.supportedBitrateRange.lower / 1000f).coerceAtLeast(1000f)
                        val maxBitrate = (capability.supportedBitrateRange.upper / 1000f).coerceAtMost(20000f)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Target Bitrate:", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                                Text(
                                    "${bitrateKbps.toInt()} kbps",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4FC3F7)
                                )
                            }
                            Slider(
                                value = bitrateKbps,
                                onValueChange = { bitrateKbps = it },
                                valueRange = minBitrate..maxBitrate
                            )
                        }

                        // Codec selector
                        Text("Video Codec:", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedCodec == "video/avc") Color(0xFF1E88E5) else Color(0xFF1B2436))
                                    .clickable { selectedCodec = "video/avc" }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("H.264 (AVC)", fontSize = 11.sp, color = Color.White)
                            }
                            if (capability.hevcSupported) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selectedCodec == "video/hevc") Color(0xFF1E88E5) else Color(0xFF1B2436))
                                        .clickable { selectedCodec = "video/hevc" }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("HEVC (H.265)", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Section 17.4 Thermal Notice
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF332014))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "High quality configurations may warm your device during long sessions. OBS Dok Studio will automatically downgrade quality safely if critical thermal thresholds are reached.",
                        fontSize = 10.sp,
                        color = Color(0xFFFFE0B2),
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isManualMode && resolutions.isNotEmpty()) {
                        val res = resolutions[selectedResolutionIndex]
                        onManualConfigured(
                            res.first,
                            res.second,
                            selectedFps,
                            bitrateKbps.toInt(),
                            selectedCodec,
                            2
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("apply_quality_button")
            ) {
                Text("Apply", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
private fun QualityOptionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    isSupported: Boolean,
    unsupportedReason: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (isSupported) 1f else 0.45f
    val borderColor = if (isSelected) Color(0xFF1E88E5) else Color(0xFF222B3D)
    val bgColor = if (isSelected) Color(0xFF152238) else Color(0xFF111724)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = alpha))
            .border(1.dp, borderColor.copy(alpha = alpha), RoundedCornerShape(8.dp))
            .clickable(enabled = isSupported, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (isSupported) Color.White else Color.Gray
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = if (isSupported) Color(0xFF8E9BB5) else Color(0xFF6B7280)
            )
            if (unsupportedReason != null) {
                Text(
                    text = "• $unsupportedReason",
                    fontSize = 10.sp,
                    color = Color(0xFFE57373)
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFF4FC3F7),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
