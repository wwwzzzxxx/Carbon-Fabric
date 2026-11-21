package first.carbon.mixin;

import first.carbon.Carbon;
import net.minecraft.client.renderer.OutlineBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OutlineBufferSource.class)
abstract class OutlineBufferSourceMixin {
    @Inject(method = "endOutlineBatch", at = @At("RETURN"))
    private void carbon$drawDroppedItemOutlines(CallbackInfo callback) {
        Carbon.Client.DROPPED_RENDERER.drawOutlines();
    }
}
