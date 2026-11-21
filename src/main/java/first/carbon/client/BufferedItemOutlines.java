package first.carbon.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.compat.RenderCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/** Shares the model mesh and packed pose with the buffered item pass. */
public final class BufferedItemOutlines {
    private static final int MATRIX_BYTES = 64;
    private static final int STRIDE = MATRIX_BYTES + Integer.BYTES;
    private static final int MAX_INSTANCES = 65536;

    private final List<Run> runs = new ArrayList<>();
    private MultiBufferSource.BufferSource source;
    private RenderType type;
    private ByteBuffer data;
    private int runCount, count, submissions, draws;
    private int program, buffer, vao, viewUniform, projectionUniform, colorUniform, samplerUniform;
    private boolean failed;

    public interface OutlineVertex {
        VertexConsumer carbon$delegate();
        void carbon$delegate(VertexConsumer delegate);
        int carbon$color();
    }

    public interface OutlineBuffer {
        int carbon$vertexCount();
    }

    static OutlineVertex mask(ItemBatchRenderer.VertexPair pair) {
        if (pair.imo$first() instanceof OutlineVertex) return (OutlineVertex) pair.imo$first();
        return pair.imo$second() instanceof OutlineVertex ? (OutlineVertex) pair.imo$second() : null;
    }

    boolean prepare(OutlineVertex mask, ItemBatchRenderer.Target body) {
        if (Carbon.Client.IRIS.active() || failed
                || Boolean.getBoolean("carbon.test.disableSharedItemOutline")) return false;
        VertexConsumer vertices = mask.carbon$delegate();
        if (!(vertices instanceof ItemBatchRenderer.Target) || !(vertices instanceof OutlineBuffer)) return false;
        ItemBatchRenderer.Target target = (ItemBatchRenderer.Target) vertices;
        MultiBufferSource.BufferSource owner = target.imo$source();
        RenderType layer = target.imo$type();
        if (owner == null || owner == body.imo$source() || layer == null || !layer.isOutline()
                || Carbon.Client.IRIS.unwrap(body.imo$type()).outline().orElse(null) != layer
                || (count != 0 && (owner != source || layer != type))
                || count == MAX_INSTANCES || !ensureProgram()) return false;

        // The vanilla mask has no depth test. Drain earlier native vertices before enqueuing a GPU run.
        if (((OutlineBuffer) vertices).carbon$vertexCount() != 0) {
            owner.endBatch(layer);
            vertices = owner.getBuffer(layer);
            mask.carbon$delegate(vertices);
            if (!(vertices instanceof ItemBatchRenderer.Target)) return false;
        }
        source = owner;
        type = layer;
        return true;
    }

    void append(GpuItemBatch mesh, ByteBuffer body, int offset, int color) {
        int size = (count + 1) * STRIDE;
        if (data == null) {
            data = MemoryUtil.memAlloc(64 * STRIDE);
        } else if (size > data.capacity()) {
            data = MemoryUtil.memRealloc(data, data.capacity() * 2);
        }
        int start = count * STRIDE;
        MemoryUtil.memCopy(MemoryUtil.memAddress(body) + offset, MemoryUtil.memAddress(data) + start, MATRIX_BYTES);
        data.putInt(start + MATRIX_BYTES, color);
        Run run = runCount == 0 ? null : runs.get(runCount - 1);
        if (run == null || run.mesh != mesh) {
            if (runCount == runs.size()) runs.add(new Run());
            run = runs.get(runCount++);
            run.mesh = mesh;
            run.offset = start;
            run.count = 0;
        }
        run.count++;
        count++;
        submissions++;
    }

    public void beforeNative(VertexConsumer vertices) {
        if (count != 0 && vertices instanceof ItemBatchRenderer.Target) {
            ItemBatchRenderer.Target target = (ItemBatchRenderer.Target) vertices;
            flush(target.imo$source(), target.imo$type());
        }
    }

