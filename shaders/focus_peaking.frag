#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES uTexture;
uniform vec2 uTexelSize;       // 1.0 / resolution (e.g. 1.0/1920.0, 1.0/1080.0)
uniform float uThreshold;      // Edge sensitivity (e.g. 0.25)
uniform vec3 uPeakingColor;    // Neon Green (0.0, 1.0, 0.4) or Red (1.0, 0.0, 0.3)
uniform int uPeakingEnabled;   // 1 = Active, 0 = Passthrough

in vec2 vTexCoord;
out vec4 fragColor;

float getLuminance(vec4 color) {
    return dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec4 centerColor = texture(uTexture, vTexCoord);

    if (uPeakingEnabled == 0) {
        fragColor = centerColor;
        return;
    }

    // 3x3 Sobel Edge Detection Filter
    float topLeft      = getLuminance(texture(uTexture, vTexCoord + vec2(-uTexelSize.x,  uTexelSize.y)));
    float top          = getLuminance(texture(uTexture, vTexCoord + vec2(          0.0,  uTexelSize.y)));
    float topRight     = getLuminance(texture(uTexture, vTexCoord + vec2( uTexelSize.x,  uTexelSize.y)));
    float left         = getLuminance(texture(uTexture, vTexCoord + vec2(-uTexelSize.x,           0.0)));
    float right        = getLuminance(texture(uTexture, vTexCoord + vec2( uTexelSize.x,           0.0)));
    float bottomLeft   = getLuminance(texture(uTexture, vTexCoord + vec2(-uTexelSize.x, -uTexelSize.y)));
    float bottom       = getLuminance(texture(uTexture, vTexCoord + vec2(          0.0, -uTexelSize.y)));
    float bottomRight  = getLuminance(texture(uTexture, vTexCoord + vec2( uTexelSize.x, -uTexelSize.y)));

    // Sobel gradients
    float gx = -topLeft - 2.0 * left - bottomLeft + topRight + 2.0 * right + bottomRight;
    float gy = -topLeft - 2.0 * top - topRight + bottomLeft + 2.0 * bottom + bottomRight;

    float gradient = sqrt(gx * gx + gy * gy);

    // If edge sharpness exceeds threshold, highlight in peaking color
    if (gradient > uThreshold) {
        fragColor = vec4(uPeakingColor, 1.0);
    } else {
        fragColor = centerColor;
    }
}
