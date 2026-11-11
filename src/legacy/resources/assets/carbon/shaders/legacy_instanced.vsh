#version 330 core
layout(location=0) in vec3 Position;
layout(location=1) in vec4 Color;
layout(location=2) in vec2 UV0;
layout(location=4) in ivec2 UV2;
layout(location=5) in vec3 Normal;
layout(location=6) in mat4 Instance;
layout(location=10) in mat3 InstanceNormal;
layout(location=13) in ivec2 Light;
layout(location=14) in ivec2 Overlay;
uniform mat4 ModelViewMat, ProjMat, TextureMat;
uniform int GlintPass, UseLighting, UseLightmap, UseOverlay;
uniform vec3 Light0, Light1;
uniform sampler2D Sampler1, Sampler2;
out vec4 vertexColor, lightColor, overlayColor;
out vec2 texCoord;
out float distanceToEye;
void main() {
    vec4 position = ModelViewMat * Instance * vec4(Position, 1.0);
    gl_Position = ProjMat * position;
    distanceToEye = abs(position.z);
    vec3 normal = mat3(ModelViewMat) * (trunc(clamp(InstanceNormal * Normal, -1.0, 1.0) * 127.0) / 127.0);
    float diffuse = UseLighting != 0 ? min(1.0, 0.4 + 0.6 * (max(dot(Light0, normal), 0.0) + max(dot(Light1, normal), 0.0))) : 1.0;
    vertexColor = vec4(Color.rgb * diffuse, Color.a);
    texCoord = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    lightColor = UseLightmap != 0 ? texelFetch(Sampler2, clamp(max(UV2, Light) / 16, ivec2(0), ivec2(15)), 0) : vec4(1.0);
    overlayColor = UseOverlay != 0 ? texelFetch(Sampler1, Overlay, 0) : vec4(1.0);
}
