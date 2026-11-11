package first.carbon.compat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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

    public record ResolvedItem(ItemGeometry model, RenderType type, int[] tints,
                               ItemStackRenderState.FoilType foil, Supplier<?> extents) {}
    private static final ItemStackRenderState scratchItem = new ItemStackRenderState();
    private static ItemStack resolvedStack;
    private static ResolvedItem resolvedItem;

    public static Supplier<?> staticGroundExtents(ItemStack stack) {
        var id = stack.get(DataComponents.ITEM_MODEL);
        if (id == null) return null;
        var model = Minecraft.getInstance().getModelManager().getItemModel(id);
        return model != null && model.getClass() == BlockModelWrapper.class ? ((BlockModelWrapper) model).extents : null;
    }

    public static boolean isStaticGroundItem(ItemStack stack, ResolvedItem resolved, Supplier<?> staticExtents) {
        if (staticExtents == null || resolved.extents() != staticExtents || resolved.tints().length != 0
                || resolved.foil() != ItemStackRenderState.FoilType.NONE) return false;
        var model = Minecraft.getInstance().getModelManager().getItemModel(stack.get(DataComponents.ITEM_MODEL));
        return model instanceof BlockModelWrapper wrapper && wrapper.extents == staticExtents
                && wrapper.tints.isEmpty() && sameQuads(resolved.model().quads, wrapper.quads);
    }

    public static ResolvedItem resolveItem(ItemStack stack, ClientLevel level, int seed,
                                           Supplier<?> staticExtents, ResolvedItem previous) {
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(scratchItem, stack, ItemDisplayContext.GROUND, level, null, seed);
        return resolveItem(stack, scratchItem, staticExtents, previous);
    }

    public static ResolvedItem resolveItem(ItemStack stack, ItemStackRenderState state,
                                           Supplier<?> staticExtents, ResolvedItem previous) {
        resolvedStack = null;
        resolvedItem = null;
        if (state.activeLayerCount != 1) return null;
        var layer = state.layers[0];
        if (layer.quads.isEmpty() || layer.renderType == null || layer.specialRenderer != null
                || layer.foilType == ItemStackRenderState.FoilType.SPECIAL) return null;
        boolean sameMaterial = previous != null && previous.type() == layer.renderType
                && previous.foil() == layer.foilType && Arrays.equals(previous.tints(), layer.tintLayers);
        // Compare the evaluated layer so resource packs and context-dependent item models keep their appearance.
        if (sameMaterial && staticExtents != null && layer.extents == staticExtents && previous.extents() == staticExtents
                && previous.model().transform == layer.transform && sameQuads(previous.model().quads, layer.quads)) {
            resolvedItem = previous;
        } else {
            var bounds = new net.minecraft.world.phys.AABB.Builder();
            state.visitExtents(bounds::include);
            var model = ItemGeometry.get(layer.quads, layer.transform, bounds.build());
            resolvedItem = sameMaterial && previous.model() == model && previous.extents() == layer.extents
                    ? previous : new ResolvedItem(model, layer.renderType, layer.tintLayers.clone(), layer.foilType, layer.extents);
        }
        resolvedStack = stack;
        return resolvedItem;
    }

    private static boolean sameQuads(List<BakedQuad> cached, List<BakedQuad> current) {
        if (cached.size() != current.size()) return false;
        for (int i = 0; i < cached.size(); i++) if (cached.get(i) != current.get(i)) return false;
        return true;
    }

    public static void clearResolved() { resolvedStack = null; resolvedItem = null; scratchItem.clear(); }
    public static ResolvedItem resolved(net.minecraft.world.item.ItemStack stack) { return resolvedStack == stack ? resolvedItem : null; }
    public static void renderItem(first.carbon.compat.ItemGeometry model,int[] tints,net.minecraft.client.renderer.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource source,int light,int overlay) {
        renderItem(model,tints,type,pose,source,light,overlay,false);
    }
    public static void renderItem(first.carbon.compat.ItemGeometry model,int[] tints,net.minecraft.client.renderer.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource source,int light,int overlay,boolean foil) {
        pose.pushPose();
        try {
            model.transform.apply(false,pose.last());
            net.minecraft.client.renderer.entity.ItemRenderer.renderItem(net.minecraft.world.item.ItemDisplayContext.GROUND,pose,source,light,overlay,tints,model.quads,type,foil ? net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.STANDARD : net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.NONE);
        } finally { pose.popPose(); }
    }

    public static void renderQuads(java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads,int[] tints,com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer vertices,int light,int overlay) {
        net.minecraft.client.renderer.entity.ItemRenderer.renderQuadList(pose,vertices,quads,tints,light,overlay);
    }
    public static com.mojang.blaze3d.opengl.GlProgram shader(net.minecraft.client.renderer.RenderType type) {
        var compiled=com.mojang.blaze3d.systems.RenderSystem.getDevice().precompilePipeline(type.getRenderPipeline());
        return compiled instanceof com.mojang.blaze3d.opengl.GlRenderPipeline gl ? gl.program() : null;
    }
    public static void shaderLights(com.mojang.blaze3d.opengl.GlProgram shader) {
        var lights=com.mojang.blaze3d.systems.RenderSystem.getShaderLights();
        if(shader.LIGHT0_DIRECTION!=null)shader.LIGHT0_DIRECTION.set(lights[0]);
        if(shader.LIGHT1_DIRECTION!=null)shader.LIGHT1_DIRECTION.set(lights[1]);
    }
    public static void applyShader(com.mojang.blaze3d.opengl.GlProgram shader) {
        com.mojang.blaze3d.opengl.GlStateManager._glUseProgram(shader.getProgramId());
        int active=com.mojang.blaze3d.opengl.GlStateManager._getActiveTexture();
        try {
            for(int i=0;i<shader.getSamplers().size();i++) {
                String name=shader.getSamplers().get(i);
                if(!name.matches("Sampler[0-9]+"))continue;
                int slot=Integer.parseInt(name.substring(7));
                var texture=com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(slot);
                if(texture instanceof com.mojang.blaze3d.opengl.GlTexture gl) {
                    org.lwjgl.opengl.GL33.glUniform1i(shader.getSamplerLocations().getInt(i),i);
                    com.mojang.blaze3d.opengl.GlStateManager._activeTexture(33984+i);
                    com.mojang.blaze3d.opengl.GlStateManager._bindTexture(gl.glId());
                    gl.flushModeChanges();
                }
            }
            for(var uniform:shader.getUniforms())uniform.upload();
        } finally {com.mojang.blaze3d.opengl.GlStateManager._activeTexture(active);}
    }
    private record DrawState(com.mojang.blaze3d.systems.RenderPass pass,int framebuffer,int[] viewport) {}
    private static final java.util.ArrayDeque<DrawState> passes=new java.util.ArrayDeque<>();
    public static void setupType(net.minecraft.client.renderer.RenderType type) {
        type.setupRenderState();
        var encoder=com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        int old=org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];org.lwjgl.opengl.GL33.glGetIntegerv(org.lwjgl.opengl.GL33.GL_VIEWPORT,viewport);
        var target=type.getRenderTarget();
        var pass=encoder.createRenderPass(target.getColorTexture(),java.util.OptionalInt.empty(),target.useDepth?target.getDepthTexture():null,java.util.OptionalDouble.empty());
        try {
            pass.setPipeline(type.getRenderPipeline());
            ((first.carbon.mixin.GlEncoderAccess)encoder).carbon$pipeline(type.getRenderPipeline());
            passes.push(new DrawState(pass,old,viewport));
        } catch(RuntimeException|Error failure) {pass.close();org.lwjgl.opengl.GL33.glBindFramebuffer(org.lwjgl.opengl.GL33.GL_FRAMEBUFFER,old);throw failure;}
    }
    public static void clearType(net.minecraft.client.renderer.RenderType type) {
        try {type.clearRenderState();} finally {
            var state=passes.pop();state.pass.close();
            com.mojang.blaze3d.opengl.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL33.GL_FRAMEBUFFER,state.framebuffer);
            com.mojang.blaze3d.opengl.GlStateManager._viewport(state.viewport[0],state.viewport[1],state.viewport[2],state.viewport[3]);
        }
    }
}
