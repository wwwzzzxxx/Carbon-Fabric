package first.carbon.mixin;
import first.carbon.Carbon;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class) public abstract class LevelRendererMixin {
    @Shadow @Final private net.minecraft.client.renderer.state.level.LevelRenderState levelRenderState;
    @Inject(method="render",at=@At("HEAD"))
    private void carbon$begin(CallbackInfo ci) {
        var cameraState=levelRenderState.cameraRenderState;var view=cameraState.viewRotationMatrix;var tracker=net.minecraft.client.Minecraft.getInstance().getDeltaTracker();
        first.carbon.compat.RenderCompat.frustum=cameraState.cullFrustum;
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
        Carbon.Client.RENDERER.beginWorld(camera,new Matrix4f(view),cameraState.projectionMatrix);
        Carbon.Client.DROPPED_RENDERER.beginFrame(camera,new Matrix4f(view),cameraState.projectionMatrix,cameraState.cameraEntityPartialTicks);
    }
    @Inject(method="render",at=@At("RETURN"))
    private void carbon$end(CallbackInfo ci) {Carbon.Client.DROPPED_RENDERER.flush();Carbon.Client.DROPPED_RENDERER.endFrame();Carbon.Client.RENDERER.endWorld();}
}
