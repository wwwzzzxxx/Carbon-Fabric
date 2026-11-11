package first.carbon.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import net.minecraft.client.renderer.entity.ItemRenderer;
import first.carbon.compat.ItemGeometry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ItemRenderer.class,priority=500)
public abstract class ItemModelSubmissionMixin {
    @Inject(method="renderQuadList",at=@At("HEAD"),cancellable=true)
    private static void carbon$submit(PoseStack pose,VertexConsumer vertices,java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads,int[] tintLayers,int light,int overlay,CallbackInfo ci) {
        if(Carbon.CONFIG.enabled.get() && Carbon.Client.RENDERER.submit(first.carbon.compat.ItemGeometry.plain(quads),tintLayers,light,overlay,pose,vertices)) ci.cancel();
    }
}
