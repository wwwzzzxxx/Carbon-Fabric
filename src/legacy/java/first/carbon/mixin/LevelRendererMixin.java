package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import first.carbon.Carbon;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import java.util.Iterator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private RenderBuffers renderBuffers;
    @Shadow private int renderedEntities;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void carbon$beginWorld(PoseStack pose, float partialTick, long finishTime, boolean blockOutline,
                                   Camera camera, GameRenderer renderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        Carbon.Client.RENDERER.beginWorld();
        Carbon.Client.DROPPED_RENDERER.beginWorld(camera, pose, partialTick);
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZIZ)V"),
            index = 1, require = 0)
    private Frustum carbon$captureFrustum(Frustum frustum) {
        Carbon.Client.DROPPED_RENDERER.captureFrustum(frustum);
        return frustum;
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Iterable;iterator()Ljava/util/Iterator;", ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;")), require = 0)
    private Iterator<Entity> carbon$collect(Iterable<Entity> entities, PoseStack pose, float partialTick, long finishTime,
                                             boolean blockOutline, Camera camera, GameRenderer renderer, LightTexture light, Matrix4f projection) {
        return Carbon.Client.DROPPED_RENDERER.collect(entities, renderBuffers.bufferSource(), pose, projection, partialTick).iterator();
    }

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void carbon$submitSelected(Entity entity, double x, double y, double z, float partialTick,
                                       PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        if (Carbon.Client.DROPPED_RENDERER.submitSelected(entity, buffers, partialTick)) ci.cancel();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;)V", ordinal = 0), require = 0)
    private void carbon$countDropped(CallbackInfo ci) {
        renderedEntities += Carbon.Client.DROPPED_RENDERER.collected;
        Carbon.Client.DROPPED_RENDERER.flushShadows(renderBuffers.bufferSource());
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void carbon$endWorld(CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.endWorld();
        Carbon.Client.RENDERER.endWorld();
    }
}
