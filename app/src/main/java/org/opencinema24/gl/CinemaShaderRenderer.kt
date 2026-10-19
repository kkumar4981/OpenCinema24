package org.opencinema24.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenCinema24 Universal OpenGL ES Camera Renderer
 * 
 * Supports 100% of Android devices with OES external texture and real-time GPU assist filters.
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
        Matrix.setIdentityM(texMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 1. Generate OES External Texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(1920, 1080)
            setOnFrameAvailableListener(this@CinemaShaderRenderer)
            Log.i(TAG, "SurfaceTexture created with texture ID: $textureId")
            onSurfaceTextureReady(this)
        }

        // 2. Compile shaders
        val vertexShaderSource = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uMvpMatrix;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """.trimIndent()

        val fragmentShaderSource = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform int uAssistMode;
            uniform float uTime;
            varying vec2 vTexCoord;

            float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

            vec3 falseColor(float y) {
                if (y < 0.05) return vec3(0.5, 0.0, 0.5);      // Purple (Crushed)
                if (y < 0.20) return vec3(0.0, 0.0, 1.0);      // Blue (Shadows)
                if (y <= 0.45) return vec3(0.0, 0.9, 0.0);     // Green (18% Grey)
                if (y <= 0.70) return vec3(1.0, 0.6, 0.8);     // Pink (Skin tones)
                if (y < 0.90) return vec3(1.0, 0.5, 0.0);      // Orange
                return vec3(1.0, 0.0, 0.0);                     // Red (Blown)
            }

            void main() {
                vec4 color = texture2D(uTexture, vTexCoord);
                float y = luma(color.rgb);

                if (uAssistMode == 1) { // Focus Peaking
                    vec2 step = vec2(1.0 / 1920.0, 1.0 / 1080.0);
                    float right = luma(texture2D(uTexture, vTexCoord + vec2(step.x, 0.0)).rgb);
                    float bottom = luma(texture2D(uTexture, vTexCoord + vec2(0.0, step.y)).rgb);
                    float grad = abs(y - right) + abs(y - bottom);
                    if (grad > 0.07) {
                        gl_FragColor = vec4(0.0, 1.0, 0.4, 1.0); // Neon Green Peaking
                    } else {
                        gl_FragColor = color;
                    }
                } else if (uAssistMode == 2) { // Zebra (>75%)
                    if (y > 0.75) {
                        float stripe = mod(gl_FragCoord.x + gl_FragCoord.y - uTime * 28.0, 16.0);
                        if (stripe < 8.0) gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                        else gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
                    } else {
                        gl_FragColor = color;
                    }
                } else if (uAssistMode == 3) { // False Color
                    gl_FragColor = vec4(falseColor(y), 1.0);
                } else {
                    gl_FragColor = color;
                }
            }
        """.trimIndent()

        program = createProgram(vertexShaderSource, fragmentShaderSource)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvpMatrixLoc = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uAssistModeLoc = GLES20.glGetUniformLocation(program, "uAssistMode")
        uTimeLoc = GLES20.glGetUniformLocation(program, "uTime")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
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

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0f
        GLES20.glUniform1f(uTimeLoc, elapsed)
        GLES20.glUniform1i(uAssistModeLoc, currentAssistMode)

        GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        synchronized(this) {
            updateSurface = true
        }
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
