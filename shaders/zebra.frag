#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES uTexture;
uniform float uZebraThreshold; // e.g. 0.70 (70 IRE) or 0.95 (95 IRE)
uniform float uTime;           // Animation time for moving stripes
uniform int uZebraEnabled;     // 1 = Active, 0 = Passthrough

in vec2 vTexCoord;
out vec4 fragColor;

float getLuminance(vec4 color) {
    return dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec4 centerColor = texture(uTexture, vTexCoord);

    if (uZebraEnabled == 0) {
        fragColor = centerColor;
        return;
    }

    float luma = getLuminance(centerColor);

    if (luma >= uZebraThreshold) {
        // Compute diagonal stripes based on screen coordinates and animation
        float stripeCoord = gl_FragCoord.x + gl_FragCoord.y - (uTime * 30.0);
        float stripe = mod(stripeCoord, 16.0);

        if (stripe < 8.0) {
            // Dark zebra line
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        } else {
            // Bright zebra line / original highlight
            fragColor = vec4(1.0, 1.0, 1.0, 1.0);
        }
    } else {
        fragColor = centerColor;
    }
}
