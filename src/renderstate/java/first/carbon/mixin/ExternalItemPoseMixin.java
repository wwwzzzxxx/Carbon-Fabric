package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import first.carbon.compat.ItemEntityCompatibility;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
abstract class ExternalItemPoseMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void carbon$submitDropPose(PoseStack pose, SubmitNodeCollector collector, int light,
                                       int overlay, int outline, CallbackInfo ci) {
        if (ItemEntityCompatibility.hasExternalDropTransforms()
                && Carbon.Client.DROPPED_RENDERER.submitExternalPose((ItemStackRenderState) (Object) this,
                pose, light, overlay, outline)) {
            ci.cancel();
        }
    }
}
