package first.carbon.compat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.ints.IntList;
public final class RenderCompat {

    private static final boolean fastShadows = java.util.Arrays.stream(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredMethods()).anyMatch(method -> method.getName().contains("renderShadowPartFast"));
    public static float shadowHeightFactor() { return fastShadows ? 2 : 1; }

    public static boolean world;
    public static net.minecraft.client.renderer.culling.Frustum frustum;
    public static float fogStart(){return 0;}
    public static float fogEnd(){return 0;}
    public static int fogShape(){return 0;}
    public static float[] fogColor(){return WHITE;}
    private static final float[] WHITE={1,1,1,1};
    public static float[] color(){return WHITE;}
    public static float glintAlpha(){return net.minecraft.client.Minecraft.getInstance().options.glintStrength().get().floatValue();}
    private static final org.joml.Matrix4f unusedProjection=new org.joml.Matrix4f();
    public static org.joml.Matrix4f projection(){return unusedProjection;}
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
    private static ItemStack resolvedStack;
    private static ResolvedItem resolvedItem;

    public static ResolvedItem resolveItem(ItemStack stack, ItemStackRenderState state,
                                           ResolvedItem previous) {
        resolvedStack = null;
        resolvedItem = null;
        if (state.activeLayerCount != 1) return null;
        var layer = state.layers[0];
        if (layer.quads.isEmpty() || layer.specialRenderer != null || layer.foilType == ItemStackRenderState.FoilType.SPECIAL
                || (layer.localTransform.properties() & org.joml.Matrix4fc.PROPERTY_IDENTITY) == 0) return null;
        RenderType type = layer.quads.getFirst().materialInfo().itemRenderType();
        boolean sameMaterial = previous != null && previous.type() == type
                && previous.foil() == layer.foilType && sameTints(previous.tints(), layer.tintLayers);
        // Compare the evaluated layer so resource packs and context-dependent item models keep their appearance.
        if (sameMaterial && !state.isAnimated() && previous.extents() == layer.extents
                && previous.model().transform == layer.itemTransform && sameQuads(previous.model().quads, layer.quads)) {
            resolvedItem = previous;
        } else {
            for (var quad : layer.quads) if (quad.materialInfo().itemRenderType() != type) return null;
            var bounds = new net.minecraft.world.phys.AABB.Builder();
            state.visitExtents(bounds::include);
            var model = ItemGeometry.get(layer.quads, layer.itemTransform, bounds.build());
            resolvedItem = sameMaterial && previous.model() == model && previous.extents() == layer.extents
                    ? previous : new ResolvedItem(model, type, layer.tintLayers == null ? new int[0] : layer.tintLayers.toIntArray(), layer.foilType, layer.extents);
        }
        resolvedStack = stack;
        return resolvedItem;
    }

    private static boolean sameQuads(List<BakedQuad> cached, List<BakedQuad> current) {
        if (cached.size() != current.size()) return false;
        for (int i = 0; i < cached.size(); i++) if (cached.get(i) != current.get(i)) return false;
        return true;
    }

    private static boolean sameTints(int[] cached, IntList current) {
        if (current == null) return cached.length == 0;
        if (cached.length != current.size()) return false;
        for (int i = 0; i < cached.length; i++) if (cached[i] != current.getInt(i)) return false;
        return true;
    }

