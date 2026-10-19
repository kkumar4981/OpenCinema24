#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES uTexture;
uniform int uFalseColorEnabled;

in vec2 vTexCoord;
out vec4 fragColor;

float getLuminance(vec4 color) {
    return dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
}

vec3 getFalseColor(float luma) {
    // Standard ARRI/Cinema False Color Scale:
    // 0.00 - 0.05 : Crushed Blacks (Purple)
    // 0.05 - 0.20 : Shadows (Blue)
    // 0.38 - 0.45 : 18% Middle Grey (Green)
    // 0.50 - 0.65 : Healthy Caucasian/Normal Skin (Pink / Light Grey)
    // 0.70 - 0.85 : Warm Highlights (Orange)
    // 0.85 - 0.95 : Near-Clipping Warning (Yellow)
    // 0.95 - 1.00 : Blown Out / Clipped (Red)

    if (luma < 0.05) return vec3(0.5, 0.0, 0.5);      // Purple (Crushed)
    if (luma < 0.20) return vec3(0.0, 0.0, 1.0);      // Blue (Shadows)
    if (luma < 0.38) return vec3(0.3, 0.3, 0.4);      // Dark Grey
    if (luma <= 0.45) return vec3(0.0, 0.9, 0.0);     // Green (18% Middle Grey reference)
    if (luma < 0.55) return vec3(0.4, 0.4, 0.5);      // Midtones
    if (luma <= 0.70) return vec3(1.0, 0.6, 0.8);     // Pink (Skin tones)
    if (luma < 0.85) return vec3(1.0, 0.5, 0.0);      // Orange (Highlights)
    if (luma < 0.95) return vec3(1.0, 1.0, 0.0);      // Yellow (Highlight warning)
    return vec3(1.0, 0.0, 0.0);                       // Red (Clipped highlights)
}

void main() {
    vec4 centerColor = texture(uTexture, vTexCoord);

    if (uFalseColorEnabled == 0) {
        fragColor = centerColor;
        return;
    }

    float luma = getLuminance(centerColor);
    fragColor = vec4(getFalseColor(luma), 1.0);
}
