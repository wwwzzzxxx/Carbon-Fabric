#version 330 core

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord;
flat in vec3 outlineColor;
out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord).a == 0.0) discard;
    fragColor = vec4(ColorModulator.rgb * outlineColor, ColorModulator.a);
}
