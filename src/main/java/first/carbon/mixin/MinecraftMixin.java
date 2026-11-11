package first.carbon.mixin;

import first.carbon.Carbon;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique private boolean carbon$hadWorld;

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"))
    private void carbon$frame(boolean render, CallbackInfo ci) {
        boolean inWorld = ((Minecraft) (Object) this).level != null;
        if (carbon$hadWorld && !inWorld) {
            // A final world frame can recreate caches after the networking disconnect event.
            Carbon.Client.RENDERER.invalidate();
            Carbon.Client.DROPPED_RENDERER.invalidate();
        }
        carbon$hadWorld = inWorld;
        Carbon.Client.IRIS.beginFrame();
        Carbon.Client.RENDERER.beginFrame();
    }
}