    void flush(MultiBufferSource.BufferSource owner, RenderType layer) {
        if (count == 0 || source != owner || type != layer) return;
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        layer.setupRenderState();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUseProgram(program);
            FloatBuffer matrix = stack.mallocFloat(16);
            applyUniforms(matrix);
            if (buffer == 0) buffer = glGenBuffers();
            bindVertexArray();
            glBindBuffer(GL_ARRAY_BUFFER, buffer);
            ByteBuffer upload = data.duplicate();
            upload.limit(count * STRIDE);
            glBufferData(GL_ARRAY_BUFFER, upload, GL_STREAM_DRAW);
            for (int i = 0; i < runCount; i++) {
                Run run = runs.get(i);
                glBindBuffer(GL_ARRAY_BUFFER, run.mesh.vertexBuffer);
                int stride = run.mesh.format.getVertexSize();
                glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
                glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 16);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, run.mesh.indexBuffer);
                glBindBuffer(GL_ARRAY_BUFFER, buffer);
                for (int column = 0; column < 4; column++) {
                    glVertexAttribPointer(6 + column, 4, GL_FLOAT, false, STRIDE, run.offset + column * 16L);
                }
                glVertexAttribIPointer(13, 1, GL_UNSIGNED_INT, STRIDE, run.offset + MATRIX_BYTES);
                glDrawElementsInstanced(GL_TRIANGLES, run.mesh.indices, GL_UNSIGNED_INT, 0, run.count);
                draws++;
            }
        } finally {
            layer.clearRenderState();
            Carbon.Client.IRIS.restoreProgram(previousProgram);
            glBindVertexArray(previousVao);
            glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
            reset();
        }
    }

    private void bindVertexArray() {
        if (vao != 0) {
            glBindVertexArray(vao);
            return;
        }
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(2);
        for (int column = 0; column < 4; column++) {
            glEnableVertexAttribArray(6 + column);
            glVertexAttribDivisor(6 + column, 1);
        }
        glEnableVertexAttribArray(13);
        glVertexAttribDivisor(13, 1);
    }

    private void applyUniforms(FloatBuffer matrix) {
        glUniformMatrix4fv(viewUniform, false, RenderCompat.floats(RenderSystem.getModelViewMatrix(), matrix));
        glUniformMatrix4fv(projectionUniform, false, RenderCompat.floats(RenderSystem.getProjectionMatrix(), matrix));
        glUniform4fv(colorUniform, RenderSystem.getShaderColor());
        glUniform1i(samplerUniform, 0);
        int activeTexture = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL_TEXTURE0);
        GlStateManager._bindTexture(RenderSystem.getShaderTexture(0));
        GlStateManager._activeTexture(activeTexture);
    }

    private boolean ensureProgram() {
        if (program != 0) return true;
        int vertex = 0, fragment = 0;
        try {
            vertex = ItemBatchRenderer.compile(GL_VERTEX_SHADER, "buffered_outline.vsh");
            fragment = ItemBatchRenderer.compile(GL_FRAGMENT_SHADER, "buffered_outline.fsh");
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(program));
            viewUniform = glGetUniformLocation(program, "ModelViewMat");
            projectionUniform = glGetUniformLocation(program, "ProjMat");
            colorUniform = glGetUniformLocation(program, "ColorModulator");
            samplerUniform = glGetUniformLocation(program, "Sampler0");
            return true;
        } catch (Exception e) {
            if (program != 0) glDeleteProgram(program);
            program = 0;
            failed = true;
            System.err.println("Carbon outline instancing unavailable: " + e);
            return false;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
    }

    private void reset() {
        for (int i = 0; i < runCount; i++) runs.get(i).mesh = null;
        count = runCount = 0;
        source = null;
        type = null;
    }

    void beginFrame() {
        reset();
        submissions = draws = 0;
    }

    void clear() {
        reset();
        if (data != null) MemoryUtil.memFree(data);
        if (buffer != 0) glDeleteBuffers(buffer);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (program != 0) glDeleteProgram(program);
        data = null;
        buffer = vao = program = 0;
        failed = false;
    }

    String statistics() {
        return ", " + submissions + " shared outline submits/" + draws + " shared outline draws";
    }

    private static final class Run {
        GpuItemBatch mesh;
        int offset;
        int count;
    }
}
