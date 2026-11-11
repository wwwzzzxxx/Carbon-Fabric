package first.carbon.compat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import first.carbon.compat.ItemGeometry;
public final class RenderCompat {

    private static final boolean fastShadows = java.util.Arrays.stream(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredMethods()).anyMatch(method -> method.getName().contains("renderShadowPartFast"));
    public static float shadowHeightFactor() { return fastShadows ? 2 : 1; }

    @FunctionalInterface public interface CaptureSource {com.mojang.blaze3d.vertex.VertexConsumer getBuffer(net.minecraft.client.renderer.rendertype.RenderType type);}
    public static boolean world;
    public static int guiTick(){return first.carbon.Carbon.Client.RENDERER.currentFrame();}
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
    public static BufferBuilder builder(int size, com.mojang.renderpearl.api.pipeline.PrimitiveTopology mode, VertexFormat format) {
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

    public record ResolvedItem(ItemGeometry model, net.minecraft.client.renderer.rendertype.RenderType type, int[] tints) {}

    public static net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState batchableLayer(
            net.minecraft.client.renderer.item.ItemStackRenderState state) {
        if (state.activeLayerCount != 1) return null;
        var layer = state.layers[0];
        if (layer.quads.isEmpty() || layer.specialRenderer != null
                || layer.foilType != net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.NONE
                || (layer.localTransform.properties() & org.joml.Matrix4fc.PROPERTY_IDENTITY) == 0) return null;
        return layer;
    }

    public static ResolvedItem resolveItem(net.minecraft.client.renderer.item.ItemStackRenderState state,
                                           net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState layer) {
        var quads = layer.quads.all();
        var type = quads.getFirst().materialInfo().itemRenderType();
        for (var quad : quads) if (quad.materialInfo().itemRenderType() != type) return null;
        var model = ItemGeometry.get(quads, layer.itemTransform, state.getModelBoundingBox());
        int[] tints = layer.tintLayers == null ? net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState.EMPTY_TINTS
                : layer.tintLayers.toIntArray();
        return new ResolvedItem(model, type, tints);
    }
    public static void renderItem(first.carbon.compat.ItemGeometry model,int[] tints,net.minecraft.client.renderer.rendertype.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,first.carbon.compat.RenderCompat.CaptureSource source,int light,int overlay) {
        renderItem(model,tints,type,pose,source,light,overlay,false);
    }
    public static void renderItem(first.carbon.compat.ItemGeometry model,int[] tints,net.minecraft.client.renderer.rendertype.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,first.carbon.compat.RenderCompat.CaptureSource source,int light,int overlay,boolean foil) {
        pose.pushPose();
        try {
            model.transform.apply(false,pose.last());
            var instance=new com.mojang.blaze3d.vertex.QuadInstance();instance.setLightCoords(light);instance.setOverlayCoords(overlay);
            for(var quad:model.quads) {
                var material=quad.materialInfo();instance.setColor(material.isTinted()&&material.tintIndex()<tints.length?tints[material.tintIndex()]:-1);
                var vertices=source.getBuffer(material.itemRenderType());
                vertices.putBakedQuad(pose.last(),quad,instance);
                if(foil)source.getBuffer(foilType(material.itemRenderType())).putBakedQuad(pose.last(),quad,instance);
            }
        } finally { pose.popPose(); }
    }

    public static void renderItem(ItemGeometry model,int[] tints,net.minecraft.client.renderer.rendertype.RenderType type,com.mojang.blaze3d.vertex.PoseStack pose,Object owner,int light,int overlay,boolean foil) {
        pose.pushPose();try{model.transform.apply(false,pose.last());first.carbon.Carbon.Client.RENDERER.submitDirect(ItemGeometry.plain(model.quads),tints,light,overlay,pose,owner,type,foil?foilType(type):null);}finally{pose.popPose();}
    }
    public static net.minecraft.client.renderer.rendertype.RenderType foilType(net.minecraft.client.renderer.rendertype.RenderType type) {
        throw new IllegalArgumentException("26.3 integrated glint uses the native material pipeline");
    }
    public static void renderQuads(java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> quads,int[] tints,com.mojang.blaze3d.vertex.PoseStack pose,com.mojang.blaze3d.vertex.VertexConsumer vertices,int light,int overlay) {
        var instance=new com.mojang.blaze3d.vertex.QuadInstance();instance.setLightCoords(light);instance.setOverlayCoords(overlay);
        for(var quad:quads) {var material=quad.materialInfo();instance.setColor(material.isTinted()&&material.tintIndex()<tints.length?tints[material.tintIndex()]:-1);vertices.putBakedQuad(pose.last(),quad,instance);}
    }
    public static org.joml.Matrix4f textureMatrix(){return passes.isEmpty()?new org.joml.Matrix4f():passes.peek().textureMatrix;}
    private record DrawState(com.mojang.renderpearl.api.commands.RenderPass pass,int framebuffer,int[] viewport,int[] ids,long[] offsets,long[] lengths,org.joml.Matrix4f textureMatrix,java.util.List<net.minecraft.client.renderer.rendertype.PreparedRenderType.Texture> textures,boolean pushed,int[] samplers,int[] texturesBound,first.carbon.mixin.GlEncoderAccess backend,com.mojang.renderpearl.backend.opengl.GlRenderPipeline previousPipeline) {}
    private static final java.util.ArrayDeque<DrawState> passes=new java.util.ArrayDeque<>();
    public static com.mojang.renderpearl.api.commands.RenderPass nativePass;
    public static void setupType(net.minecraft.client.renderer.rendertype.RenderType type) {
        var setup=type.state;
        var modifier=setup.layeringTransform.getModifier();boolean pushed=modifier!=null;
        if(pushed){com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().pushMatrix();modifier.accept(com.mojang.blaze3d.systems.RenderSystem.getModelViewStack());}
        var textureMatrix=setup.textureTransform.createMatrix();var textures=type.prepare().textures();
        int[] samplers=new int[3];for(int i=0;i<3;i++)samplers[i]=org.lwjgl.opengl.GL33.glGetIntegeri(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING,i);
        var encoder=com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        int old=org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];org.lwjgl.opengl.GL33.glGetIntegerv(org.lwjgl.opengl.GL33.GL_VIEWPORT,viewport);
        int[] ids=new int[3];long[] offsets=new long[3],lengths=new long[3];
        for(int i=0;i<3;i++) {ids[i]=org.lwjgl.opengl.GL33.glGetIntegeri(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_BINDING,12+i);offsets[i]=org.lwjgl.opengl.GL33.glGetInteger64i(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_START,12+i);lengths[i]=org.lwjgl.opengl.GL33.glGetInteger64i(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER_SIZE,12+i);}
        var target=net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var pass=nativePass==null?encoder.createRenderPass(()->"Carbon instancing",target.getColorTextureView(),java.util.Optional.empty(),target.getDepthTextureView(),java.util.OptionalDouble.empty()):null;
        var backend=(first.carbon.mixin.GlEncoderAccess)((first.carbon.mixin.CommandEncoderAccess)(Object)encoder).carbon$backend();
        var previousPipeline=backend.carbon$lastPipeline();
        int active=org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE);int[] texturesBound=new int[3];for(int i=0;i<3;i++){com.mojang.renderpearl.backend.opengl.GlStateManager._activeTexture(33984+i);texturesBound[i]=org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D);}com.mojang.renderpearl.backend.opengl.GlStateManager._activeTexture(active);
        try {
            if(pass!=null)pass.setPipeline(com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(type.pipeline()));
            ((com.mojang.renderpearl.backend.opengl.GlRenderPipeline)((com.mojang.renderpearl.frontend.FrontendRenderPipeline)com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(type.pipeline())).backendRenderPipeline()).bind();
            passes.push(new DrawState(pass,old,viewport,ids,offsets,lengths,textureMatrix,textures,pushed,samplers,texturesBound,backend,previousPipeline));
        } catch(RuntimeException|Error failure) {if(pass!=null)pass.close();org.lwjgl.opengl.GL33.glBindFramebuffer(org.lwjgl.opengl.GL33.GL_FRAMEBUFFER,old);throw failure;}
    }
    public static void clearType(net.minecraft.client.renderer.rendertype.RenderType type) {
        try { } finally {
            var state=passes.pop();if(state.pass!=null)state.pass.close();
            if(state.previousPipeline!=null)state.previousPipeline.bind();state.backend.carbon$lastPipeline(null);state.backend.carbon$lastProgram(null);
            int active=org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE);for(int i=0;i<3;i++){com.mojang.renderpearl.backend.opengl.GlStateManager._activeTexture(33984+i);com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(state.texturesBound[i]);}com.mojang.renderpearl.backend.opengl.GlStateManager._activeTexture(active);
            for(int i=0;i<3;i++)org.lwjgl.opengl.GL33.glBindSampler(i,state.samplers[i]);
            if(state.pushed)com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().popMatrix();
            for(int i=0;i<3;i++) {if(state.ids[i]!=0 && state.lengths[i]>0)org.lwjgl.opengl.GL33.glBindBufferRange(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER,12+i,state.ids[i],state.offsets[i],state.lengths[i]);else org.lwjgl.opengl.GL33.glBindBufferBase(org.lwjgl.opengl.GL33.GL_UNIFORM_BUFFER,12+i,state.ids[i]);}

            com.mojang.renderpearl.backend.opengl.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL33.GL_FRAMEBUFFER,state.framebuffer);
            com.mojang.renderpearl.backend.opengl.GlStateManager._viewport(state.viewport[0],state.viewport[1],state.viewport[2],state.viewport[3]);
        }
    }

    public static void bindShared(int program) {
        shared(program,"Projection",12,com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrixBuffer());
        shared(program,"Fog",13,com.mojang.blaze3d.systems.RenderSystem.getShaderFog());
        shared(program,"Lighting",14,com.mojang.blaze3d.systems.RenderSystem.getShaderLights());
        int active=org.lwjgl.opengl.GL33.glGetInteger(org.lwjgl.opengl.GL33.GL_ACTIVE_TEXTURE);
        try {for(var binding:passes.peek().textures) {
            if(!binding.name().matches("Sampler[0-2]"))continue;int unit=Integer.parseInt(binding.name().substring(7));
            if(binding.textureView().texture() instanceof com.mojang.renderpearl.backend.opengl.GlTexture texture && binding.sampler() instanceof com.mojang.renderpearl.backend.opengl.GlSampler sampler){com.mojang.renderpearl.backend.opengl.GlStateManager._activeTexture(33984+unit);com.mojang.renderpearl.backend.opengl.GlStateManager._bindTexture(texture.glId());org.lwjgl.opengl.GL33.glBindSampler(unit,sampler.getId());}
        }}finally{com.mojang.renderpearl.backend.opengl.GlStateManager._activeTexture(active);}
    }
    private static void shared(int program,String name,int binding,com.mojang.renderpearl.api.buffers.GpuBufferSlice slice) {
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
