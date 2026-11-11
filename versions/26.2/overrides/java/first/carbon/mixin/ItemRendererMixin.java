package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import first.carbon.compat.ItemGeometry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemRendererMixin {

    @Redirect(method = {"getFoilBuffer", "getSpecialFoilBuffer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CaptureSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer carbon$buffer(first.carbon.compat.RenderCompat.CaptureSource source, net.minecraft.client.renderer.rendertype.RenderType type) {
        return Carbon.Client.IRIS.getBuffer(source, type);
    }


}
