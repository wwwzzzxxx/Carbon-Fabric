#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 PoseRow0;
layout(location = 3) in vec4 PoseRow1;
layout(location = 4) in vec4 PoseRow2;
layout(location = 5) in vec4 OutlineColor;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 texCoord0;

void main() {
    vec4 local = vec4(Position, 1.0);
    vec3 position = vec3(dot(PoseRow0, local), dot(PoseRow1, local), dot(PoseRow2, local));
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexColor = OutlineColor;
    texCoord0 = UV0;
}
