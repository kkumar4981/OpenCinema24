package org.opencinema24

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencinema24.camera.Camera2ManualController
import org.opencinema24.ui.CinemaCameraScreen

class MainActivity : ComponentActivity() {

    private lateinit var cameraController: Camera2ManualController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted && audioGranted) {
            cameraController.startBackgroundThread()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep cinema viewfinder awake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cameraController = Camera2ManualController(this)

        checkAndRequestPermissions()

        setContent {
            CinemaCameraScreen(cameraController = cameraController)
        }
    }

    private fun checkAndRequestPermissions() {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (cameraPermission == PackageManager.PERMISSION_GRANTED &&
            audioPermission == PackageManager.PERMISSION_GRANTED) {
            cameraController.startBackgroundThread()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.close()
        cameraController.stopBackgroundThread()
    }
}
