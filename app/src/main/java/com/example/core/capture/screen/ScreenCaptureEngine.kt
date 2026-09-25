package com.example.core.capture.screen

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager

class ScreenCaptureEngine(private val context: Context) {

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    var isCapturing = false
        private set

    fun createScreenCaptureIntent(): Intent {
        return projectionManager.createScreenCaptureIntent()
    }

    fun startCapture(
        resultCode: Int,
        resultData: Intent,
        surface: Surface,
        width: Int = 1280,
        height: Int = 720,
        densityDpi: Int = 320,
        onStopped: () -> Unit = {}
    ): Boolean {
        try {
            stopCapture()
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            val projection = mediaProjection ?: run {
                Log.e("ScreenCaptureEngine", "MediaProjection is null after getMediaProjection")
                return false
            }

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i("ScreenCaptureEngine", "MediaProjection callback onStop fired")
                    stopCapture()
                    onStopped()
                }
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = projection.createVirtualDisplay(
                "OBSDokScreenVirtualDisplay",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            isCapturing = true
            Log.i("ScreenCaptureEngine", "Screen capture virtual display created (${width}x${height})")
            return true
        } catch (e: Exception) {
            Log.e("ScreenCaptureEngine", "Failed to start screen capture: ${e.message}", e)
            stopCapture()
            return false
        }
    }

    fun stopCapture() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.stop()
            mediaProjection = null
            isCapturing = false
            Log.i("ScreenCaptureEngine", "Screen capture stopped")
        } catch (e: Exception) {
            Log.w("ScreenCaptureEngine", "Error stopping screen capture: ${e.message}")
        }
    }
}
