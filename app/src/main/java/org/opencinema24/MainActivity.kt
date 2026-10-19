package org.opencinema24

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencinema24.camera.Camera2ManualController
import org.opencinema24.gl.CinemaGLSurfaceView
import org.opencinema24.ui.CinemaCameraScreen

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: Camera2ManualController
    private var glSurfaceView: CinemaGLSurfaceView? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted && audioGranted) {
            setupCameraPipeline()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep cinema screen on at all times
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraController = Camera2ManualController(this)

        checkAndRequestPermissions()

        setContent {
            CinemaCameraScreen()
        }
    }

    private fun checkAndRequestPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (cameraPermission == PackageManager.PERMISSION_GRANTED &&
            audioPermission == PackageManager.PERMISSION_GRANTED) {
            setupCameraPipeline()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    private fun setupCameraPipeline() {
        cameraController.startBackgroundThread()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.close()
        cameraController.stopBackgroundThread()
    }
}
