package org.opencinema24.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * OpenCinema24 Jetpack Compose Cinema Camera Screen
 * 
 * Features:
 * - OLED True Black (#05070B) Theme
 * - Focus Peaking, False Color, Zebra Assist Toggles
 * - Manual Shutter, ISO, White Balance Rotary Pills
 * - Stereo VU Meters
 * - High-Tactile Cinema Record Trigger
 */
@Composable
fun CinemaCameraScreen() {
    var isRecording by remember { mutableStateOf(false) }
    var activeAssist by remember { mutableStateOf("NONE") } // PEAKING, ZEBRA, FALSE_COLOR

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
    ) {
        // 1. Camera Viewfinder (EGL Surface / TextureView)
        Box(modifier = Modifier.fillMaxSize())

        // 2. Top Status & Assist Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rec Indicator & Timecode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) Color(0xFFEF4444) else Color(0xFF64748B))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "00:01:24:18" else "00:00:00:00",
                    color = Color.White,
                    fontSize = 15.sp
                )
            }

            // Assist Toggles (PEAK, ZEBRA, FALSE COLOR)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    text = "PEAK",
                    isActive = activeAssist == "PEAKING",
                    activeColor = Color(0xFF00FF66)
                ) {
                    activeAssist = if (activeAssist == "PEAKING") "NONE" else "PEAKING"
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
                    isActive = activeAssist == "FALSE_COLOR",
                    activeColor = Color(0xFF38BDF8)
                ) {
                    activeAssist = if (activeAssist == "FALSE_COLOR") "NONE" else "FALSE_COLOR"
                }
            }
        }

        // 3. Right Record Trigger
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

        // 4. Bottom Manual Cinema Dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CinemaPill(label = "FPS", value = "24.000", isLocked = true)
            CinemaPill(label = "SHUTTER", value = "180.0°", isLocked = true)
            CinemaPill(label = "ISO", value = "100", isLocked = true)
            CinemaPill(label = "WB", value = "5600K", isLocked = true)
            CinemaPill(label = "CODEC", value = "HEVC 120M", isLocked = false)
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
fun CinemaPill(label: String, value: String, isLocked: Boolean) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xD90F172A))
            .border(1.dp, if (isLocked) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = Color(0xFF64748B), fontSize = 10.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp)
    }
}
