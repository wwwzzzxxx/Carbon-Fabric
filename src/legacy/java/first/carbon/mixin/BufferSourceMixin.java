package first.carbon.mixin;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void carbon$target(RenderType type, CallbackInfoReturnable<VertexConsumer> ci) {
        if (ci.getReturnValue() instanceof ItemBatchRenderer.Target) ((ItemBatchRenderer.Target) ci.getReturnValue()).imo$target((MultiBufferSource.BufferSource) (Object) this, type);
    }
    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"))
    private void carbon$flush(RenderType type, CallbackInfo ci) { Carbon.Client.RENDERER.flush((MultiBufferSource.BufferSource) (Object) this, type); }
}
