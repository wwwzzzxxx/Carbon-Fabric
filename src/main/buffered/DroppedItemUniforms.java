package first.carbon.client;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;

// Carbon owns these programs. Resolve their locations and assign sampler units once per link.
final class DroppedItemUniforms {
    final int view, projection, textureMatrix;
    final int glintPass, glintAlpha, camera, clock, groundScaleY;
    final int center, halfSize, flat;
    final int color, fogColor, fogStart, fogEnd, fogShape;
    final int light0, light1, outlineColor;

    DroppedItemUniforms(int program) {
        view = glGetUniformLocation(program, "View");
        projection = glGetUniformLocation(program, "Projection");
        textureMatrix = glGetUniformLocation(program, "TextureMat");
        glintPass = glGetUniformLocation(program, "GlintPass");
        glintAlpha = glGetUniformLocation(program, "GlintAlpha");
        camera = glGetUniformLocation(program, "Camera");
        clock = glGetUniformLocation(program, "Clock");
        groundScaleY = glGetUniformLocation(program, "GroundScaleY");
        center = glGetUniformLocation(program, "Center");
        halfSize = glGetUniformLocation(program, "HalfSize");
        flat = glGetUniformLocation(program, "Flat");
        color = glGetUniformLocation(program, "ColorModulator");
        fogColor = glGetUniformLocation(program, "FogColor");
        fogStart = glGetUniformLocation(program, "FogStart");
        fogEnd = glGetUniformLocation(program, "FogEnd");
        fogShape = glGetUniformLocation(program, "FogShape");
        light0 = glGetUniformLocation(program, "Light0");
        light1 = glGetUniformLocation(program, "Light1");
        outlineColor = glGetUniformLocation(program, "OutlineColor");
        for (int unit = 0; unit < 3; unit++) {
            glUniform1i(glGetUniformLocation(program, "Sampler" + unit), unit);
        }
    }
}
