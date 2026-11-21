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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LevelRenderer.class) public abstract class LevelRendererMixin {
    @Unique private int carbon$lastVisibleEntityCount;
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
        Carbon.Client.DROPPED_RENDERER.beginItemExtraction(camera.position());
        if (StaticItemRenderStates.replayVisibleItems(frustum, camera.entity(), camera.isDetached(), tracker,
                state.entityRenderStates, ((LevelRenderer) (Object) this)::isSectionCompiledAndVisible)) {
            carbon$lastVisibleEntityCount = state.entityRenderStates.size();
            StaticItemRenderStates.end();
            ci.cancel();
        }
    }
    @Inject(method="extractVisibleEntities",at=@At("RETURN"))
    private void carbon$endExtraction(Camera camera, net.minecraft.client.renderer.culling.Frustum frustum,
            DeltaTracker tracker, net.minecraft.client.renderer.state.LevelRenderState state, CallbackInfo ci) {
        StaticItemRenderStates.rememberVisibleItems(camera.entity(), camera.isDetached(), state.entityRenderStates);
        StaticItemRenderStates.end();
    }
    @Inject(method="extractVisibleEntities",at=@At("TAIL"))
    private void carbon$rememberVisibleEntities(Camera camera,net.minecraft.client.renderer.culling.Frustum frustum,DeltaTracker tracker,net.minecraft.client.renderer.state.LevelRenderState state,CallbackInfo ci){carbon$lastVisibleEntityCount=state.entityRenderStates.size();}
    @Inject(method="setLevel",at=@At("HEAD"))
    private void carbon$resetVisibleEntities(CallbackInfo ci){carbon$lastVisibleEntityCount=0;}
    @Inject(method="getEntityStatistics",at=@At("RETURN"),cancellable=true)
    private void carbon$entityStatistics(CallbackInfoReturnable<String> cir){
        String statistics=cir.getReturnValue();
        if(statistics!=null&&statistics.startsWith("E: ")){
            int separator=statistics.indexOf('/');
            if(separator>=0)cir.setReturnValue("E: "+carbon$lastVisibleEntityCount+statistics.substring(separator));
        }
    }
}
