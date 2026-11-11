#version 330 core
uniform sampler2D Sampler0;
uniform int GlintPass, UseFog, FogMode;
uniform vec4 ColorModulator, FogColor;
uniform float FogStart, FogEnd, FogDensity, AlphaCutoff;
in vec4 vertexColor, lightColor, overlayColor;
in vec2 texCoord;
in float distanceToEye;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord);
    if (GlintPass != 0) color *= ColorModulator;
    else {
        color *= vertexColor;
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        color *= lightColor;
    }
    if (color.a <= AlphaCutoff) discard;
    if (UseFog != 0) {
        float fog = FogMode == 2048 ? exp(-FogDensity * distanceToEye)
            : FogMode == 2049 ? exp(-pow(FogDensity * distanceToEye, 2.0))
            : (FogEnd - distanceToEye) / max(FogEnd - FogStart, 0.0001);
        color.rgb = mix(FogColor.rgb, color.rgb, clamp(fog, 0.0, 1.0));
    }
    fragColor = color;
}
