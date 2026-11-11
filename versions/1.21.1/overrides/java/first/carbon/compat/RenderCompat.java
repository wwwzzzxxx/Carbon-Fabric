package first.carbon.compat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
public final class RenderCompat {
    private static final boolean fastShadows = java.util.Arrays.stream(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredMethods()).anyMatch(method -> method.getName().contains("renderShadowPartFast"));
    public static float shadowHeightFactor() { return fastShadows ? 2 : 1; }

    private static final java.util.IdentityHashMap<BufferBuilder, ByteBufferBuilder> owners = new java.util.IdentityHashMap<>();
    public static BufferBuilder builder(int size, VertexFormat.Mode mode, VertexFormat format) {
        ByteBufferBuilder storage = new ByteBufferBuilder(size);
        try {
            BufferBuilder builder = new BufferBuilder(storage, mode, format);
            owners.put(builder, storage);
            return builder;
        } catch (RuntimeException | Error exception) { storage.close(); throw exception; }
    }
    public static org.joml.Matrix4f matrix(org.joml.Matrix4f source) { return new org.joml.Matrix4f(source); }
    public static org.joml.Matrix3f matrix(org.joml.Matrix3f source) { return new org.joml.Matrix3f(source); }
    public static FloatBuffer floats(org.joml.Matrix4f source, FloatBuffer target) { return source.get(target); }
    public static org.joml.Matrix4f minecraft(org.joml.Matrix4fc source) { return new org.joml.Matrix4f(source); }
    public static Mesh finish(BufferBuilder builder) {
        MeshData result = builder.build();
        if (result == null) return null;
        return new Mesh(result, result.vertexBuffer().order(ByteOrder.nativeOrder()), builder);
    }
    public static void release(BufferBuilder builder) {
        ByteBufferBuilder storage = owners.remove(builder);
        if (storage != null) storage.close();
    }
    public record Mesh(MeshData rendered, ByteBuffer vertexBuffer, BufferBuilder owner) {
        public MeshData.DrawState drawState() { return rendered.drawState(); }
        public void release() { try { rendered.close(); } finally { RenderCompat.release(owner); } }
    }
}
