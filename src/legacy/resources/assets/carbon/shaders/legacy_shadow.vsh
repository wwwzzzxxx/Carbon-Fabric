#version 330 core
layout(location=0) in vec4 Bounds;
layout(location=1) in vec4 Receiver;
layout(location=2) in vec4 Item;
uniform mat4 ModelViewMat, ProjMat, TextureMat, ViewPose;
uniform mat3 ViewNormal;
uniform vec3 CameraOffset, Light0, Light1;
uniform int UseLighting, UseLightmap, UseOverlay, ClipShadow;
uniform sampler2D Sampler1, Sampler2;
out vec4 vertexColor, lightColor, overlayColor;
out vec2 texCoord;
out float distanceToEye;

void main() {
    vec2 lo = Bounds.xz;
    vec2 hi = vec2(Bounds.w, Receiver.x);
    if (ClipShadow != 0 && TextureMat == mat4(1.0)) {
        lo = max(lo, Receiver.yz - vec2(0.15));
        hi = min(hi, Receiver.yz + vec2(0.15));
        hi = max(lo, hi);
    }
    float x = (gl_VertexID & 2) == 0 ? lo.x : hi.x;
    float z = (gl_VertexID & 1) == 0 ? lo.y : hi.y;
    vec4 position = ModelViewMat * ViewPose * vec4(vec3(x, Bounds.y, z) + CameraOffset, 1.0);
    gl_Position = ProjMat * position;
    distanceToEye = abs(position.z);
    texCoord = (TextureMat * vec4((Receiver.yz - vec2(x, z)) / 0.30 + 0.5, 0.0, 1.0)).xy;
    vec3 offset = Item.yzw + CameraOffset;
    float weight = (1.0 - dot(offset, offset) / 256.0) * 0.75;
    float alpha = floor(clamp((weight - Item.x * 0.5) * 0.5 * Receiver.w, 0.0, 1.0) * 255.0) / 255.0;
    if (weight <= 0.0 || alpha <= 0.0) gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vec3 normal = mat3(ModelViewMat) * (trunc(clamp(ViewNormal[1], -1.0, 1.0) * 127.0) / 127.0);
    float diffuse = UseLighting != 0 ? min(1.0, 0.4 + 0.6 * (max(dot(Light0, normal), 0.0) + max(dot(Light1, normal), 0.0))) : 1.0;
    vertexColor = vec4(vec3(diffuse), alpha);
    lightColor = UseLightmap != 0 ? texelFetch(Sampler2, ivec2(15), 0) : vec4(1.0);
    overlayColor = UseOverlay != 0 ? texelFetch(Sampler1, ivec2(0, 10), 0) : vec4(1.0);
}
