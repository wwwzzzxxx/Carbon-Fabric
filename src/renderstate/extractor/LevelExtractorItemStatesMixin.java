package first.carbon.mixin;

import first.carbon.Carbon;
import first.carbon.client.StaticItemRenderStates;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
abstract class LevelExtractorItemStatesMixin {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void carbon$beginItemStates(Camera camera, net.minecraft.client.renderer.culling.Frustum frustum,
                                        net.minecraft.client.DeltaTracker tracker,
                                        net.minecraft.client.renderer.state.level.LevelRenderState state, CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.beginItemExtraction(camera.position());
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void carbon$endItemStates(CallbackInfo ci) {
        StaticItemRenderStates.end();
    }
}
