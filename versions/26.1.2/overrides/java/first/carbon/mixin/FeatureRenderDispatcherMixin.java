package first.carbon.mixin;
import first.carbon.Carbon;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FeatureRenderDispatcher.class) public abstract class FeatureRenderDispatcherMixin {
    @Inject(method="renderSolidFeatures",at=@At("RETURN"))
    private void carbon$flush(CallbackInfo ci){Carbon.Client.DROPPED_RENDERER.flush();Carbon.Client.RENDERER.flushShadows();}
}
