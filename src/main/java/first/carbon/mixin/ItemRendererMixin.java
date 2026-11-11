package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
    private boolean imo$foil(ItemStack stack) {
        if (!Carbon.Client.RENDERER.isRenderingWorld()) return stack.hasFoil();
        return !Carbon.Client.DROPPED_RENDERER.capturingModel()
                && ((ItemBatchRenderer.FoilState) (Object) stack).imo$foil(Minecraft.getInstance().gui.getGuiTicks());
    }

    @Redirect(method = {"getFoilBufferDirect", "getFoilBuffer", "getCompassFoilBuffer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer carbon$buffer(net.minecraft.client.renderer.MultiBufferSource source, net.minecraft.client.renderer.RenderType type) {
        return Carbon.Client.RENDERER.isRenderingWorld() ? Carbon.Client.IRIS.getBuffer(source, type) : source.getBuffer(type);
    }

    @Inject(method = "renderModelLists", at = @At("HEAD"), cancellable = true)
    private void imo$submit(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose, VertexConsumer vertices, CallbackInfo ci) {
        if (Carbon.Client.RENDERER.submit(model, stack, light, overlay, pose, vertices)) {
            ci.cancel();
        }
    }
}
