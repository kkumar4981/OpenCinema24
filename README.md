# 🎬 OpenCinema24

> **The Free & Open-Source Manual Cinema Video Camera Engine for Android.**  
> Inspired by professional cinema tools like mcpro24fps, FiLMiC Pro, and Blackmagic Camera.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-Camera2%20API-green.svg)](https://developer.android.com/training/camera2)
[![OpenGL ES](https://img.shields.io/badge/GPU-OpenGL%20ES%203.0-orange.svg)]()

---

## 💡 What is OpenCinema24?

Stock camera apps force aggressive auto-exposure, skin smoothing, and low-bitrate compression.  
**OpenCinema24** unlocks raw hardware sensor control, real-time GPU filmmaker assists, and uncompressed cinema workflows.

### 🌟 Key Capabilities
- 🎥 **True 24.000 FPS Cadence:** Hardware sensor lock preventing stutter and frame drops.
- 📐 **180° Shutter Angle Rule:** Precise `1/48s` shutter speed for natural cinematic motion blur.
- 🎛️ **Full Manual Exposure:** Direct control over ISO sensitivity, shutter speed nanoseconds, and Kelvin white balance.
- 🔍 **Real-Time GPU Focus Peaking:** Sobel edge-detection GLSL shader highlighting sharp in-focus planes in neon green.
- 🦓 **Zebra Stripes & False Color:** ARRI-standard 16-band IRE exposure evaluation and highlight zebra warnings.
- 🚀 **120+ Mbps Ultra-High Bitrate:** Hardware HEVC (H.265) / AVC encoding directly through zero-copy EGL surfaces.
- 🎙️ **48 kHz Pro Audio with VU Meters:** External USB-C mic routing, uncompressed 16-bit PCM, and live stereo dBFS monitoring.

---

## 📂 Project Structure

```
OpenCinema24/
├── ARCHITECTURE.md                  # Complete hardware & mathematical specifications
├── shaders/                         # Real-time OpenGL ES GPU Shaders
│   ├── camera_preview.vert          # Texture transform vertex shader
│   ├── focus_peaking.frag           # Sobel edge detection focus assist
│   ├── zebra.frag                   # Highlight exposure zebra stripes
│   └── false_color.frag             # 16-band ARRI IRE false-color heat map
├── src/android/                     # Android Kotlin Core Engine
│   ├── Camera2ManualController.kt   # Sensor ISO/Shutter/Focus/AWB controller
│   ├── HighBitrateMediaCodecRecorder.kt # 120 Mbps HEVC hardware encoder
│   └── ProAudioEngine.kt            # 48 kHz PCM stereo audio & VU meters
└── web_preview/                     # Interactive Shader Simulator
    └── index.html                   # Live WebGL testbed to test assist shaders
```

---

## 🚀 Live Shader Testbed

You can preview the real-time Focus Peaking, Zebra, and False Color shaders directly in your browser:
Double click or open [`web_preview/index.html`](web_preview/index.html) in Chrome or Edge.

---

## 📄 License

Distributed under the [MIT License](LICENSE).
