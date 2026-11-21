package first.carbon.mixin;

import first.carbon.client.ItemBatchRenderer;
import first.carbon.compat.LegacyItemPhysicFireCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemBatchRenderer.class, remap = false)
public abstract class LegacyItemPhysicRenderScopeMixin {
    @Inject(method = "beginWorld", at = @At("HEAD"))
    private void carbon$beginFireCache(CallbackInfo ci) {
        LegacyItemPhysicFireCache.beginPass();
    }

    @Inject(method = "endWorld", at = @At("RETURN"))
    private void carbon$endFireCache(CallbackInfo ci) {
        LegacyItemPhysicFireCache.endPass();
    }
}
