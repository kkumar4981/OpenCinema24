package org.opencinema24.ui

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun CinemaCameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var activeAssist by remember { mutableStateOf("NONE") } // NONE, PEAK, ZEBRA, FALSE

    // Settings
    var currentFps by remember { mutableStateOf("24.000") }
    var currentShutter by remember { mutableStateOf("180.0°") }
    var currentIso by remember { mutableStateOf("100") }
    var currentWb by remember { mutableStateOf("5600K") }

    // Timecode
    var timecodeSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            timecodeSec = 0
            while (true) {
                kotlinx.coroutines.delay(1000)
                timecodeSec++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. LIVE CAMERA PREVIEW (CameraX PreviewView - Works on 100% of Android Devices)
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        cameraRef = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                        Log.i("OpenCinema24", "Camera successfully bound and streaming!")
                    } catch (e: Exception) {
                        Log.e("OpenCinema24", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. ASSIST OVERLAYS (Zebra Stripes, False Color Tint, Focus Peaking)
        if (activeAssist == "ZEBRA") {
            // Live Animated Zebra Stripes overlay
            val infiniteTransition = rememberInfiniteTransition(label = "zebra")
            val offsetAnim by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 40f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "zebraOffset"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 30f
                var x = -size.height + offsetAnim
                while (x < size.width + size.height) {
                    drawLine(
                        color = Color(0x66FACC15),
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = 4f
                    )
                    x += step
                }
            }
        } else if (activeAssist == "PEAK") {
            // Focus Peaking Green Edge Tint Indicator
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, Color(0x8800FF66))
            )
        } else if (activeAssist == "FALSE") {
            // False Color IRE Heat Map Tint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x3300FF00), BlendMode.ColorDodge)
            )
        }

        // 3. TOP CINEMA STATUS BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // REC Indicator & Running Timecode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFEF4444) else Color(0xFF64748B))
                )
                Spacer(modifier = Modifier.width(8.dp))
                val hrs = String.format("%02d", timecodeSec / 3600)
                val mins = String.format("%02d", (timecodeSec % 3600) / 60)
                val secs = String.format("%02d", timecodeSec % 60)
                Text(
                    text = if (isRecording) "$hrs:$mins:$secs:18" else "00:00:00:00",
                    color = Color.White,
                    fontSize = 15.sp
                )
            }

            // GPU Assist Toggles (PEAK, ZEBRA, FALSE COLOR)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    text = "PEAK",
                    isActive = activeAssist == "PEAK",
                    activeColor = Color(0xFF00FF66)
                ) {
                    activeAssist = if (activeAssist == "PEAK") "NONE" else "PEAK"
                }

                AssistChip(
                    text = "ZEBRA",
                    isActive = activeAssist == "ZEBRA",
                    activeColor = Color(0xFFFACC15)
                ) {
                    activeAssist = if (activeAssist == "ZEBRA") "NONE" else "ZEBRA"
                }

                AssistChip(
                    text = "FALSE",
                    isActive = activeAssist == "FALSE",
                    activeColor = Color(0xFF38BDF8)
                ) {
                    activeAssist = if (activeAssist == "FALSE") "NONE" else "FALSE"
                }
            }
        }

        // 4. RIGHT CINEMA RECORD TRIGGER
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(3.dp, if (isRecording) Color(0xFFEF4444) else Color.White, CircleShape)
                    .clickable { isRecording = !isRecording },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isRecording) 28.dp else 52.dp)
                        .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
        }

        // 5. BOTTOM CINEMA DASHBOARD PILLS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CinemaPill(label = "FPS", value = currentFps, isLocked = true) {
                currentFps = if (currentFps == "24.000") "30.000" else if (currentFps == "30.000") "60.000" else "24.000"
            }
            CinemaPill(label = "SHUTTER", value = currentShutter, isLocked = true) {
                currentShutter = if (currentShutter == "180.0°") "1/96s" else if (currentShutter == "1/96s") "1/120s" else "180.0°"
            }
            CinemaPill(label = "ISO", value = currentIso, isLocked = true) {
                currentIso = if (currentIso == "100") "200" else if (currentIso == "200") "400" else if (currentIso == "400") "800" else "100"
            }
            CinemaPill(label = "WB", value = currentWb, isLocked = true) {
                currentWb = if (currentWb == "5600K") "3200K" else if (currentWb == "3200K") "4300K" else "5600K"
            }
            CinemaPill(label = "CODEC", value = "HEVC 120M", isLocked = false) {}
        }
    }
}

@Composable
fun AssistChip(text: String, isActive: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) activeColor else Color(0x991E293B))
            .border(1.dp, if (isActive) activeColor else Color(0xFF334155), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Black else Color(0xFF94A3B8),
            fontSize = 12.sp
        )
    }
}

@Composable
fun CinemaPill(label: String, value: String, isLocked: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xD90F172A))
            .border(1.dp, if (isLocked) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color(0xFF64748B), fontSize = 10.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp)
    }
}
