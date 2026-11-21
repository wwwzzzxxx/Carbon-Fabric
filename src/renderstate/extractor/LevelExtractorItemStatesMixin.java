package first.carbon.mixin;

import first.carbon.Carbon;
import first.carbon.client.StaticItemRenderStates;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
abstract class LevelExtractorItemStatesMixin {
    @Shadow @Final private LevelRenderer levelRenderer;

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"), cancellable = true)
    private void carbon$beginItemStates(Camera camera, Frustum frustum, DeltaTracker tracker,
                                        LevelRenderState state, CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.beginItemExtraction(camera.position());
        if (StaticItemRenderStates.replayVisibleItems(frustum, camera.entity(), camera.isDetached(), tracker,
                state.entityRenderStates, levelRenderer::isSectionCompiledAndVisible)) {
            state.lastEntityRenderStateCount = state.entityRenderStates.size();
            StaticItemRenderStates.end();
            ci.cancel();
        }
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void carbon$endItemStates(Camera camera, Frustum frustum, DeltaTracker tracker,
                                      LevelRenderState state, CallbackInfo ci) {
        StaticItemRenderStates.rememberVisibleItems(camera.entity(), camera.isDetached(), state.entityRenderStates);
        StaticItemRenderStates.end();
    }
}