    public static void clearResolved() { resolvedStack = null; resolvedItem = null; }
    public static ResolvedItem resolved(net.minecraft.world.item.ItemStack stack) { return resolvedStack == stack ? resolvedItem : null; }
    public static void renderItem(first.carbon.compat.ItemGeometry model,int[] tints,net.minecraft.client.renderer.rendertype.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource source,int light,int overlay) {
        renderItem(model,tints,type,pose,source,light,overlay,false);
    }
    public static void renderItem(first.carbon.compat.ItemGeometry model,int[] tints,net.minecraft.client.renderer.rendertype.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource source,int light,int overlay,boolean foil) {
        pose.pushPose();
        try {
            model.transform.apply(false,pose.last());
            var instance=new com.mojang.blaze3d.vertex.QuadInstance();instance.setLightCoords(light);instance.setOverlayCoords(overlay);
            for(var quad:model.quads) {
                var material=quad.materialInfo();instance.setColor(material.isTinted()&&material.tintIndex()<tints.length?tints[material.tintIndex()]:-1);
                var vertices=net.minecraft.client.renderer.feature.ItemFeatureRenderer.getFoilBuffer(source,material.itemRenderType(),true,foil);
                vertices.putBakedQuad(pose.last(),quad,instance);
            }
        } finally { pose.popPose(); }
    }

