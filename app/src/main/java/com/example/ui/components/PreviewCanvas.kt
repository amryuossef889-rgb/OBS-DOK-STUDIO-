package com.example.ui.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.engine.StudioEngine
import com.example.core.model.SourceRef
import com.example.core.model.Transform

@Composable
fun PreviewCanvas(
    engine: StudioEngine,
    selectedSource: SourceRef?,
    onTransformChanged: (Transform) -> Unit,
    showGuides: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .testTag("preview_canvas_box"),
        contentAlignment = Alignment.Center
    ) {
        // Real OpenGL ES output surface
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            engine.setPreviewSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            engine.setPreviewSurface(holder.surface)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            engine.setPreviewSurface(null)
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Detection & Selection Handle Overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedSource) {
                    if (selectedSource != null) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            val current = selectedSource.transform
                            val newX = (current.x + pan.x / size.width).coerceIn(0f, 1f)
                            val newY = (current.y + pan.y / size.height).coerceIn(0f, 1f)
                            val newScaleX = (current.scaleX * zoom).coerceIn(0.1f, 3.0f)
                            val newScaleY = (current.scaleY * zoom).coerceIn(0.1f, 3.0f)
                            val newRot = (current.rotation + rotation) % 360f

                            onTransformChanged(
                                current.copy(
                                    x = newX,
                                    y = newY,
                                    scaleX = newScaleX,
                                    scaleY = newScaleY,
                                    rotation = newRot
                                )
                            )
                        }
                    }
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // 1. Optional Safe Area Guides
            if (showGuides) {
                // Outer 90% Action Safe
                drawRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(canvasW * 0.05f, canvasH * 0.05f),
                    size = Size(canvasW * 0.9f, canvasH * 0.9f),
                    style = Stroke(width = 1.dp.toPx())
                )
                // Center Crosshair
                drawLine(
                    color = Color.Cyan.copy(alpha = 0.2f),
                    start = Offset(canvasW * 0.5f, 0f),
                    end = Offset(canvasW * 0.5f, canvasH),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.Cyan.copy(alpha = 0.2f),
                    start = Offset(0f, canvasH * 0.5f),
                    end = Offset(canvasW, canvasH * 0.5f),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 2. Selection Handles around selected source
            if (selectedSource != null && selectedSource.visible) {
                val t = selectedSource.transform
                val centerX = t.x * canvasW
                val centerY = t.y * canvasH
                val boxW = t.scaleX * (canvasW * 0.5f)
                val boxH = t.scaleY * (canvasH * 0.5f)
                val left = centerX - boxW / 2f
                val top = centerY - boxH / 2f

                drawRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(left, top),
                    size = Size(boxW, boxH),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Corner handles
                val handleRadius = 5.dp.toPx()
                val corners = listOf(
                    Offset(left, top),
                    Offset(left + boxW, top),
                    Offset(left, top + boxH),
                    Offset(left + boxW, top + boxH)
                )
                corners.forEach { corner ->
                    drawCircle(
                        color = Color.White,
                        radius = handleRadius,
                        center = corner
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = handleRadius,
                        center = corner,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}
