package first.carbon.mixin;
import first.carbon.Carbon;
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
    private void carbon$begin(GraphicsResourceAllocator allocator,DeltaTracker tracker,boolean outline,net.minecraft.client.renderer.state.level.CameraRenderState cameraState,org.joml.Matrix4fc view,GpuBufferSlice fog,Vector4f fogColor,boolean sky,net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunks,CallbackInfo ci) {
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        Carbon.Client.RENDERER.beginWorld(camera,new Matrix4f(view),cameraState.projectionMatrix);
        Carbon.Client.DROPPED_RENDERER.beginFrame(camera,new Matrix4f(view),cameraState.projectionMatrix,tracker.getGameTimeDeltaPartialTick(false));
    }
    @Inject(method="renderLevel",at=@At("RETURN"))
    private void carbon$end(CallbackInfo ci) {Carbon.Client.DROPPED_RENDERER.flush();Carbon.Client.DROPPED_RENDERER.endFrame();Carbon.Client.RENDERER.endWorld();}
    @Inject(method="extractVisibleEntities",at=@At("HEAD"))
    private void carbon$frustum(Camera camera,net.minecraft.client.renderer.culling.Frustum frustum,DeltaTracker tracker,net.minecraft.client.renderer.state.level.LevelRenderState state,CallbackInfo ci) {
        first.carbon.compat.RenderCompat.frustum = frustum;
        Carbon.Client.DROPPED_RENDERER.beginItemExtraction(camera.position());
    }
    @Inject(method="extractVisibleEntities",at=@At("RETURN"))
    private void carbon$endExtraction(CallbackInfo ci) { first.carbon.client.StaticItemRenderStates.end(); }
}
