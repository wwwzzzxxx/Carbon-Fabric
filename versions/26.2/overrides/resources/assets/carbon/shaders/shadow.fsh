#version 330 core
uniform sampler2D Sampler0;
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

in vec2 texCoord;
in float alpha;
in vec2 vertexDistance;
out vec4 fragColor;
float carbonLinearFog(float distance,float start,float end) {
    if(distance<=start)return 0.0;
    if(distance>=end)return 1.0;
    return (distance-start)/(end-start);
}
float carbonFog() {return max(carbonLinearFog(vertexDistance.x,FogEnvironmentalStart,FogEnvironmentalEnd),carbonLinearFog(vertexDistance.y,FogRenderDistanceStart,FogRenderDistanceEnd));}
void main() {
    vec4 color = texture(Sampler0, clamp(texCoord, 0.0, 1.0)) * vec4(1.0, 1.0, 1.0, alpha) * ColorModulator;
    color.rgb = mix(color.rgb, FogColor.rgb, carbonFog() * FogColor.a);
    fragColor = color;
}
