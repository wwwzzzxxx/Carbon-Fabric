#version 330 core
layout(location=0) in vec3 Position;
layout(location=1) in vec4 Color;
layout(location=2) in vec2 UV0;
layout(location=4) in ivec2 UV2;
layout(location=5) in vec3 Normal;
layout(location=6) in vec3 PreviousPosition;
layout(location=7) in vec3 CurrentPosition;
layout(location=8) in vec2 AgePhase;
layout(location=9) in ivec2 Light;

uniform mat4 ModelViewMat, ProjMat, TextureMat, ViewPose, GroundPose;
uniform mat3 ViewNormal, GroundNormal;
uniform float PartialTick, GroundHeight, AgeOffset;
uniform vec3 CameraOffset;
uniform vec3 CopyOffsets[5];
uniform int Copies, UseLighting, UseLightmap, UseOverlay;
uniform vec3 Light0, Light1;
uniform sampler2D Sampler1, Sampler2;
out vec4 vertexColor, lightColor, overlayColor;
out vec2 texCoord;
out float distanceToEye;
out vec3 itemPosition;

void main() {
    float age = (AgePhase.x + AgeOffset) + PartialTick;
    float halfAngle = (age / 20.0 + AgePhase.y) * 0.5;
    float q = sin(halfAngle);
    float c = 1.0 - 2.0 * q * q;
    float s = 2.0 * q * cos(halfAngle);
    mat3 spin = mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
    float bobPhase = float(int((age / 10.0 + AgePhase.y) * 10430.378) & 65535) * 0.0000958738;
    itemPosition = mix(PreviousPosition, CurrentPosition, PartialTick) + CameraOffset;
    itemPosition.y += sin(bobPhase) * 0.1 + 0.1 + GroundHeight;
    vec3 modelPosition = (GroundPose * vec4(Position, 1.0)).xyz + CopyOffsets[gl_InstanceID % Copies];
    vec4 position = ModelViewMat * ViewPose * vec4(itemPosition + spin * modelPosition, 1.0);
    gl_Position = ProjMat * position;
    distanceToEye = abs(position.z);

    vec3 normal = ViewNormal * spin * GroundNormal * Normal;
    normal = mat3(ModelViewMat) * (trunc(clamp(normal, -1.0, 1.0) * 127.0) / 127.0);
    float diffuse = UseLighting != 0 ? min(1.0, 0.4 + 0.6 * (max(dot(Light0, normal), 0.0) + max(dot(Light1, normal), 0.0))) : 1.0;
    vertexColor = vec4(Color.rgb * diffuse, Color.a);
    texCoord = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    lightColor = UseLightmap != 0 ? texelFetch(Sampler2, clamp(max(UV2, Light) / 16, ivec2(0), ivec2(15)), 0) : vec4(1.0);
    overlayColor = UseOverlay != 0 ? texelFetch(Sampler1, ivec2(0, 10), 0) : vec4(1.0);
}
