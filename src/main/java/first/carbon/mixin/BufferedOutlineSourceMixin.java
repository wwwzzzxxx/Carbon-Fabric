package first.carbon.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.client.OrderedOutlineBufferSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OutlineBufferSource.class)
abstract class BufferedOutlineSourceMixin {
    @Shadow @Final private MultiBufferSource.BufferSource bufferSource;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/"
            + "MultiBufferSource;immediate(Lcom/mojang/blaze3d/vertex/BufferBuilder;)"
            + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"))
    private static MultiBufferSource.BufferSource carbon$maskBuffer(BufferBuilder buffer) {
        if (FabricLoader.getInstance().isModLoaded("immediatelyfast")) {
            return new OrderedOutlineBufferSource(buffer);
        }
        return MultiBufferSource.immediate(buffer);
    }

    @Redirect(method = "getBuffer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/"
            + "MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer carbon$bodyBuffer(MultiBufferSource.BufferSource buffers, RenderType type) {
        // Iris can replace the body source with a buffered subclass while leaving the mask source intact.
        if (buffers == bufferSource && !Carbon.Client.IRIS.active()) {
            return Carbon.Client.IRIS.getBuffer(buffers, type);
        }
        return buffers.getBuffer(type);
    }
}
