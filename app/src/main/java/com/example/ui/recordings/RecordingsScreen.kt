package com.example.ui.recordings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.core.model.RecordingItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    recordings: List<RecordingItem>,
    onDeleteRecording: (RecordingItem) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Studio Recordings (${recordings.size})", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_from_recordings")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F141F))
            )
        },
        containerColor = Color(0xFF0A0D14)
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF37474F),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No recordings saved yet",
                        color = Color(0xFF78909C),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = "Tap 'Record' in the dock to capture MP4 video",
                        color = Color(0xFF546E7A),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recordings, key = { it.id }) { item ->
                    val file = File(item.filePath)
                    val exists = file.exists()
                    val durSec = item.durationMs / 1000
                    val durText = String.format("%02d:%02d", durSec / 60, durSec % 60)
                    val sizeMb = String.format(Locale.US, "%.1f MB", item.sizeBytes / (1024f * 1024f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141A29))
                            .border(1.dp, Color(0xFF222B3D), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .testTag("recording_item_${item.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1A273D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFF4FC3F7),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = "$durText · $sizeMb · ${item.width}x${item.height}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E9BB5)
                                )
                                Text(
                                    text = dateFormat.format(Date(item.timestamp)),
                                    fontSize = 10.sp,
                                    color = Color(0xFF546E7A)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (exists) {
                                IconButton(
                                    onClick = {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color(0xFF4FC3F7),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteRecording(item) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFE57373),
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
