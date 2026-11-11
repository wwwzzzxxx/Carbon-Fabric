package first.carbon.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer;
import first.carbon.compat.RenderCompat.CaptureSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Object.class)
public abstract class BufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void imo$target(RenderType type, CallbackInfoReturnable<VertexConsumer> ci) {
        if (ci.getReturnValue() instanceof ItemBatchRenderer.Target target) {
            target.imo$target((Object) (Object) this, type);
        }
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/rendertype/RenderType;)V", at = @At("HEAD"))
    private void imo$flush(RenderType type, CallbackInfo ci) {
        Carbon.Client.RENDERER.flush((Object) (Object) this, type);
    }
}
