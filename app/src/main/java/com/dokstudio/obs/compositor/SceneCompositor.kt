package com.dokstudio.obs.compositor

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SceneCompositor {
    enum class FrameShape { RECTANGLE, ROUNDED, CIRCLE }

    data class InputSource internal constructor(
        val surface: Surface,
        internal val texture: SurfaceTexture,
        internal val textureId: Int,
        internal var alpha: Float = 1f,
        internal var z: Int = 0,
        internal var scaleX: Float = 1f,
        internal var scaleY: Float = 1f,
        internal var translateX: Float = 0f,
        internal var translateY: Float = 0f,
        internal var frameEnabled: Boolean = false,
        internal var frameShape: Int = 1,
        internal var cornerRadius: Float = 0.14f,
        internal var borderWidth: Float = 0.018f,
    )

    private data class LayerState(
        val source: InputSource,
        val texMatrix: FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) },
        var frameAvailable: Boolean = false,
    )

    private val thread = HandlerThread("OBS-Dok-Compositor")
    private lateinit var handler: Handler
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var config: android.opengl.EGLConfig? = null
    private var encoderOutput = EGL14.EGL_NO_SURFACE
    private var previewOutput = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var positionLocation = -1
    private var texCoordLocation = -1
    private var textureLocation = -1
    private var alphaLocation = -1
    private var modelLocation = -1
    private var texMatrixLocation = -1
    private var localLocation = -1
    private var frameEnabledLocation = -1
    private var frameShapeLocation = -1
    private var cornerRadiusLocation = -1
    private var borderWidthLocation = -1
    private val layers = mutableListOf<LayerState>()
    private val released = AtomicBoolean(false)

    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(16 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                -1f,  1f, 0f, 1f,
                 1f,  1f, 1f, 1f,
            ))
            position(0)
        }

    fun initialize(outputSurface: Surface) {
        check(!released.get()) { "Compositor already released" }
        thread.start()
        handler = Handler(thread.looper)
        val ready = CountDownLatch(1)
        var error: Throwable? = null
        handler.post {
            try { initializeEgl(outputSurface) }
            catch (t: Throwable) { error = t }
            finally { ready.countDown() }
        }
        check(ready.await(5, TimeUnit.SECONDS)) { "Compositor initialization timed out" }
        error?.let { throw it }
    }

    fun setPreviewSurface(surface: Surface?) {
        if (!::handler.isInitialized || released.get()) return
        handler.post {
            if (previewOutput != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, previewOutput)
                previewOutput = EGL14.EGL_NO_SURFACE
            }
            if (surface != null && surface.isValid) previewOutput = createWindowSurface(surface)
            render()
        }
    }

    fun createInputSurface(width: Int, height: Int): InputSource {
        check(::handler.isInitialized) { "Compositor is not initialized" }
        require(width > 0 && height > 0)
        val result = arrayOfNulls<InputSource>(1)
        var error: Throwable? = null
        val ready = CountDownLatch(1)
        handler.post {
            try {
                val textureId = createExternalTexture()
                val texture = SurfaceTexture(textureId).apply { setDefaultBufferSize(width, height) }
                val inputSurface = Surface(texture)
                val state = InputSource(inputSurface, texture, textureId)
                layers += LayerState(state)
                texture.setOnFrameAvailableListener({ sourceTexture ->
                    layers.firstOrNull { it.source.texture === sourceTexture }?.frameAvailable = true
                    render()
                }, handler)
                result[0] = state
            } catch (t: Throwable) {
                error = t
            } finally { ready.countDown() }
        }
        check(ready.await(5, TimeUnit.SECONDS)) { "Input surface creation timed out" }
        error?.let { throw it }
        return result[0] ?: error("Input surface creation failed")
    }

    fun updateLayer(
        source: InputSource,
        alpha: Float = source.alpha,
        z: Int = source.z,
        scaleX: Float = source.scaleX,
        scaleY: Float = source.scaleY,
        translateX: Float = source.translateX,
        translateY: Float = source.translateY,
    ) {
        if (!::handler.isInitialized || released.get()) return
        handler.post {
            source.alpha = alpha.coerceIn(0f, 1f)
            source.z = z
            source.scaleX = scaleX
            source.scaleY = scaleY
            source.translateX = translateX
            source.translateY = translateY
            render()
        }
    }

    fun setFrameStyle(
        source: InputSource,
        shape: FrameShape,
        borderWidth: Float,
        cornerRadius: Float,
    ) {
        if (!::handler.isInitialized || released.get()) return
        handler.post {
            source.frameEnabled = true
            source.frameShape = when (shape) {
                FrameShape.RECTANGLE -> 0
                FrameShape.ROUNDED -> 1
                FrameShape.CIRCLE -> 2
            }
            source.borderWidth = borderWidth.coerceIn(0f, 0.12f)
            source.cornerRadius = cornerRadius.coerceIn(0f, 0.49f)
            render()
        }
    }

    private fun initializeEgl(surface: Surface) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "EGL display unavailable" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL initialization failed" }

        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] == 1) {
            "No compatible EGL configuration"
        }
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "EGL context creation failed" }

        encoderOutput = createWindowSurface(surface)
        check(EGL14.eglMakeCurrent(display, encoderOutput, encoderOutput, context)) {
            "EGL makeCurrent failed"
        }

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureLocation = GLES20.glGetUniformLocation(program, "uTexture")
        alphaLocation = GLES20.glGetUniformLocation(program, "uAlpha")
        modelLocation = GLES20.glGetUniformLocation(program, "uModel")
        texMatrixLocation = GLES20.glGetUniformLocation(program, "uTexMatrix")
        localLocation = GLES20.glGetAttribLocation(program, "aLocal")
        frameEnabledLocation = GLES20.glGetUniformLocation(program, "uFrameEnabled")
        frameShapeLocation = GLES20.glGetUniformLocation(program, "uFrameShape")
        cornerRadiusLocation = GLES20.glGetUniformLocation(program, "uCornerRadius")
        borderWidthLocation = GLES20.glGetUniformLocation(program, "uBorderWidth")
        check(positionLocation >= 0 && texCoordLocation >= 0 && textureLocation >= 0 &&
            alphaLocation >= 0 && modelLocation >= 0 && texMatrixLocation >= 0 && localLocation >= 0 &&
            frameEnabledLocation >= 0 && frameShapeLocation >= 0 &&
            cornerRadiusLocation >= 0 && borderWidthLocation >= 0) {
            "Required GL locations are unavailable"
        }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun createWindowSurface(surface: Surface): android.opengl.EGLSurface {
        check(surface.isValid) { "Output surface is invalid" }
        return EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            .also { check(it != EGL14.EGL_NO_SURFACE) { "EGL output surface creation failed" } }
    }

    private fun render() {
        if (released.get() || display == EGL14.EGL_NO_DISPLAY || encoderOutput == EGL14.EGL_NO_SURFACE) return
        if (!layers.any { it.frameAvailable }) return

        layers.filter { it.frameAvailable }.forEach { layer ->
            layer.source.texture.updateTexImage()
            layer.source.texture.getTransformMatrix(layer.texMatrix)
            layer.frameAvailable = false
        }
        renderTo(encoderOutput)
        if (previewOutput != EGL14.EGL_NO_SURFACE) {
            runCatching { renderTo(previewOutput) }.onFailure {
                EGL14.eglDestroySurface(display, previewOutput)
                previewOutput = EGL14.EGL_NO_SURFACE
            }
        }
    }

    private fun renderTo(target: android.opengl.EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, target, target, context)) return
        val width = IntArray(1)
        val height = IntArray(1)
        EGL14.eglQuerySurface(display, target, EGL14.EGL_WIDTH, width, 0)
        EGL14.eglQuerySurface(display, target, EGL14.EGL_HEIGHT, height, 0)
        GLES20.glViewport(0, 0, width[0], height[0])
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 16, vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(texCoordLocation)
        GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, 16, vertices)
        vertices.position(2)
        GLES20.glEnableVertexAttribArray(localLocation)
        GLES20.glVertexAttribPointer(localLocation, 2, GLES20.GL_FLOAT, false, 16, vertices)

        layers.sortedBy { it.source.z }.forEach { layer ->
            val model = FloatArray(16)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, layer.source.translateX, layer.source.translateY, 0f)
            Matrix.scaleM(model, 0, layer.source.scaleX, layer.source.scaleY, 1f)
            GLES20.glUniformMatrix4fv(modelLocation, 1, false, model, 0)
            GLES20.glUniformMatrix4fv(texMatrixLocation, 1, false, layer.texMatrix, 0)
            GLES20.glUniform1f(alphaLocation, layer.source.alpha)
            GLES20.glUniform1i(frameEnabledLocation, if (layer.source.frameEnabled) 1 else 0)
            GLES20.glUniform1i(frameShapeLocation, layer.source.frameShape)
            GLES20.glUniform1f(cornerRadiusLocation, layer.source.cornerRadius)
            GLES20.glUniform1f(borderWidthLocation, layer.source.borderWidth)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, layer.source.textureId)
            GLES20.glUniform1i(textureLocation, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        EGLExt.eglPresentationTimeANDROID(display, target, System.nanoTime())
        check(EGL14.eglSwapBuffers(display, target)) {
            "EGL swap buffers failed: ${EGL14.eglGetError()}"
        }
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("GL program link failed: $message")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("GL shader compile failed: $message")
        }
        return shader
    }

    fun release() {
        if (!released.compareAndSet(false, true) || !::handler.isInitialized) return
        val done = CountDownLatch(1)
        handler.post {
            layers.forEach {
                it.source.surface.release()
                it.source.texture.release()
                GLES20.glDeleteTextures(1, intArrayOf(it.source.textureId), 0)
            }
            layers.clear()
            if (program != 0) GLES20.glDeleteProgram(program)
            if (previewOutput != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, previewOutput)
            if (encoderOutput != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, encoderOutput)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            if (display != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(display)
            done.countDown()
        }
        done.await(5, TimeUnit.SECONDS)
        thread.quitSafely()
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            attribute vec2 aLocal;
            uniform mat4 uModel;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            varying vec2 vLocal;
            void main() {
                gl_Position = uModel * aPosition;
                vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
                vLocal = aLocal;
            }
        """
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            #extension GL_OES_standard_derivatives : enable
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform float uAlpha;
            uniform int uFrameEnabled;
            uniform int uFrameShape;
            uniform float uCornerRadius;
            uniform float uBorderWidth;
            varying vec2 vTexCoord;
            varying vec2 vLocal;
            float roundedDistance(vec2 p, float radius) {
                vec2 q = abs(p - vec2(0.5)) - vec2(0.5 - radius);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }
            float shapeDistance() {
                if (uFrameShape == 2) return distance(vLocal, vec2(0.5)) - 0.5;
                if (uFrameShape == 1) return roundedDistance(vLocal, clamp(uCornerRadius, 0.0, 0.49));
                return max(abs(vLocal.x - 0.5), abs(vLocal.y - 0.5)) - 0.5;
            }
            void main() {
                vec4 color = texture2D(uTexture, vTexCoord);
                float alpha = color.a * uAlpha;
                if (uFrameEnabled == 1) {
                    float d = shapeDistance();
                    float edge = max(fwidth(d), 0.001);
                    float inside = 1.0 - smoothstep(0.0, edge, d);
                    float border = 1.0 - smoothstep(-uBorderWidth - edge, -uBorderWidth, d);
                    color.rgb = mix(color.rgb, vec3(1.0), border * inside);
                    alpha *= inside;
                }
                gl_FragColor = vec4(color.rgb, alpha);
            }
        """
    }
}
