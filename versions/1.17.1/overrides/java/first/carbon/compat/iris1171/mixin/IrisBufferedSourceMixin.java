package first.carbon.compat.iris1171.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.coderbot.batchedentityrendering.impl.FullyBufferedMultiBufferSource", remap = false)
public abstract class IrisBufferedSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void carbon$target(RenderType layer, CallbackInfoReturnable<VertexConsumer> ci) {
        if (Carbon.Client.RENDERER.isRenderingWorld() && Carbon.Client.IRIS.canInstance()
                && ci.getReturnValue() instanceof ItemBatchRenderer.Target target) {
            target.imo$target((MultiBufferSource.BufferSource) (Object) this, layer);
        }
    }

    @Redirect(method = "method_22993", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1921;method_23516()V", remap = false))
    private void carbon$drawLayer(RenderType layer) {
        Carbon.Client.RENDERER.flush((MultiBufferSource.BufferSource) (Object) this, layer);
        layer.setupRenderState();
    }
}
