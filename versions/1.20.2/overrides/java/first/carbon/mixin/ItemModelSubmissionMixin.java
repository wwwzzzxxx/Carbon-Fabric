package first.carbon.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ItemRenderer.class,priority=500)
public abstract class ItemModelSubmissionMixin {
    @Inject(method="renderModelLists",at=@At("HEAD"),cancellable=true)
    private void carbon$submit(BakedModel model,ItemStack stack,int light,int overlay,PoseStack pose,VertexConsumer vertices,CallbackInfo ci) {
        if(Carbon.Client.RENDERER.submit(model,stack,light,overlay,pose,vertices)) ci.cancel();
    }
}
