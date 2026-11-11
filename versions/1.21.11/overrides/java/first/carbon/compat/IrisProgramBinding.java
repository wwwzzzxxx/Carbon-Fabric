package first.carbon.compat;

import com.mojang.blaze3d.opengl.GlProgram;
import static org.lwjgl.opengl.GL33.*;

/** Iris can unbind its program during a pack change while the native encoder retains its cache. */
public final class IrisProgramBinding {
    public static long repairs;

    public static void restoreIfMissing(GlProgram program) {
        if (!program.getClass().getName().startsWith("net.irisshaders.iris.pipeline.programs.")) return;
        int expected = program.getProgramId();
        if (expected > 0 && glGetInteger(GL_CURRENT_PROGRAM) == 0 && glIsProgram(expected)) {
            glUseProgram(expected);
            repairs++;
        }
    }
}
