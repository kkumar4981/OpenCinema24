package org.opencinema24.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenCinema24 OpenGL ES 3.0 Assist Shader Renderer
 * 
 * Runs real-time GPU filters: Focus Peaking, Zebra Stripes, False Color, Flat Log curve.
 */
class CinemaShaderRenderer(
    private val context: Context,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "CinemaShaderRenderer"
        const val MODE_NORMAL = 0
        const val MODE_FOCUS_PEAKING = 1
        const val MODE_ZEBRA = 2
        const val MODE_FALSE_COLOR = 3
    }

    private var surfaceTexture: SurfaceTexture? = null
    private var textureId: Int = 0

    @Volatile
    private var updateSurface = false

    var currentAssistMode = MODE_NORMAL
    var zebraThreshold = 0.75f
    var peakingThreshold = 0.08f

    private val mvpMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)

    private var program = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uMvpMatrixLoc = 0
    private var uTexMatrixLoc = 0
    private var uAssistModeLoc = 0
    private var uTimeLoc = 0

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private val vertices = floatArrayOf(
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f
    )

    private val texCoords = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)

        Matrix.setIdentityM(mvpMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 1. Generate OES External Texture for Camera2 Preview
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@CinemaShaderRenderer)
            onSurfaceTextureReady(this)
        }

        // 2. Compile and Link Shaders
        val vertSource = loadShaderFromAssets("shaders/camera_preview.vert")
        val fragSource = getCombinedFragmentShader()
        program = createProgram(vertSource, fragSource)

        aPositionLoc = GLES30.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES30.glGetAttribLocation(program, "aTexCoord")
        uMvpMatrixLoc = GLES30.glGetUniformLocation(program, "uMvpMatrix")
        uTexMatrixLoc = GLES30.glGetUniformLocation(program, "uTexMatrix")
        uAssistModeLoc = GLES30.glGetUniformLocation(program, "uAssistMode")
        uTimeLoc = GLES30.glGetUniformLocation(program, "uTime")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    private var startTime = System.currentTimeMillis()

    override fun onDrawFrame(gl: GL10?) {
        synchronized(this) {
            if (updateSurface) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(texMatrix)
                updateSurface = false
            }
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0f
        GLES30.glUniform1f(uTimeLoc, elapsed)
        GLES30.glUniform1i(uAssistModeLoc, currentAssistMode)

        GLES30.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES30.glEnableVertexAttribArray(aPositionLoc)
        GLES30.glVertexAttribPointer(aPositionLoc, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        GLES30.glEnableVertexAttribArray(aTexCoordLoc)
        GLES30.glVertexAttribPointer(aTexCoordLoc, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(aPositionLoc)
        GLES30.glDisableVertexAttribArray(aTexCoordLoc)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        synchronized(this) {
            updateSurface = true
        }
    }

    private fun loadShaderFromAssets(filename: String): String {
        return context.assets.open(filename).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).readText()
        }
    }

    private fun getCombinedFragmentShader(): String {
        return """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;

            uniform samplerExternalOES uTexture;
            uniform int uAssistMode;
            uniform float uTime;
            in vec2 vTexCoord;
            out vec4 fragColor;

            float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

            vec3 falseColor(float y) {
                if (y < 0.05) return vec3(0.5, 0.0, 0.5);      // Purple
                if (y < 0.20) return vec3(0.0, 0.0, 1.0);      // Blue
                if (y <= 0.45) return vec3(0.0, 0.9, 0.0);     // Green (18% Grey)
                if (y <= 0.70) return vec3(1.0, 0.6, 0.8);     // Pink (Skin tones)
                if (y < 0.90) return vec3(1.0, 0.5, 0.0);      // Orange
                return vec3(1.0, 0.0, 0.0);                     // Red (Blown)
            }

            void main() {
                vec4 color = texture(uTexture, vTexCoord);
                float y = luma(color.rgb);

                if (uAssistMode == 1) { // Focus Peaking
                    vec2 step = vec2(1.0 / 1920.0, 1.0 / 1080.0);
                    float right = luma(texture(uTexture, vTexCoord + vec2(step.x, 0.0)).rgb);
                    float bottom = luma(texture(uTexture, vTexCoord + vec2(0.0, step.y)).rgb);
                    float grad = abs(y - right) + abs(y - bottom);
                    if (grad > 0.08) {
                        fragColor = vec4(0.0, 1.0, 0.4, 1.0); // Neon Green Peaking
                    } else {
                        fragColor = color;
                    }
                } else if (uAssistMode == 2) { // Zebra Stripes (>75%)
                    if (y > 0.75) {
                        float stripe = mod(gl_FragCoord.x + gl_FragCoord.y - uTime * 28.0, 16.0);
                        if (stripe < 8.0) fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                        else fragColor = vec4(1.0, 1.0, 1.0, 1.0);
                    } else {
                        fragColor = color;
                    }
                } else if (uAssistMode == 3) { // False Color
                    fragColor = vec4(falseColor(y), 1.0);
                } else {
                    fragColor = color;
                }
            }
        """.trimIndent()
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentCode)
        return GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertexShader)
            GLES30.glAttachShader(it, fragmentShader)
            GLES30.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, shaderCode)
            GLES30.glCompileShader(shader)
        }
    }
}
