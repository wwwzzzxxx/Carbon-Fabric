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
    @Inject(method="render",at=@At("HEAD"))
    private void carbon$begin(GraphicsResourceAllocator allocator,DeltaTracker tracker,boolean outline,net.minecraft.client.renderer.state.level.CameraRenderState cameraState,org.joml.Matrix4fc view,GpuBufferSlice fog,Vector4f fogColor,boolean sky,CallbackInfo ci) {
        first.carbon.compat.RenderCompat.frustum=cameraState.cullFrustum;
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
        Carbon.Client.RENDERER.beginWorld(camera,new Matrix4f(view),cameraState.projectionMatrix);
        Carbon.Client.DROPPED_RENDERER.beginFrame(camera,new Matrix4f(view),cameraState.projectionMatrix,tracker.getGameTimeDeltaPartialTick(false));
    }
    @Inject(method="render",at=@At("RETURN"))
    private void carbon$end(CallbackInfo ci) {Carbon.Client.DROPPED_RENDERER.flush();Carbon.Client.DROPPED_RENDERER.endFrame();Carbon.Client.RENDERER.endWorld();}
}
