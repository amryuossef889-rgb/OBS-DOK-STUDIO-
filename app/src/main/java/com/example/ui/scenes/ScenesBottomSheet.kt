package com.example.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.core.model.Scene

@Composable
fun ScenesBottomSheet(
    scenes: List<Scene>,
    activeScene: Scene?,
    onSelectScene: (Scene) -> Unit,
    onCreateScene: (String) -> Unit,
    onDeleteScene: (Scene) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newSceneName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F141F))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = Color(0xFF4FC3F7),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scenes Collection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_scene_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Scene", fontSize = 12.sp)
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(scenes, key = { it.id }) { scene ->
                val isActive = scene.id == activeScene?.id
                val borderCol = if (isActive) Color(0xFF1E88E5) else Color(0xFF222B3D)
                val bgCol = if (isActive) Color(0xFF17243A) else Color(0xFF141A29)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgCol)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable { onSelectScene(scene) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("scene_item_${scene.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            Text(
                                text = scene.name,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) Color.White else Color(0xFFCFD8DC),
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${scene.sources.size} sources",
                                fontSize = 11.sp,
                                color = Color(0xFF8E9BB5)
                            )
                        }
                    }

                    if (scenes.size > 1) {
                        IconButton(
                            onClick = { onDeleteScene(scene) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Scene",
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF141A29),
            title = { Text("Create New Scene", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newSceneName,
                    onValueChange = { newSceneName = it },
                    label = { Text("Scene Name", color = Color(0xFF90A4AE)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_scene_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSceneName.isNotBlank()) {
                            onCreateScene(newSceneName.trim())
                            newSceneName = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}
