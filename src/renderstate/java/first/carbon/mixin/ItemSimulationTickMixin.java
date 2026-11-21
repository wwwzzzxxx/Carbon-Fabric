package first.carbon.mixin;

import first.carbon.client.StaticItemRenderStates;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ItemSimulationTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void carbon$advanceItemTick(CallbackInfo ci) {
        StaticItemRenderStates.advanceClientTick();
    }
}
