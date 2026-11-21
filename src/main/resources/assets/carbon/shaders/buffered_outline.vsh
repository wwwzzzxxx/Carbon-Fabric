#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;
layout(location = 6) in mat4 InstancePose;
layout(location = 13) in uint InstanceColor;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 texCoord;
flat out vec3 outlineColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * InstancePose * vec4(Position, 1.0);
    texCoord = UV0;
    outlineColor = vec3((InstanceColor >> 16u) & 255u, (InstanceColor >> 8u) & 255u, InstanceColor & 255u) / 255.0;
}
