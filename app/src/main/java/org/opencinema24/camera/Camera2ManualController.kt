package org.opencinema24.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface

/**
 * OpenCinema24 Camera2 Manual Controller
 * 
 * Provides robust camera device binding with cinema manual controls:
 * - Automatic Back Camera selection
 * - Safe FPS range detection (24 FPS if supported, or closest available)
 * - Manual Shutter, ISO, Focus with graceful device fallbacks
 */
class Camera2ManualController(private val context: Context) {

    companion object {
        private const val TAG = "Camera2Controller"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var currentPreviewSurface: Surface? = null
    private var currentRecordSurface: Surface? = null

    // Cinema Settings
    var targetFps: Int = 24
    var isoSensitivity: Int = 100
    var shutterSpeedNanos: Long = 20_833_333L // 1/48 sec
    var manualFocusDistance: Float = 0.0f
    var kelvinTemperature: Int = 5600

    var isManualSensorSupported: Boolean = false
    var supportedIsoRange: Range<Int>? = null
    var supportedExposureRange: Range<Long>? = null
    var minFocusDistance: Float = 0.0f
    var selectedFpsRange: Range<Int> = Range(24, 24)

    init {
        startBackgroundThread()
    }

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    fun getBackCameraId(): String {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering back camera", e)
        }
        return cameraManager.cameraIdList.firstOrNull() ?: "0"
    }

    fun inspectCapabilities(cameraId: String) {
        try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            
            isManualSensorSupported = hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                                      hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3

            supportedIsoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            supportedExposureRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            minFocusDistance = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f

            // Find best matching FPS range
            val availableFps = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
            selectedFpsRange = availableFps.firstOrNull { it.lower == 24 && it.upper == 24 }
                ?: availableFps.firstOrNull { it.upper >= 24 && it.lower <= 24 }
                ?: availableFps.firstOrNull { it.upper == 30 }
                ?: availableFps.firstOrNull()
                ?: Range(24, 24)

            Log.i(TAG, "Camera $cameraId Level: $hardwareLevel | ManualSensor: $isManualSensorSupported | FPS Range: $selectedFpsRange")
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting capabilities", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(previewSurface: Surface, recordSurface: Surface? = null) {
        currentPreviewSurface = previewSurface
        currentRecordSurface = recordSurface

        val cameraId = getBackCameraId()
        inspectCapabilities(cameraId)

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i(TAG, "CameraDevice opened successfully: ${camera.id}")
                    cameraDevice = camera
                    startCaptureSession(previewSurface, recordSurface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "CameraDevice disconnected")
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "CameraDevice onError: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
        }
    }

    private fun startCaptureSession(previewSurface: Surface, recordSurface: Surface?) {
        val surfaces = mutableListOf(previewSurface)
        recordSurface?.let { surfaces.add(it) }

        try {
            previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
                addTarget(previewSurface)
                recordSurface?.let { addTarget(it) }
                applyCinemaManualSettings(this)
            }

            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.i(TAG, "CameraCaptureSession configured successfully!")
                    captureSession = session
                    updateRepeatingRequest()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "CameraCaptureSession configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting capture session", e)
        }
    }

    fun applyCinemaManualSettings(builder: CaptureRequest.Builder) {
        try {
            // 1. Target Frame Rate
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selectedFpsRange)

            // 2. Exposure & ISO (with safe fallback if manual mode not supported)
            if (isManualSensorSupported) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeedNanos)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, isoSensitivity)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            // 3. Focus (Manual or Continuous Video)
            if (minFocusDistance > 0.0f && manualFocusDistance > 0.0f) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDistance)
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            // 4. Color correction
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        } catch (e: Exception) {
            Log.w(TAG, "Error setting manual settings, falling back to auto defaults", e)
        }
    }

    fun setShutterSpeedFraction(fractionDenominator: Long) {
        shutterSpeedNanos = 1_000_000_000L / fractionDenominator
        updateRepeatingRequest()
    }

    fun setFocusDistance(distance: Float) {
        manualFocusDistance = distance.coerceIn(0.0f, minFocusDistance)
        updateRepeatingRequest()
    }

    fun setISO(iso: Int) {
        supportedIsoRange?.let {
            isoSensitivity = iso.coerceIn(it.lower, it.upper)
        } ?: run {
            isoSensitivity = iso
        }
        updateRepeatingRequest()
    }

    fun updateRepeatingRequest() {
        previewRequestBuilder?.let { builder ->
            applyCinemaManualSettings(builder)
            try {
                captureSession?.setRepeatingRequest(builder.build(), null, backgroundHandler)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update repeating request", e)
            }
        }
    }

    fun close() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }
}
