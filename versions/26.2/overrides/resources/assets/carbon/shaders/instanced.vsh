#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in float BakedNormal;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;
layout(location = 6) in mat4 Instance;
layout(location = 10) in mat3 InstanceNormal;
layout(location = 13) in ivec2 Light;
layout(location = 14) in ivec2 Overlay;

uniform mat4 ModelViewMat;
layout(std140) uniform Projection { mat4 ProjMat; };
uniform mat4 TextureMat;
uniform int GlintPass;
uniform int UseOverlay;
uniform int FogShape;
layout(std140) uniform Lighting { vec3 Light0; vec3 Light1; };
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out vec2 vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord;

void main() {
    vec4 position = Instance * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * position;
    vertexDistance = vec2(length(position.xyz), max(length((position.xyz).xz),abs((position.xyz).y)));
    vec3 normal = InstanceNormal * Normal;
    if (BakedNormal == 0.0) normal = normalize(normal);
    normal = trunc(clamp(normal, -1.0, 1.0) * 127.0) / 127.0;
    float light = min(1.0, (max(0.0, dot(Light0, normal)) + max(0.0, dot(Light1, normal))) * 0.6 + 0.4);
    vertexColor = vec4(Color.rgb * light, Color.a);
    lightMapColor = texture(Sampler2, clamp(vec2(max(UV2, Light)) / 256.0 + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    overlayColor = UseOverlay != 0 ? texelFetch(Sampler1, Overlay, 0) : vec4(1.0);
    texCoord = GlintPass == 0 ? UV0 : (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
}