    public static void renderQuads(java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> quads,int[] tints,com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer vertices,int light,int overlay) {
        var instance=new com.mojang.blaze3d.vertex.QuadInstance();instance.setLightCoords(light);instance.setOverlayCoords(overlay);
        for(var quad:quads) {var material=quad.materialInfo();instance.setColor(material.isTinted()&&material.tintIndex()<tints.length?tints[material.tintIndex()]:-1);vertices.putBakedQuad(pose.last(),quad,instance);}
    }
    public static org.joml.Matrix4f textureMatrix(){return passes.isEmpty()?new org.joml.Matrix4f():passes.peek().textureMatrix;}
    private record DrawState(com.mojang.blaze3d.systems.RenderPass pass,int framebuffer,int[] viewport,int[] ids,long[] offsets,long[] lengths,org.joml.Matrix4f textureMatrix,java.util.Map<String,net.minecraft.client.renderer.rendertype.RenderSetup.TextureAndSampler> textures,boolean pushed,int[] samplers) {}
    private static final java.util.ArrayDeque<DrawState> passes=new java.util.ArrayDeque<>();
    public static void setupType(net.minecraft.client.renderer.rendertype.RenderType type) {
        var setup=type.state;
        var modifier=setup.layeringTransform.getModifier();boolean pushed=modifier!=null;
        if(pushed){com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().pushMatrix();modifier.accept(com.mojang.blaze3d.systems.RenderSystem.getModelViewStack());}
        var textureMatrix=setup.textureTransform.getMatrix();var textures=setup.getTextures();
        int[] samplers=new int[3];for(int i=0;i<3;i++)samplers[i]=org.lwjgl.opengl.GL33.glGetIntegeri(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING,i);
        var encoder=com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        int old=org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];org.lwjgl.opengl.GL33.glGetIntegerv(org.lwjgl.opengl.GL33.GL_VIEWPORT,viewport);
        int[] ids=new int[3];long[] offsets=new long[3],lengths=new long[3];
        for(int i=0;i<3;i++) {ids[i]=org.lwjgl.opengl.GL33.glGetIntegeri(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_BINDING,12+i);offsets[i]=org.lwjgl.opengl.GL33.glGetInteger64i(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_START,12+i);lengths[i]=org.lwjgl.opengl.GL33.glGetInteger64i(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_SIZE,12+i);}
        var target=setup.outputTarget.getRenderTarget();
        var pass=encoder.createRenderPass(()->"Carbon instancing",com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride!=null?com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride:target.getColorTextureView(),java.util.OptionalInt.empty(),target.useDepth?(com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride!=null?com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride:target.getDepthTextureView()):null,java.util.OptionalDouble.empty());
        try {
            pass.setPipeline(type.pipeline());
            ((first.carbon.mixin.GlEncoderAccess)((first.carbon.mixin.CommandEncoderAccess)(Object)encoder).carbon$backend()).carbon$pipeline(type.pipeline());
            passes.push(new DrawState(pass,old,viewport,ids,offsets,lengths,textureMatrix,textures,pushed,samplers));
        } catch(RuntimeException|Error failure) {pass.close();org.lwjgl.opengl.GL33.glBindFramebuffer(org.lwjgl.opengl.GL33.GL_FRAMEBUFFER,old);throw failure;}
    }
    public static void clearType(net.minecraft.client.renderer.rendertype.RenderType type) {
        try { } finally {
            var state=passes.pop();state.pass.close();
            for(int i=0;i<3;i++)org.lwjgl.opengl.GL33.glBindSampler(i,state.samplers[i]);
            if(state.pushed)com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().popMatrix();
            for(int i=0;i<3;i++) {if(state.ids[i]!=0 && state.lengths[i]>0)org.lwjgl.opengl.GL33.glBindBufferRange(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER,12+i,state.ids[i],state.offsets[i],state.lengths[i]);else org.lwjgl.opengl.GL33.glBindBufferBase(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER,12+i,state.ids[i]);}

            com.mojang.blaze3d.opengl.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL33.GL_FRAMEBUFFER,state.framebuffer);
            com.mojang.blaze3d.opengl.GlStateManager._viewport(state.viewport[0],state.viewport[1],state.viewport[2],state.viewport[3]);
        }
    }

    public static void bindShared(int program) {
        shared(program,"Projection",12,com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrixBuffer());
        shared(program,"Fog",13,com.mojang.blaze3d.systems.RenderSystem.getShaderFog());
        shared(program,"Lighting",14,com.mojang.blaze3d.systems.RenderSystem.getShaderLights());
        int active=org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE);
        try {for(var entry:passes.peek().textures.entrySet()) {
            if(!entry.getKey().matches("Sampler[0-2]"))continue;
            int unit=Integer.parseInt(entry.getKey().substring(7));var binding=entry.getValue();
            if(binding.textureView().texture() instanceof com.mojang.blaze3d.opengl.GlTexture texture && binding.sampler() instanceof com.mojang.blaze3d.opengl.GlSampler sampler) {
                com.mojang.blaze3d.opengl.GlStateManager._activeTexture(33984+unit);
                com.mojang.blaze3d.opengl.GlStateManager._bindTexture(texture.glId());
                org.lwjgl.opengl.GL33.glBindSampler(unit,sampler.getId());
            }
        }} finally {com.mojang.blaze3d.opengl.GlStateManager._activeTexture(active);}
    }
    private static void shared(int program,String name,int binding,com.mojang.blaze3d.buffers.GpuBufferSlice slice) {
        int block=org.lwjgl.opengl.GL33.glGetUniformBlockIndex(program,name);
        if(block!=org.lwjgl.opengl.GL33.GL_INVALID_INDEX&&slice!=null) {
            org.lwjgl.opengl.GL33.glUniformBlockBinding(program,block,binding);
            org.lwjgl.opengl.GL33.glBindBufferRange(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER,binding,((first.carbon.mixin.GlBufferAccess)slice.buffer()).carbon$handle(),slice.offset(),slice.length());
        }
    }
    public static net.minecraft.client.renderer.rendertype.RenderType opaqueType(net.minecraft.client.renderer.rendertype.RenderType type) {
        if(type==net.minecraft.client.renderer.Sheets.translucentItemSheet())return net.minecraft.client.renderer.Sheets.cutoutItemSheet();
        if(type==net.minecraft.client.renderer.Sheets.translucentBlockItemSheet())return net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet();
        return type;
    }
    public static boolean itemType(net.minecraft.client.renderer.rendertype.RenderType type) {
        if(type.hasBlending())return false;
        return type==net.minecraft.client.renderer.Sheets.cutoutItemSheet() || type==net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet() || type==net.minecraft.client.renderer.Sheets.translucentItemSheet() || type==net.minecraft.client.renderer.Sheets.translucentBlockItemSheet();
    }
    public static boolean usesOverlay(net.minecraft.client.renderer.rendertype.RenderType type) {return type.state.useOverlay;}
}
