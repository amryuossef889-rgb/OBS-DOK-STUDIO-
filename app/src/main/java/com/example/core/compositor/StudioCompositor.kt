package com.example.core.compositor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.core.model.Scene
import com.example.core.model.SourceRef
import com.example.core.model.SourceType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class StudioCompositor(
    val outputWidth: Int = 1280,
    val outputHeight: Int = 720,
    val targetFps: Int = 30
) {

    private val handlerThread = HandlerThread("StudioCompositorThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private var eglCore: EglCore? = null
    private var glProgram: GlProgram? = null

    private var previewEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var encoderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var previewSurface: Surface? = null
    private var encoderSurface: Surface? = null

    // Textures for video capture
    var cameraTextureId: Int = 0
        private set
    var cameraSurfaceTexture: SurfaceTexture? = null
        private set

    var screenTextureId: Int = 0
        private set
    var screenSurfaceTexture: SurfaceTexture? = null
        private set
    var screenInputSurface: Surface? = null
        private set

    private val dynamic2dTextures = ConcurrentHashMap<String, Int>()

    private val isRunning = AtomicBoolean(false)
    private val frameIntervalMs = (1000L / targetFps).coerceAtLeast(16L)

    private var currentScene: Scene? = null
    private val texMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16)

    // Performance metrics
    @Volatile
    var currentRenderFps: Float = 0f
        private set
    @Volatile
    var totalRenderedFrames: Long = 0L
        private set

    private var fpsCounterFrames = 0
    private var lastFpsUpdateTime = 0L

    init {
        Matrix.setIdentityM(identityMatrix, 0)
        handler.post {
            initGl()
        }
    }

    private fun initGl() {
        try {
            eglCore = EglCore()
            glProgram = GlProgram()

            // Create OES texture for Camera
            cameraTextureId = createOesTexture()
            cameraSurfaceTexture = SurfaceTexture(cameraTextureId).apply {
                setDefaultBufferSize(outputWidth, outputHeight)
            }

            // Create OES texture for Screen
            screenTextureId = createOesTexture()
            screenSurfaceTexture = SurfaceTexture(screenTextureId).apply {
                setDefaultBufferSize(outputWidth, outputHeight)
            }
            screenInputSurface = Surface(screenSurfaceTexture)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            Log.i("StudioCompositor", "GL initialized successfully (CameraTex=$cameraTextureId, ScreenTex=$screenTextureId)")
        } catch (e: Exception) {
            Log.e("StudioCompositor", "Failed to initialize GL: ${e.message}", e)
        }
    }

    private fun createOesTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return texId
    }

    fun setPreviewSurface(surface: Surface?) {
        handler.post {
            val core = eglCore ?: return@post
            if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
                core.destroySurface(previewEglSurface)
                previewEglSurface = EGL14.EGL_NO_SURFACE
            }
            previewSurface = surface
            if (surface != null && surface.isValid) {
                try {
                    previewEglSurface = core.createWindowSurface(surface)
                    Log.i("StudioCompositor", "Preview EGL surface created")
                } catch (e: Exception) {
                    Log.e("StudioCompositor", "Failed to create preview EGL surface: ${e.message}")
                }
            }
        }
    }

    fun setEncoderSurface(surface: Surface?) {
        handler.post {
            val core = eglCore ?: return@post
            if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
                core.destroySurface(encoderEglSurface)
                encoderEglSurface = EGL14.EGL_NO_SURFACE
            }
            encoderSurface = surface
            if (surface != null && surface.isValid) {
                try {
                    encoderEglSurface = core.createWindowSurface(surface)
                    Log.i("StudioCompositor", "Encoder EGL surface created")
                } catch (e: Exception) {
                    Log.e("StudioCompositor", "Failed to create encoder EGL surface: ${e.message}")
                }
            }
        }
    }

    fun setScene(scene: Scene) {
        handler.post {
            currentScene = scene
        }
    }

    fun startRendering() {
        if (isRunning.compareAndSet(false, true)) {
            lastFpsUpdateTime = System.currentTimeMillis()
            handler.post(renderRunnable)
            Log.i("StudioCompositor", "Rendering loop started")
        }
    }

    fun stopRendering() {
        if (isRunning.compareAndSet(true, false)) {
            handler.removeCallbacks(renderRunnable)
            Log.i("StudioCompositor", "Rendering loop stopped")
        }
    }

    private val renderRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return
            val startTime = System.currentTimeMillis()

            renderFrame()

            val elapsed = System.currentTimeMillis() - startTime
            val delay = (frameIntervalMs - elapsed).coerceAtLeast(4L)
            handler.postDelayed(this, delay)
        }
    }

    private fun renderFrame() {
        val core = eglCore ?: return
        val prog = glProgram ?: return

        // Update SurfaceTextures if new frame available
        runCatching { cameraSurfaceTexture?.updateTexImage() }
        runCatching { screenSurfaceTexture?.updateTexImage() }

        val timestampNs = System.nanoTime()

        // 1. Render to Preview Surface if valid
        if (previewEglSurface != EGL14.EGL_NO_SURFACE && previewSurface?.isValid == true) {
            core.makeCurrent(previewEglSurface)
            drawSceneContent(prog)
            core.swapBuffers(previewEglSurface)
        }

        // 2. Render to Encoder Surface if valid
        if (encoderEglSurface != EGL14.EGL_NO_SURFACE && encoderSurface?.isValid == true) {
            core.makeCurrent(encoderEglSurface)
            core.setPresentationTime(encoderEglSurface, timestampNs)
            drawSceneContent(prog)
            core.swapBuffers(encoderEglSurface)
        }

        // Update FPS counter
        totalRenderedFrames++
        fpsCounterFrames++
        val now = System.currentTimeMillis()
        if (now - lastFpsUpdateTime >= 1000L) {
            val deltaSec = (now - lastFpsUpdateTime) / 1000f
            currentRenderFps = fpsCounterFrames / deltaSec
            fpsCounterFrames = 0
            lastFpsUpdateTime = now
        }
    }

    private fun drawSceneContent(prog: GlProgram) {
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        // OBS Studio canvas dark background
        GLES20.glClearColor(0.06f, 0.08f, 0.12f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val scene = currentScene ?: return
        val sortedSources = scene.sources.filter { it.visible }.sortedBy { it.zIndex }

        for (source in sortedSources) {
            drawSource(prog, source)
        }
    }

    private fun drawSource(prog: GlProgram, source: SourceRef) {
        val t = source.transform
        Matrix.setIdentityM(modelMatrix, 0)
        // Normalized canvas coordinates: x, y in [-1, 1]
        Matrix.translateM(modelMatrix, 0, t.x * 2f - 1f, -(t.y * 2f - 1f), 0f)
        Matrix.rotateM(modelMatrix, 0, t.rotation, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, t.scaleX, t.scaleY, 1f)

        when (source.type) {
            SourceType.CAMERA -> {
                cameraSurfaceTexture?.getTransformMatrix(texMatrix) ?: Matrix.setIdentityM(texMatrix, 0)
                prog.drawOesTexture(cameraTextureId, texMatrix, modelMatrix, t.opacity)
            }
            SourceType.SCREEN -> {
                screenSurfaceTexture?.getTransformMatrix(texMatrix) ?: Matrix.setIdentityM(texMatrix, 0)
                prog.drawOesTexture(screenTextureId, texMatrix, modelMatrix, t.opacity)
            }
            SourceType.COLOR -> {
                prog.drawColorQuad(modelMatrix, 0.2f, 0.4f, 0.8f, t.opacity)
            }
            SourceType.TEXT -> {
                val texId = getOrCreateTextTexture(source.id, source.name)
                Matrix.setIdentityM(texMatrix, 0)
                prog.draw2dTexture(texId, texMatrix, modelMatrix, t.opacity)
            }
            SourceType.IMAGE -> {
                val texId = getOrCreatePlaceholderImageTexture(source.id)
                Matrix.setIdentityM(texMatrix, 0)
                prog.draw2dTexture(texId, texMatrix, modelMatrix, t.opacity)
            }
            SourceType.MICROPHONE, SourceType.DEVICE_AUDIO -> {
                // Audio sources don't draw pixels
            }
        }
    }

    private fun getOrCreateTextTexture(sourceId: String, text: String): Int {
        val existing = dynamic2dTextures[sourceId]
        if (existing != null) return existing

        val bitmap = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(text, 20f, 80f, paint)

        val texId = uploadBitmapTexture(bitmap)
        bitmap.recycle()
        dynamic2dTextures[sourceId] = texId
        return texId
    }

    private fun getOrCreatePlaceholderImageTexture(sourceId: String): Int {
        val existing = dynamic2dTextures[sourceId]
        if (existing != null) return existing

        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.DKGRAY)
        val paint = Paint().apply {
            color = Color.CYAN
            textSize = 32f
            isAntiAlias = true
        }
        canvas.drawText("OBS Image", 30f, 130f, paint)

        val texId = uploadBitmapTexture(bitmap)
        bitmap.recycle()
        dynamic2dTextures[sourceId] = texId
        return texId
    }

    private fun uploadBitmapTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texId
    }

    fun release() {
        stopRendering()
        handler.post {
            dynamic2dTextures.values.forEach { texId ->
                val arr = intArrayOf(texId)
                GLES20.glDeleteTextures(1, arr, 0)
            }
            dynamic2dTextures.clear()

            cameraSurfaceTexture?.release()
            screenInputSurface?.release()
            screenSurfaceTexture?.release()

            glProgram?.release()
            eglCore?.destroySurface(previewEglSurface)
            eglCore?.destroySurface(encoderEglSurface)
            eglCore?.release()

            eglCore = null
            glProgram = null
            handlerThread.quitSafely()
            Log.i("StudioCompositor", "Compositor released")
        }
    }
}
