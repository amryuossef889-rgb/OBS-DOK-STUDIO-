package com.example.core.capture.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraCaptureEngine(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var isFrontFacing = false
    private var previewUseCase: Preview? = null
    private var currentSurfaceTexture: SurfaceTexture? = null

    var isRunning = false
        private set

    fun initialize(onReady: () -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = runCatching { future.get() }.getOrNull()
            onReady()
        }, ContextCompat.getMainExecutor(context))
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceTexture: SurfaceTexture,
        width: Int = 1280,
        height: Int = 720,
        useFrontCamera: Boolean = false,
        onError: (String) -> Unit = {}
    ) {
        val provider = cameraProvider ?: run {
            onError("Camera provider not initialized")
            return
        }

        try {
            provider.unbindAll()
            currentSurfaceTexture = surfaceTexture
            surfaceTexture.setDefaultBufferSize(width, height)
            isFrontFacing = useFrontCamera

            val cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            previewUseCase = Preview.Builder()
                .setTargetResolution(Size(width, height))
                .build()

            val surface = Surface(surfaceTexture)
            previewUseCase!!.setSurfaceProvider { request ->
                request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { result ->
                    surface.release()
                }
            }

            camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, previewUseCase)
            isRunning = true
            Log.i("CameraCaptureEngine", "Camera started successfully (front=$useFrontCamera)")
        } catch (e: Exception) {
            Log.e("CameraCaptureEngine", "Failed to start camera: ${e.message}", e)
            onError(e.message ?: "Camera binding failed")
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, onError: (String) -> Unit = {}) {
        val tex = currentSurfaceTexture ?: return
        startCamera(lifecycleOwner, tex, useFrontCamera = !isFrontFacing, onError = onError)
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            previewUseCase = null
            isRunning = false
            Log.i("CameraCaptureEngine", "Camera stopped")
        } catch (e: Exception) {
            Log.w("CameraCaptureEngine", "Error stopping camera: ${e.message}")
        }
    }
}
