#version 330 core

uniform sampler2D Sampler0;
uniform int GlintPass;
uniform float GlintAlpha;
uniform vec4 ColorModulator;
layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};


in vec2 vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord;
out vec4 fragColor;

float carbonLinearFog(float distance,float start,float end) {
    if(distance<=start)return 0.0;
    if(distance>=end)return 1.0;
    return (distance-start)/(end-start);
}
float carbonFog() {return max(carbonLinearFog(vertexDistance.x,FogEnvironmentalStart,FogEnvironmentalEnd),carbonLinearFog(vertexDistance.y,FogRenderDistanceStart,FogRenderDistanceEnd));}
void main() {
    vec4 color = texture(Sampler0, texCoord);
    if (color.a < 0.1) discard;
    if (GlintPass != 0) {
        color *= ColorModulator;
        float fade = 1.0 - carbonFog();
        fragColor = vec4(color.rgb * fade * GlintAlpha, color.a);
    } else {
        color *= vertexColor * ColorModulator;
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        color *= lightMapColor;
        color.rgb = mix(color.rgb, FogColor.rgb, carbonFog() * FogColor.a);
        fragColor = color;
    }
}
