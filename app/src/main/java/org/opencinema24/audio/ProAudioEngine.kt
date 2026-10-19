package org.opencinema24.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Log
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * OpenCinema24 Professional Audio Engine
 * 
 * Features:
 * 1. External USB-C Microphone & Audio Interface auto-detection
 * 2. Uncompressed 48 kHz 16-bit PCM Stereo Audio capture
 * 3. Real-time dBFS RMS calculation for visual Stereo VU Meters
 */
class ProAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "OpenCinema24Audio"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    // Callback for live VU Meters (Left dB, Right dB)
    var onVuMeterUpdate: ((leftDb: Float, rightDb: Float) -> Unit)? = null

    /**
     * Detects if an external USB microphone or audio interface is attached.
     */
    fun findPreferredAudioDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        for (device in devices) {
            if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE || 
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                Log.i(TAG, "External USB Audio device detected: ${device.productName}")
                return device
            }
        }
        Log.i(TAG, "Using built-in microphone array")
        return null
    }

    @SuppressLint("MissingPermission")
    fun start(onAudioData: (buffer: ByteArray, bytesRead: Int) -> Unit) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        // Route to USB device if available
        findPreferredAudioDevice()?.let {
            audioRecord?.preferredDevice = it
        }

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread({
            val audioBuffer = ShortArray(bufferSize / 2)
            val byteBuffer = ByteArray(bufferSize)

            while (isRecording) {
                val shortsRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                if (shortsRead > 0) {
                    // Convert shorts to bytes for muxer / encoder
                    for (i in 0 until shortsRead) {
                        byteBuffer[i * 2] = (audioBuffer[i].toInt() and 0x00FF).toByte()
                        byteBuffer[i * 2 + 1] = ((audioBuffer[i].toInt() shr 8) and 0x00FF).toByte()
                    }
                    onAudioData(byteBuffer, shortsRead * 2)

                    // Compute Stereo RMS dB for live VU Meters
                    computeVuMeter(audioBuffer, shortsRead)
                }
            }
        }, "ProAudioRecordThread").also { it.start() }
    }

    private fun computeVuMeter(buffer: ShortArray, count: Int) {
        var sumLeft = 0.0
        var sumRight = 0.0
        val half = count / 2

        for (i in 0 until count step 2) {
            val left = buffer[i].toDouble() / 32768.0
            val right = if (i + 1 < count) buffer[i + 1].toDouble() / 32768.0 else left
            sumLeft += left * left
            sumRight += right * right
        }

        val rmsLeft = sqrt(sumLeft / half.coerceAtLeast(1))
        val rmsRight = sqrt(sumRight / half.coerceAtLeast(1))

        // Convert to dBFS (-60 dB to 0 dB)
        val leftDb = (20.0 * log10(rmsLeft.coerceAtLeast(0.00001))).toFloat().coerceIn(-60f, 0f)
        val rightDb = (20.0 * log10(rmsRight.coerceAtLeast(0.00001))).toFloat().coerceIn(-60f, 0f)

        onVuMeterUpdate?.invoke(leftDb, rightDb)
    }

    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join()
            recordingThread = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        }
    }
}
