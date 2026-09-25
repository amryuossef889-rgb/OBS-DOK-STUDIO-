package com.example.ui.sources

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.SourceRef

@Composable
fun SourcesBottomSheet(
    sources: List<SourceRef>,
    selectedSource: SourceRef?,
    onSelectSource: (SourceRef) -> Unit,
    onToggleVisibility: (SourceRef) -> Unit,
    onMoveZIndex: (source: SourceRef, delta: Int) -> Unit,
    onAddSourceClick: () -> Unit,
    onPropertiesClick: (SourceRef) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = Color(0xFF4FC3F7),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scene Sources",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Button(
                onClick = onAddSourceClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_source_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Source", fontSize = 12.sp)
            }
        }

        if (sources.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No sources in this scene yet", color = Color(0xFF78909C), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(sources.sortedByDescending { it.zIndex }, key = { it.id }) { source ->
                    val isSelected = source.id == selectedSource?.id
                    val borderCol = if (isSelected) Color(0xFF00E5FF) else Color(0xFF222B3D)
                    val bgCol = if (isSelected) Color(0xFF17243A) else Color(0xFF141A29)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgCol)
                            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                            .clickable { onSelectSource(source) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("source_item_${source.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onToggleVisibility(source) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (source.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Visibility",
                                    tint = if (source.visible) Color(0xFF4FC3F7) else Color(0xFF546E7A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = source.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (source.visible) Color.White else Color.Gray,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "${source.type.name} · zIndex: ${source.zIndex}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF8E9BB5)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Layer ordering
                            IconButton(
                                onClick = { onMoveZIndex(source, 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Bring Forward",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onMoveZIndex(source, -1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Send Backward",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // Properties
                            IconButton(
                                onClick = { onPropertiesClick(source) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Properties",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
