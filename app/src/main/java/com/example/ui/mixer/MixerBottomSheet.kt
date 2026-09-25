package com.example.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.AudioChannelConfig

@Composable
fun MixerBottomSheet(
    channels: List<AudioChannelConfig>,
    onVolumeChange: (channelId: String, volume: Float) -> Unit,
    onMuteToggle: (channelId: String, isMuted: Boolean) -> Unit,
    onSoloToggle: (channelId: String, isSolo: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F141F))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = Color(0xFF4FC3F7),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Audio Mixer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(channels, key = { it.id }) { channel ->
                AudioChannelCard(
                    channel = channel,
                    onVolumeChange = { onVolumeChange(channel.id, it) },
                    onMuteToggle = { onMuteToggle(channel.id, it) },
                    onSoloToggle = { onSoloToggle(channel.id, it) }
                )
            }
        }
    }
}

@Composable
private fun AudioChannelCard(
    channel: AudioChannelConfig,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSoloToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141A29))
            .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (channel.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (channel.isMuted) Color(0xFFE57373) else Color(0xFF4FC3F7),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = channel.name,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Solo Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (channel.isSolo) Color(0xFFFFB300) else Color(0xFF222B3D))
                        .clickable { onSoloToggle(!channel.isSolo) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("solo_button_${channel.id}")
                ) {
                    Text(
                        text = "SOLO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (channel.isSolo) Color.Black else Color.Gray
                    )
                }

                // Mute Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (channel.isMuted) Color(0xFFD32F2F) else Color(0xFF222B3D))
                        .clickable { onMuteToggle(!channel.isMuted) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("mute_button_${channel.id}")
                ) {
                    Text(
                        text = "MUTE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (channel.isMuted) Color.White else Color.Gray
                    )
                }
            }
        }

        // dB Meter bar (-60dB to 0dB)
        val normalizedRms = ((channel.currentRmsDb + 60f) / 60f).coerceIn(0f, 1f)
        val normalizedPeak = ((channel.peakDb + 60f) / 60f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0A0E17))
        ) {
            // RMS Fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (channel.isMuted) 0f else normalizedRms)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF4CAF50),
                                Color(0xFFFFEB3B),
                                Color(0xFFF44336)
                            )
                        )
                    )
            )
            // Peak hold tick
            if (!channel.isMuted && normalizedPeak > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .padding(start = (normalizedPeak * 250).coerceAtLeast(0f).dp)
                        .background(Color.White)
                )
            }
        }

        // Volume Gain Fader
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = channel.volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1.5f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("volume_slider_${channel.id}")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(channel.volume * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCFD8DC),
                modifier = Modifier.width(42.dp)
            )
        }
    }
}
