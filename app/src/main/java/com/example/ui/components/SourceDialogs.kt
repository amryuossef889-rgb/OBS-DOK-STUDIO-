package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.core.model.SourceRef
import com.example.core.model.SourceType
import com.example.core.model.Transform

@Composable
fun AddSourceDialog(
    onAdd: (name: String, type: SourceType) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(SourceType.CAMERA) }
    var sourceName by remember { mutableStateOf("Camera 1") }

    val sourceTypes = listOf(
        SourceType.CAMERA to ("Camera" to Icons.Default.Videocam),
        SourceType.SCREEN to ("Screen Display" to Icons.Default.PhoneAndroid),
        SourceType.TEXT to ("Text Overlay" to Icons.Default.TextFields),
        SourceType.IMAGE to ("Image" to Icons.Default.Photo),
        SourceType.COLOR to ("Solid Color" to Icons.Default.Palette),
        SourceType.MICROPHONE to ("Microphone" to Icons.Default.Mic),
        SourceType.DEVICE_AUDIO to ("Device Audio" to Icons.Default.VolumeUp)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141A29),
        title = {
            Text(
                text = "Add Source to Scene",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = sourceName,
                    onValueChange = { sourceName = it },
                    label = { Text("Source Name", color = Color(0xFF90A4AE)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("source_name_input")
                )

                Text(
                    text = "Select Source Type:",
                    fontSize = 12.sp,
                    color = Color(0xFF90A4AE)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sourceTypes.forEach { (type, info) ->
                        val isSel = selectedType == type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF1E88E5) else Color(0xFF1B2436))
                                .clickable {
                                    selectedType = type
                                    if (sourceName.isEmpty() || sourceName.startsWith("Source") || sourceName.endsWith("1")) {
                                        sourceName = "${info.first} 1"
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("source_type_item_${type.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = info.second,
                                contentDescription = null,
                                tint = if (isSel) Color.White else Color(0xFF90A4AE),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = info.first,
                                color = if (isSel) Color.White else Color(0xFFCFD8DC),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sourceName.isNotBlank()) {
                        onAdd(sourceName.trim(), selectedType)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                modifier = Modifier.testTag("confirm_add_source_button")
            ) {
                Text("Add Source", color = Color.White)
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
fun SourcePropertiesDialog(
    source: SourceRef,
    onTransformUpdated: (Transform) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var opacity by remember { mutableFloatStateOf(source.transform.opacity) }
    var scale by remember { mutableFloatStateOf(source.transform.scaleX) }
    var rotation by remember { mutableFloatStateOf(source.transform.rotation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141A29),
        title = {
            Text(
                text = "${source.name} Properties",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Type: ${source.type.name}", fontSize = 12.sp, color = Color(0xFF8E9BB5))

                // Opacity Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity:", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                        Text("${(opacity * 100).toInt()}%", fontSize = 12.sp, color = Color.White)
                    }
                    Slider(
                        value = opacity,
                        onValueChange = {
                            opacity = it
                            onTransformUpdated(source.transform.copy(opacity = it))
                        },
                        valueRange = 0.05f..1.0f
                    )
                }

                // Scale Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Scale:", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                        Text(String.format("%.2fx", scale), fontSize = 12.sp, color = Color.White)
                    }
                    Slider(
                        value = scale,
                        onValueChange = {
                            scale = it
                            onTransformUpdated(source.transform.copy(scaleX = it, scaleY = it))
                        },
                        valueRange = 0.1f..2.5f
                    )
                }

                // Rotation Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rotation:", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                        Text("${rotation.toInt()}°", fontSize = 12.sp, color = Color.White)
                    }
                    Slider(
                        value = rotation,
                        onValueChange = {
                            rotation = it
                            onTransformUpdated(source.transform.copy(rotation = it))
                        },
                        valueRange = 0f..360f
                    )
                }

                // Delete Source
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF331418))
                        .clickable {
                            onDelete()
                            onDismiss()
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove Source from Scene", color = Color(0xFFFF5252), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF4FC3F7))
            }
        }
    )
}
