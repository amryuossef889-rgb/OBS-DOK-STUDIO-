package com.example.core.compositor

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GlProgram {

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uTexMatrix;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = (uTexMatrix * aTextureCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderOes = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        uniform float uAlpha;
        void main() {
            vec4 col = texture2D(sTexture, vTextureCoord);
            gl_FragColor = vec4(col.rgb, col.a * uAlpha);
        }
    """.trimIndent()

    private val fragmentShader2d = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        uniform float uAlpha;
        void main() {
            vec4 col = texture2D(sTexture, vTextureCoord);
            gl_FragColor = vec4(col.rgb, col.a * uAlpha);
        }
    """.trimIndent()

    private val fragmentShaderColor = """
        precision mediump float;
        uniform vec4 uColor;
        void main() {
            gl_FragColor = uColor;
        }
    """.trimIndent()

    private var programOes: Int = 0
    private var program2d: Int = 0
    private var programColor: Int = 0

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    init {
        // Standard quad [-1, 1]
        val quadCoords = floatArrayOf(
            -1.0f,  1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
             1.0f,  1.0f, 0.0f,
             1.0f, -1.0f, 0.0f
        )
        vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadCoords)
        vertexBuffer.position(0)

        // UV coords
        val texCoords = floatArrayOf(
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
        )
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)

        buildPrograms()
    }

    private fun buildPrograms() {
        programOes = createProgram(vertexShaderCode, fragmentShaderOes)
        program2d = createProgram(vertexShaderCode, fragmentShader2d)
        programColor = createProgram(
            """
                uniform mat4 uMVPMatrix;
                attribute vec4 aPosition;
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                }
            """.trimIndent(),
            fragmentShaderColor
        )
    }

    fun drawOesTexture(
        textureId: Int,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray,
        alpha: Float = 1.0f
    ) {
        GLES20.glUseProgram(programOes)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        val uMVP = GLES20.glGetUniformLocation(programOes, "uMVPMatrix")
        val uTex = GLES20.glGetUniformLocation(programOes, "uTexMatrix")
        val uAlpha = GLES20.glGetUniformLocation(programOes, "uAlpha")
        val sTex = GLES20.glGetUniformLocation(programOes, "sTexture")

        val aPos = GLES20.glGetAttribLocation(programOes, "aPosition")
        val aTex = GLES20.glGetAttribLocation(programOes, "aTextureCoord")

        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uTex, 1, false, texMatrix, 0)
        GLES20.glUniform1f(uAlpha, alpha)
        GLES20.glUniform1i(sTex, 0)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    fun draw2dTexture(
        textureId: Int,
        texMatrix: FloatArray,
        mvpMatrix: FloatArray,
        alpha: Float = 1.0f
    ) {
        GLES20.glUseProgram(program2d)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        val uMVP = GLES20.glGetUniformLocation(program2d, "uMVPMatrix")
        val uTex = GLES20.glGetUniformLocation(program2d, "uTexMatrix")
        val uAlpha = GLES20.glGetUniformLocation(program2d, "uAlpha")
        val sTex = GLES20.glGetUniformLocation(program2d, "sTexture")

        val aPos = GLES20.glGetAttribLocation(program2d, "aPosition")
        val aTex = GLES20.glGetAttribLocation(program2d, "aTextureCoord")

        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uTex, 1, false, texMatrix, 0)
        GLES20.glUniform1f(uAlpha, alpha)
        GLES20.glUniform1i(sTex, 0)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun drawColorQuad(mvpMatrix: FloatArray, r: Float, g: Float, b: Float, a: Float) {
        GLES20.glUseProgram(programColor)
        val uMVP = GLES20.glGetUniformLocation(programColor, "uMVPMatrix")
        val uCol = GLES20.glGetUniformLocation(programColor, "uColor")
        val aPos = GLES20.glGetAttribLocation(programColor, "aPosition")

        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(uCol, r, g, b, a)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vShader)
        GLES20.glAttachShader(program, fShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val error = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Could not link program: $error")
        }
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader $type: $error")
        }
        return shader
    }

    fun release() {
        if (programOes != 0) GLES20.glDeleteProgram(programOes)
        if (program2d != 0) GLES20.glDeleteProgram(program2d)
        if (programColor != 0) GLES20.glDeleteProgram(programColor)
    }
}
