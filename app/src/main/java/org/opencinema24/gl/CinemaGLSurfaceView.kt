package org.opencinema24.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class CinemaGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    val onSurfaceTextureReady: (SurfaceTexture) -> Unit = {}
) : GLSurfaceView(context, attrs) {

    val renderer = CinemaShaderRenderer(context, onSurfaceTextureReady)

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
