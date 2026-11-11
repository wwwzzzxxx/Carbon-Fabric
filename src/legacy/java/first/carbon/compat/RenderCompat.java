package first.carbon.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class RenderCompat {
    public static ByteBuffer slice(ByteBuffer source, int offset, int size) {
        ByteBuffer result = source.duplicate().order(ByteOrder.nativeOrder());
        result.position(offset);
        result.limit(offset + size);
        return result.slice().order(ByteOrder.nativeOrder());
    }

    public static Mesh finish(BufferBuilder builder) {
        builder.end();
        com.mojang.datafixers.util.Pair<BufferBuilder.DrawState, ByteBuffer> data = builder.popNextBuffer();
        return data.getFirst().vertexCount() == 0 ? null : new Mesh(data.getFirst(), slice(data.getSecond(), 0,
                data.getFirst().vertexCount() * data.getFirst().format().getVertexSize()), builder);
    }

    public static void release(BufferBuilder builder) {
        first.carbon.mixin.BufferBuilderAccessor access = (first.carbon.mixin.BufferBuilderAccessor) builder;
        ByteBuffer buffer = access.carbon$buffer();
        if (buffer != null) {
            // 1.16 MemoryTracker uses allocateDirect; its cleaner owns the allocation.
            access.carbon$buffer(null);
        }
    }

    public static void load(com.mojang.math.Matrix3f matrix, java.nio.FloatBuffer data) {
        first.carbon.mixin.Matrix3Accessor target = (first.carbon.mixin.Matrix3Accessor) (Object) matrix;
        target.carbon$set00(data.get(0));
        target.carbon$set10(data.get(1));
        target.carbon$set20(data.get(2));
        target.carbon$set01(data.get(3));
        target.carbon$set11(data.get(4));
        target.carbon$set21(data.get(5));
        target.carbon$set02(data.get(6));
        target.carbon$set12(data.get(7));
        target.carbon$set22(data.get(8));
    }
    public static void store(com.mojang.math.Matrix3f matrix, java.nio.FloatBuffer data) {
        first.carbon.mixin.Matrix3Accessor source = (first.carbon.mixin.Matrix3Accessor) (Object) matrix;
        data.put(0, source.carbon$get00());
        data.put(1, source.carbon$get10());
        data.put(2, source.carbon$get20());
        data.put(3, source.carbon$get01());
        data.put(4, source.carbon$get11());
        data.put(5, source.carbon$get21());
        data.put(6, source.carbon$get02());
        data.put(7, source.carbon$get12());
        data.put(8, source.carbon$get22());
    }
    public static void load(com.mojang.math.Matrix4f matrix, java.nio.FloatBuffer data) {
        first.carbon.mixin.Matrix4Accessor target = (first.carbon.mixin.Matrix4Accessor) (Object) matrix;
        target.carbon$set00(data.get(0));
        target.carbon$set10(data.get(1));
        target.carbon$set20(data.get(2));
        target.carbon$set30(data.get(3));
        target.carbon$set01(data.get(4));
        target.carbon$set11(data.get(5));
        target.carbon$set21(data.get(6));
        target.carbon$set31(data.get(7));
        target.carbon$set02(data.get(8));
        target.carbon$set12(data.get(9));
        target.carbon$set22(data.get(10));
        target.carbon$set32(data.get(11));
        target.carbon$set03(data.get(12));
        target.carbon$set13(data.get(13));
        target.carbon$set23(data.get(14));
        target.carbon$set33(data.get(15));
    }

    public static final class Mesh {
        private final BufferBuilder.DrawState state;
        private final ByteBuffer vertices;
        private final BufferBuilder owner;
        Mesh(BufferBuilder.DrawState state, ByteBuffer vertices, BufferBuilder owner) { this.state = state; this.vertices = vertices; this.owner = owner; }
        public BufferBuilder.DrawState drawState() { return state; }
        public ByteBuffer vertexBuffer() { return vertices; }
        public void release() { RenderCompat.release(owner); }
    }
}
