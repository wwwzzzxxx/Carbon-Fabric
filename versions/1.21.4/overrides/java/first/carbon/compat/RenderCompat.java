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

    private static final float[] fog = new float[4];
    public static float fogStart() { return com.mojang.blaze3d.systems.RenderSystem.getShaderFog().start(); }
    public static float fogEnd() { return com.mojang.blaze3d.systems.RenderSystem.getShaderFog().end(); }
    public static int fogShape() { return com.mojang.blaze3d.systems.RenderSystem.getShaderFog().shape().getIndex(); }
    public static float[] fogColor() { var value=com.mojang.blaze3d.systems.RenderSystem.getShaderFog();fog[0]=value.red();fog[1]=value.green();fog[2]=value.blue();fog[3]=value.alpha();return fog; }
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

    public record ResolvedItem(net.minecraft.client.resources.model.BakedModel model, net.minecraft.client.renderer.RenderType type, int[] tints, net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foil) {}
    private static final net.minecraft.client.renderer.item.ItemStackRenderState scratchItem = new net.minecraft.client.renderer.item.ItemStackRenderState();
    private static net.minecraft.world.item.ItemStack resolvedStack;
    private static ResolvedItem resolvedItem;
    public static boolean isStaticGroundItem(net.minecraft.world.item.ItemStack stack, ResolvedItem resolved) {
        if (resolved.tints().length != 0 || resolved.foil() != net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.NONE) return false;
        var id = stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
        if (id == null) return false;
        var model = net.minecraft.client.Minecraft.getInstance().getModelManager().getItemModel(id);
        return model instanceof net.minecraft.client.renderer.item.BlockModelWrapper wrapper
                && wrapper.getClass() == net.minecraft.client.renderer.item.BlockModelWrapper.class
                && wrapper.model == resolved.model() && wrapper.tints.isEmpty();
    }

    public static ResolvedItem resolveGroundItem(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, int seed, ResolvedItem previous) {
        resolvedStack = null; resolvedItem = null;
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.getItemModelResolver().updateForTopItem(scratchItem,stack,net.minecraft.world.item.ItemDisplayContext.GROUND,false,level,null,seed);
        if (scratchItem.activeLayerCount != 1) return null;
        var layer = scratchItem.layers[0];
        if (layer.model == null || layer.renderType == null || layer.specialRenderer != null
                || layer.foilType == net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.SPECIAL) return null;
        resolvedItem = previous != null && previous.model() == layer.model && previous.type() == layer.renderType
                && previous.foil() == layer.foilType && java.util.Arrays.equals(previous.tints(), layer.tintLayers)
                ? previous : new ResolvedItem(layer.model, layer.renderType, layer.tintLayers.clone(), layer.foilType);
        resolvedStack = stack;
        return resolvedItem;
    }
    public static void clearResolved() { resolvedStack = null; resolvedItem = null; scratchItem.clear(); }
    public static ResolvedItem resolved(net.minecraft.world.item.ItemStack stack) { return resolvedStack == stack ? resolvedItem : null; }
    public static void renderItem(net.minecraft.client.resources.model.BakedModel model,int[] tints,net.minecraft.client.renderer.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource source,int light,int overlay) {
        renderItem(model,tints,type,pose,source,light,overlay,false);
    }
    public static void renderItem(net.minecraft.client.resources.model.BakedModel model,int[] tints,net.minecraft.client.renderer.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource source,int light,int overlay,boolean foil) {
        pose.pushPose();
        try {
            model.getTransforms().getTransform(net.minecraft.world.item.ItemDisplayContext.GROUND).apply(false,pose);
            pose.translate(-.5f,-.5f,-.5f);
            net.minecraft.client.renderer.entity.ItemRenderer.renderItem(net.minecraft.world.item.ItemDisplayContext.GROUND,pose,source,light,overlay,tints,model,type,foil ? net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.STANDARD : net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.NONE);
        } finally { pose.popPose(); }
    }
}
