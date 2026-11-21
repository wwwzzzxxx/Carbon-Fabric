package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.compat.ItemGeometry;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class ItemModelSubmissionMixin {
    // Wrap the call after Sodium's wrapper so its fast path also goes through Carbon.
    @WrapOperation(method = "renderItem", order = 1100, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderQuadList("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Ljava/util/List;[III)V"))
    private static void carbon$submit(PoseStack pose, VertexConsumer vertices, List<BakedQuad> quads,
                                      int[] tintLayers, int light, int overlay, Operation<Void> render) {
        if (!Carbon.CONFIG.enabled.get()
                || !Carbon.Client.RENDERER.submit(ItemGeometry.plain(quads), tintLayers, light, overlay, pose, vertices)) {
            render.call(pose, vertices, quads, tintLayers, light, overlay);
        }
    }
}
