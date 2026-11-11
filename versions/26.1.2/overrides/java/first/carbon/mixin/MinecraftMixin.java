package first.carbon.mixin;
import first.carbon.Carbon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class) public abstract class MinecraftMixin {
    @Unique private boolean carbon$hadWorld;

    @Inject(method = "render", at = @At("HEAD"))
    private void carbon$frame(CallbackInfo ci) {
        boolean inWorld = Minecraft.getInstance().level != null;
        if (carbon$hadWorld && !inWorld) {
            Carbon.Client.RENDERER.invalidate();
            Carbon.Client.DROPPED_RENDERER.invalidate();
        }
        carbon$hadWorld = inWorld;
        Carbon.Client.IRIS.beginFrame();
        Carbon.Client.RENDERER.beginFrame();
    }
}
