package first.carbon.client;

import first.carbon.compat.RenderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

final class DroppedItemOutline {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon");
    private int program;
    private int preparedProgram;
    private boolean unavailable;
    private int color;
    private int submissions;
    private int draws;

    void beginFrame() {
        color = 0;
        submissions = 0;
        draws = 0;
    }

    void beginSubmission(DroppedItemBatchRenderer renderer, List<EntityRenderState> states) {
        if (unavailable || Boolean.getBoolean("carbon.test.disableSharedItemOutline")) return;
        int sharedColor = 0;
        int count = 0;
        for (EntityRenderState state : states) {
            if (!state.appearsGlowing()) continue;
            // The outline target resolves overlaps in draw order. Reordering is safe for one color.
            if (!(state instanceof ItemEntityRenderState) || sharedColor != 0 && state.outlineColor != sharedColor) return;
            sharedColor = state.outlineColor;
            count++;
        }
        if (count >= 8 && prepare(renderer)) color = sharedColor;
    }

    boolean accepts(int outlineColor) {
        return color != 0 && color == outlineColor;
    }

    static RenderType type(RenderType baseType) {
        var type = baseType.outline().orElse(null);
        if (type == null) return null;
        var pipeline = type.pipeline();
        return pipeline == RenderPipelines.OUTLINE_CULL || pipeline == RenderPipelines.OUTLINE_NO_CULL ? type : null;
    }

    boolean submit() {
        return submissions++ == 0;
    }

    String statistics() {
        return submissions + " shared outline submits/" + draws + " shared outline draws";
    }

    void draw(Iterable<List<GpuDroppedItemBatch>> buckets, Matrix4f view, Matrix4f projection,
              float cameraX, float cameraY, float cameraZ, float clock) {
        if (submissions == 0 || draws != 0) return;
        int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try (MemoryStack memory = MemoryStack.stackPush()) {
            var matrix = memory.mallocFloat(16);
            for (List<GpuDroppedItemBatch> bucket : buckets) {
                for (GpuDroppedItemBatch batch : bucket) {
                    if (batch.outlineType == null || !batch.flushed || batch.count == 0) continue;
                    int mask = batch.processed ? preparedProgram : program;
                    RenderCompat.setupType(batch.outlineType);
                    try {
                        glUseProgram(mask);
                        glUniform1i(glGetUniformLocation(mask, "Sampler0"), 0);
                        glUniform4f(glGetUniformLocation(mask, "OutlineColor"),
                                ARGB.red(color) / 255f, ARGB.green(color) / 255f, ARGB.blue(color) / 255f, 1);
                        glUniformMatrix4fv(glGetUniformLocation(mask, "View"), false, view.get(matrix));
                        glUniformMatrix4fv(glGetUniformLocation(mask, "Projection"), false, projection.get(matrix));
                        glUniform3f(glGetUniformLocation(mask, "Camera"), cameraX, cameraY, cameraZ);
                        glUniform1f(glGetUniformLocation(mask, "Clock"), clock);
                        glUniform1f(glGetUniformLocation(mask, "GroundScaleY"), batch.groundScaleY);
                        RenderCompat.bindShared(mask);
                        for (GpuDroppedItemBatch.Mesh mesh : batch.meshes) {
                            mesh.bind();
                            glDrawElementsInstanced(GL_TRIANGLES, mesh.indices, GL_UNSIGNED_INT, 0, batch.count);
                            draws++;
                        }
                    } finally {
                        RenderCompat.clearType(batch.outlineType);
                    }
                }
            }
        } finally {
            glUseProgram(previousProgram);
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, buffer);
        }
    }

    private boolean prepare(DroppedItemBatchRenderer renderer) {
        if (program != 0) return true;
        var resources = Minecraft.getInstance().getResourceManager();
        for (String ext : List.of("vsh", "fsh")) {
            var id = Identifier.withDefaultNamespace("shaders/core/rendertype_outline." + ext);
            if (resources.getResource(id).filter(resource -> OutlineShader.isBuiltin(resource.sourcePackId())).isEmpty()) {
                unavailable = true;
                return false;
            }
        }
        int fragment = 0;
        try {
            fragment = renderer.compile(GL_FRAGMENT_SHADER, "dropped_outline.fsh");
            program = renderer.createProgram("entity_instanced.vsh", fragment);
            preparedProgram = renderer.createProgram("entity_prepared.vsh", fragment);
            return true;
        } catch (IOException exception) {
            invalidate();
            unavailable = true;
            LOGGER.warn("Shared item outline shader unavailable", exception);
            return false;
        } finally {
            if (fragment != 0) glDeleteShader(fragment);
        }
    }

    void invalidate() {
        if (program != 0) glDeleteProgram(program);
        if (preparedProgram != 0) glDeleteProgram(preparedProgram);
        program = 0;
        preparedProgram = 0;
        unavailable = false;
        beginFrame();
    }
}
