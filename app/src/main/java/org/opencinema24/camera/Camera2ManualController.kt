package org.opencinema24.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface

/**
 * OpenCinema24 Camera2 Manual Controller
 * 
 * Provides strict cinematic manual controls (mcpro24fps grade):
 * 1. True 24.000 / 25.000 / 30.000 / 60.000 FPS Lock
 * 2. 180° Shutter Angle (1/48s at 24fps)
 * 3. Manual ISO Sensitivity
 * 4. Manual Lens Focus Slider
 * 5. Manual Kelvin Color Correction Matrix
 */
class Camera2ManualController(private val context: Context) {

    companion object {
        private const val TAG = "OpenCinema24"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Cinema Settings
    var targetFps: Int = 24
    var isoSensitivity: Int = 100
    var shutterSpeedNanos: Long = 20_833_333L // 1/48 sec in nanoseconds (180° shutter at 24fps)
    var manualFocusDistance: Float = 0.0f    // 0.0 = Infinity, >0.0 = Macro
    var kelvinTemperature: Int = 5600        // Daylight (5600K)

    // Hardware Capability Limits
    var supportedIsoRange: Range<Int>? = null
    var supportedExposureRange: Range<Long>? = null
    var minFocusDistance: Float = 0.0f
    var isManualSensorSupported: Boolean = false

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
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

    /**
     * Inspects camera hardware for Level_3 / Full manual sensor support.
     */
    fun inspectCapabilities(cameraId: String): Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        
        isManualSensorSupported = hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                                  hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3

        supportedIsoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        supportedExposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0.0f

        Log.i(TAG, "Camera $cameraId Hardware Level: $hardwareLevel (Manual Sensor Supported: $isManualSensorSupported)")
        Log.i(TAG, "ISO Range: $supportedIsoRange, Shutter Range: $supportedExposureRange, Min Focus: $minFocusDistance")

        return isManualSensorSupported
    }

    /**
     * Opens the camera and establishes an OpenGL/EGL preview stream.
     */
    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String, previewSurface: Surface, recordSurface: Surface?) {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startCaptureSession(previewSurface, recordSurface)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraDevice = null
                Log.e(TAG, "Camera open error: $error")
            }
        }, backgroundHandler)
    }

    private fun startCaptureSession(previewSurface: Surface, recordSurface: Surface?) {
        val surfaces = mutableListOf(previewSurface)
        recordSurface?.let { surfaces.add(it) }

        previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)?.apply {
            addTarget(previewSurface)
            recordSurface?.let { addTarget(it) }
            applyCinemaManualSettings(this)
        }

        cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                updateRepeatingRequest()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Capture session configuration failed")
            }
        }, backgroundHandler)
    }

    /**
     * Applies manual cinema parameters: 24 FPS lock, 180° shutter, manual ISO, manual focus.
     */
    fun applyCinemaManualSettings(builder: CaptureRequest.Builder) {
        // 1. Lock Target Frame Rate (True 24.000 FPS)
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(targetFps, targetFps))

        // 2. Disable Auto-Exposure and Lock Shutter Speed & ISO
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterSpeedNanos)
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, isoSensitivity)

        // 3. Disable Auto-Focus and Set Manual Lens Distance
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDistance)

        // 4. Manual Color Temperature (Kelvin / Tint)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        val gains = kelvinToRggbGains(kelvinTemperature)
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)

        // 5. Disable Edge Enhancement & Noise Reduction for clean flat grading
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
    }

    /**
     * Live runtime adjustment of shutter speed (e.g., 1/48s, 1/50s, 1/96s).
     */
    fun setShutterSpeedFraction(fractionDenominator: Long) {
        shutterSpeedNanos = 1_000_000_000L / fractionDenominator
        updateRepeatingRequest()
    }

    /**
     * Live runtime adjustment of manual focus distance.
     */
    fun setFocusDistance(distance: Float) {
        manualFocusDistance = distance.coerceIn(0.0f, minFocusDistance)
        updateRepeatingRequest()
    }

    /**
     * Live runtime adjustment of ISO.
     */
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

    private fun kelvinToRggbGains(kelvin: Int): RggbChannelVector {
        // Approximate Planckian locus calculation for white balance gains
        val temp = (kelvin / 100.0).toFloat()
        val rGain = if (temp <= 66) 1.0f else (temp - 60).let { 1.2f + it * 0.01f }
        val bGain = if (temp >= 66) 1.0f else (66 - temp).let { 1.0f + it * 0.02f }
        return RggbChannelVector(rGain, 1.0f, 1.0f, bGain)
    }

    fun close() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }
}
