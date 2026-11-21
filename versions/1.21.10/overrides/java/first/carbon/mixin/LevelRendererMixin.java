package first.carbon.mixin;
import first.carbon.Carbon;
import first.carbon.client.StaticItemRenderStates;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class) public abstract class LevelRendererMixin {
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void carbon$begin(GraphicsResourceAllocator allocator,DeltaTracker tracker,boolean outline,Camera camera,Matrix4f view,Matrix4f projection,Matrix4f cullingProjection,GpuBufferSlice fog,Vector4f fogColor,boolean sky,CallbackInfo ci) {
        first.carbon.compat.RenderCompat.frustum=null;
        Carbon.Client.RENDERER.beginWorld(camera,view,projection);
        Carbon.Client.DROPPED_RENDERER.beginFrame(camera,view,projection,tracker.getGameTimeDeltaPartialTick(false));
    }
    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void carbon$prepareItemOutlines(com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.state.LevelRenderState state,
            net.minecraft.client.renderer.SubmitNodeCollector collector, CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.beginOutlineSubmission(state.haveGlowingEntities, state.entityRenderStates);
    }

    @Inject(method="renderLevel",at=@At("RETURN"))
    private void carbon$end(CallbackInfo ci) {Carbon.Client.DROPPED_RENDERER.flush();Carbon.Client.DROPPED_RENDERER.endFrame();Carbon.Client.RENDERER.endWorld();}
    @Inject(method="extractVisibleEntities",at=@At("HEAD"),cancellable=true)
    private void carbon$frustum(Camera camera,net.minecraft.client.renderer.culling.Frustum frustum,DeltaTracker tracker,net.minecraft.client.renderer.state.LevelRenderState state,CallbackInfo ci) {
        first.carbon.compat.RenderCompat.frustum = frustum;
        Carbon.Client.DROPPED_RENDERER.beginItemExtraction(camera.getPosition());
        if (StaticItemRenderStates.replayVisibleItems(frustum, camera.getEntity(), camera.isDetached(), tracker,
                state.entityRenderStates, ((LevelRenderer) (Object) this)::isSectionCompiled)) {
            StaticItemRenderStates.end();
            ci.cancel();
        }
    }
    @Inject(method="extractVisibleEntities",at=@At("RETURN"))
    private void carbon$endExtraction(Camera camera, net.minecraft.client.renderer.culling.Frustum frustum,
            DeltaTracker tracker, net.minecraft.client.renderer.state.LevelRenderState state, CallbackInfo ci) {
        StaticItemRenderStates.rememberVisibleItems(camera.getEntity(), camera.isDetached(), state.entityRenderStates);
        StaticItemRenderStates.end();
    }
}
