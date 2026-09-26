package com.dokstudio.obs.capture

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.view.Surface

class ScreenCaptureManager(private val context: Context) {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var stopping = false

    fun startDirect(
        code: Int,
        data: Intent,
        surface: Surface,
        width: Int,
        height: Int,
        dpi: Int,
        onStopped: () -> Unit,
    ) {
        check(projection == null) { "Screen capture is already active" }
        stopping = false

        val manager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val mediaProjection = manager.getMediaProjection(code, data)
            ?: error("MediaProjection consent was not accepted")

        projection = mediaProjection
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    val wasExpected = stopping
                    cleanupProjection()
                    if (!wasExpected) onStopped()
                }
            },
            Handler(context.mainLooper),
        )

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "OBS-Dok",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null,
        ) ?: error("Unable to create MediaProjection virtual display")
    }

    fun stop() {
        stopping = true
        cleanupProjection()
    }

    private fun cleanupProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
    }
}
