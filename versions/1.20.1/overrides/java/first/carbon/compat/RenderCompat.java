package first.carbon.compat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
public final class RenderCompat {
    private static final boolean sodiumShadows = java.util.Arrays.stream(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredMethods()).anyMatch(method -> method.getName().contains("renderShadowPartFast"));
    public static float shadowHeightFactor() { return sodiumShadows ? 2 : 1; }
    public static org.joml.Matrix4f matrix(org.joml.Matrix4f source) { return new org.joml.Matrix4f(source); }
    public static org.joml.Matrix3f matrix(org.joml.Matrix3f source) { return new org.joml.Matrix3f(source); }
    public static FloatBuffer floats(org.joml.Matrix4f source, FloatBuffer target) { return source.get(target); }
    public static org.joml.Matrix4f minecraft(org.joml.Matrix4fc source) { return new org.joml.Matrix4f(source); }
    public static Mesh finish(BufferBuilder builder) {
        BufferBuilder.RenderedBuffer result = builder.end();
        if (result.drawState().vertexCount() == 0) { result.release(); return null; }
        return new Mesh(result, result.vertexBuffer().order(ByteOrder.nativeOrder()), builder);
    }
    public static void release(BufferBuilder builder) {
        var access = (first.carbon.mixin.BufferBuilderAccessor) builder;
        ByteBuffer buffer = access.carbon$buffer();
        if (buffer != null) {
            org.lwjgl.system.MemoryUtil.getAllocator(false).free(org.lwjgl.system.MemoryUtil.memAddress0(buffer));
            access.carbon$buffer(null);
        }
    }
    public record Mesh(BufferBuilder.RenderedBuffer rendered, ByteBuffer vertexBuffer, BufferBuilder owner) {
        public BufferBuilder.DrawState drawState() { return rendered.drawState(); }
        public void release() { rendered.release(); RenderCompat.release(owner); }
    }
}
