package first.carbon.client;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL33.*;

final class LegacyState {
    void apply(int program, boolean glint) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer data = stack.mallocFloat(16);
            glUseProgram(program);
            glGetFloatv(GL_MODELVIEW_MATRIX, data); glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, data);
            glGetFloatv(GL_PROJECTION_MATRIX, data); glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, data);
            int active = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0); glGetFloatv(GL_TEXTURE_MATRIX, data); glUniformMatrix4fv(glGetUniformLocation(program, "TextureMat"), false, data);
            glActiveTexture(GL_TEXTURE1); glUniform1i(glGetUniformLocation(program, "UseOverlay"), glIsEnabled(GL_TEXTURE_2D) ? 1 : 0);
            glActiveTexture(GL_TEXTURE2); glUniform1i(glGetUniformLocation(program, "UseLightmap"), glIsEnabled(GL_TEXTURE_2D) ? 1 : 0);
            glActiveTexture(active);
            glGetLightfv(GL_LIGHT0, GL_POSITION, data); glUniform3f(glGetUniformLocation(program, "Light0"), data.get(0), data.get(1), data.get(2));
            glGetLightfv(GL_LIGHT1, GL_POSITION, data); glUniform3f(glGetUniformLocation(program, "Light1"), data.get(0), data.get(1), data.get(2));
            glUniform1i(glGetUniformLocation(program, "UseLighting"), glIsEnabled(GL_LIGHTING) ? 1 : 0);
            glUniform1i(glGetUniformLocation(program, "UseFog"), glIsEnabled(GL_FOG) ? 1 : 0);
            glUniform1i(glGetUniformLocation(program, "FogMode"), glGetInteger(GL_FOG_MODE));
            glUniform1f(glGetUniformLocation(program, "FogDensity"), glGetFloat(GL_FOG_DENSITY));
            glUniform1f(glGetUniformLocation(program, "FogStart"), glGetFloat(GL_FOG_START));
            glUniform1f(glGetUniformLocation(program, "FogEnd"), glGetFloat(GL_FOG_END));
            glGetFloatv(GL_FOG_COLOR, data); glUniform4f(glGetUniformLocation(program, "FogColor"), data.get(0), data.get(1), data.get(2), data.get(3));
            glGetFloatv(GL_CURRENT_COLOR, data); glUniform4f(glGetUniformLocation(program, "ColorModulator"), data.get(0), data.get(1), data.get(2), data.get(3));
            glUniform1i(glGetUniformLocation(program, "GlintPass"), glint ? 1 : 0);
            glUniform1f(glGetUniformLocation(program, "AlphaCutoff"), glIsEnabled(GL_ALPHA_TEST) ? glGetFloat(GL_ALPHA_TEST_REF) : 0);
            for (int unit = 0; unit < 3; unit++) glUniform1i(glGetUniformLocation(program, "Sampler" + unit), unit);
        }
    }
}
