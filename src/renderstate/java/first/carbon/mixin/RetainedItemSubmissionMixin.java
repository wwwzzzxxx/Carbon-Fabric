package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
abstract class RetainedItemSubmissionMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void carbon$submitRetained(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                      PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
        if (state instanceof ItemEntityRenderState item && Carbon.Client.DROPPED_RENDERER.submitRetained(item, pose)) {
            ci.cancel();
        }
    }
}
