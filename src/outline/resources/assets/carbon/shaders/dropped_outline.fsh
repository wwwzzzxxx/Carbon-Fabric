#version 330 core

uniform sampler2D Sampler0;
uniform vec4 OutlineColor;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord).a == 0.0) {
        discard;
    }
    fragColor = OutlineColor;
}
