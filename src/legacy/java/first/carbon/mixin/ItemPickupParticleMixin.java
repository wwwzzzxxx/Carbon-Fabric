package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemPickupParticle.class)
abstract class ItemPickupParticleMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"))
    private MultiBufferSource.BufferSource carbon$separatePickupBuffer(RenderBuffers buffers) {
        return Carbon.Client.RENDERER.pickupBuffer(buffers);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void carbon$nativePickup(EntityRenderDispatcher dispatcher, Entity item, double x, double y, double z, float yaw,
                                      float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemBatchRenderer renderer = Carbon.Client.RENDERER;
        boolean capturing = renderer.setCapturing(true);
        try {
            dispatcher.render(item, x, y, z, yaw, partialTick, pose, buffers, light);
        } finally {
            renderer.setCapturing(capturing);
        }
    }
}
