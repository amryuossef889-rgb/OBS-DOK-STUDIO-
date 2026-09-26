package com.dokstudio.obs.capture

import android.content.Context
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraCaptureManager(
    private val context: Context,
    private val owner: LifecycleOwner,
) {
    private var provider: ProcessCameraProvider? = null
    private var targetSurface: Surface? = null

    enum class Lens { BACK, FRONT }

    fun start(
        target: Surface,
        width: Int,
        height: Int,
        lens: Lens = Lens.BACK,
        onStarted: () -> Unit = {},
        onError: (Throwable) -> Unit,
    ) {
        stop()
        targetSurface = target
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    val preview = Preview.Builder()
                        .setTargetResolution(Size(width, height))
                        .build()
                    preview.setSurfaceProvider { request ->
                        val surface = targetSurface
                        if (surface == null || !surface.isValid) {
                            request.willNotProvideSurface()
                        } else {
                            request.provideSurface(
                                surface,
                                ContextCompat.getMainExecutor(context),
                            ) { }
                        }
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        owner,
                        if (lens == Lens.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )
                    onStarted()
                } catch (t: Throwable) {
                    onError(t)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stop() {
        runCatching { provider?.unbindAll() }
        provider = null
        targetSurface = null
    }
}
