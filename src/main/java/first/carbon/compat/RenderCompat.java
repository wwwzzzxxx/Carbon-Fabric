package first.carbon.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryStack;

public final class RenderCompat {
    public static org.joml.Matrix4f matrix(com.mojang.math.Matrix4f source) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer data = stack.mallocFloat(16);
            source.store(data);
            return new org.joml.Matrix4f(data);
        }
    }

    public static org.joml.Matrix3f matrix(com.mojang.math.Matrix3f source) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer data = stack.mallocFloat(9);
            source.store(data);
            return new org.joml.Matrix3f(data);
        }
    }

    public static FloatBuffer floats(com.mojang.math.Matrix4f source, FloatBuffer target) {
        source.store(target);
        return target;
    }

    public static com.mojang.math.Matrix4f minecraft(org.joml.Matrix4fc source) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            com.mojang.math.Matrix4f result = new com.mojang.math.Matrix4f();
            result.load(source.get(stack.mallocFloat(16)));
            return result;
        }
    }

    public static Mesh finish(BufferBuilder builder) {
        builder.end();
        var result = builder.popNextBuffer();
        var state = result.getFirst();
        return state.vertexCount() == 0 ? null : new Mesh(state, result.getSecond().slice(0, state.vertexBufferSize()).order(ByteOrder.nativeOrder()), builder);
    }

    // Only for capture builders owned by Carbon; vanilla's buffers have a separate lifetime.
    public static void release(BufferBuilder builder) {
        var access = (first.carbon.mixin.BufferBuilderAccessor) builder;
        ByteBuffer buffer = access.carbon$buffer();
        if (buffer != null) {
            org.lwjgl.system.MemoryUtil.getAllocator(false).free(org.lwjgl.system.MemoryUtil.memAddress0(buffer));
            access.carbon$buffer(null);
        }
    }

    public record Mesh(BufferBuilder.DrawState drawState, ByteBuffer vertexBuffer, BufferBuilder owner) {
        public void release() { RenderCompat.release(owner); }
    }
}
