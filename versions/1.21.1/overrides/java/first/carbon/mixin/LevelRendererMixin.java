package first.carbon.mixin;

import first.carbon.Carbon;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow private int renderedEntities;
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
    private Iterable<Entity> imo$entities(ClientLevel level) {
        var renderer = Carbon.Client.DROPPED_RENDERER;
        renderer.collecting = Carbon.CONFIG.optimizeDroppedItems.get();
        try {
            return level.entitiesForRendering();
        } finally {
            renderer.collecting = false;
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void imo$shadows(net.minecraft.client.DeltaTracker tracker, boolean outline, Camera camera, GameRenderer renderer, LightTexture light, Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        float partialTick = tracker.getGameTimeDeltaPartialTick(false);
        Carbon.Client.RENDERER.beginWorld(camera, first.carbon.compat.RenderCompat.matrix(view), first.carbon.compat.RenderCompat.matrix(projection));
        Carbon.Client.DROPPED_RENDERER.beginFrame(camera, first.carbon.compat.RenderCompat.matrix(view), first.carbon.compat.RenderCompat.matrix(projection), partialTick);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;)V", ordinal = 0))
    private void carbon$afterEntities(CallbackInfo ci) {
        renderedEntities += Carbon.Client.DROPPED_RENDERER.visibleCollected;
        Carbon.Client.DROPPED_RENDERER.flush();
        Carbon.Client.RENDERER.flushShadows();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void carbon$endWorld(CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.flush();
        Carbon.Client.DROPPED_RENDERER.endFrame();
        Carbon.Client.RENDERER.endWorld();
    }
}
