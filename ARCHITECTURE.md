# OpenCinema24: Architecture & Engineering Specification

> Free & Open-Source Manual Cinema Video Camera for Android (mcpro24fps & FiLMiC Pro Alternative)

---

## 1. System Pipeline Overview

```
                      ┌────────────────────────┐
                      │ Android Camera2 Sensor │
                      │  (Level_3 / Full HW)   │
                      └───────────┬────────────┘
                                  │ (OES Texture Stream)
                                  ▼
                      ┌────────────────────────┐
                      │    OpenGL ES 3.0 /     │
                      │   Vulkan GPU Pipeline  │
                      └─────┬────────────┬─────┘
                            │            │
            ┌───────────────┘            └───────────────┐
            ▼                                            ▼
  ┌──────────────────────┐                     ┌────────────────────┐
  │ Monitor Assist View  │                     │ Hardware Recording │
  │  (GLSL Shader Passes)│                     │  (MediaCodec EGL)  │
  ├──────────────────────┤                     ├────────────────────┤
  │ • Focus Peaking      │                     │ • 100-200 Mbps     │
  │ • Zebra 70% / 100%   │                     │ • HEVC / H.264     │
  │ • False Color (IRE)  │                     │ • 10-bit HDR / LOG │
  │ • Live Waveform / VU │                     │ • True 24.000 FPS  │
  └──────────────────────┘                     └─────────┬──────────┘
                                                         │
                                                         ▼
                                               ┌────────────────────┐
                                               │    MediaMuxer      │
                                               │ (MP4 / 48kHz Audio)│
                                               └────────────────────┘
```

---

## 2. Component Specifications

### A. Camera2 Manual Sensor Control (`Camera2Controller.kt`)
Standard camera apps run automatic AE (Auto Exposure) and AWB (Auto White Balance). In cinematography, exposure must remain strictly constant:
- **Frame Rate Lock:** `CONTROL_AE_TARGET_FPS_RANGE = Range(24, 24)` (24.000 fps lock without cadence jitter).
- **180° Cinematic Shutter Rule:** 
  $$\text{Shutter Speed} = \frac{1}{2 \times \text{FPS}} = \frac{1}{48}\text{s} \approx 20,833,333\text{ ns}$$
  Sensor exposure time set via `CaptureRequest.SENSOR_EXPOSURE_TIME`.
- **Manual ISO Sensitivity:** `CaptureRequest.SENSOR_SENSITIVITY = 100` (disables auto ISO ramping).
- **Manual Lens Focus:** `CaptureRequest.CONTROL_AF_MODE = AF_MODE_OFF` with `CaptureRequest.LENS_FOCUS_DISTANCE` (0.0f = infinity, >0.0f = macro).
- **Kelvin & Tint Matrix:** `CaptureRequest.CONTROL_AWB_MODE = AWB_MODE_OFF` with `CaptureRequest.COLOR_CORRECTION_GAINS` (R, G_even, G_odd, B).

### B. Real-Time GPU Assist Shaders (GLSL)
- **Focus Peaking (`focus_peaking.frag`):**
  Uses a 3x3 Sobel operator over pixel luminance to detect edge gradient magnitude:
  $$G = \sqrt{G_x^2 + G_y^2}$$
  If $G > \text{threshold}$, the pixel color is replaced with neon green (`#00FF66`) or neon red (`#FF0055`).
- **Zebra Stripes (`zebra.frag`):**
  Calculates Rec.709 luma: $Y = 0.2126R + 0.7152G + 0.0722B$.
  If $Y > 0.70$ (for skin highlights) or $Y > 0.95$ (blown highlights), alternates diagonal stripes using modulo coordinate arithmetic.
- **False Color (`false_color.frag`):**
  Maps luminance to 16 IRE color bands (Purple for under-exposure, Green for 18% middle grey, Yellow for highlight warning, Red for clipping).

### C. Ultra-High Bitrate MediaCodec Pipeline (`MediaCodecRecorder.kt`)
- Stock camera apps cap video bitrate at 20–30 Mbps, which creates macro-blocking in high-motion scenes.
- OpenCinema24 configures `MediaCodec` with:
  - MIME: `MediaFormat.MIMETYPE_VIDEO_HEVC` (H.265) or `MIMETYPE_VIDEO_AVC`.
  - Bitrate: `150,000,000` (150 Mbps).
  - Bitrate Mode: `BITRATE_MODE_CBR` or `BITRATE_MODE_VBR`.
  - I-Frame Interval: `1` second (GOP length = 24 frames for precise post editing).
- Uses `MediaCodec.createInputSurface()` directly connected to EGL surface for zero-copy GPU encoding.

### D. Professional Audio (`ProAudioEngine.kt`)
- Support for external USB-C Audio Interfaces / Lav Mics (`AudioDeviceInfo.TYPE_USB_DEVICE`).
- Uncompressed 48 kHz, 16-bit PCM stereo capture.
- Real-time RMS (Root-Mean-Square) calculation in decibels ($-60\text{ dB}$ to $0\text{ dBFS}$) for live visual VU peak meters.
