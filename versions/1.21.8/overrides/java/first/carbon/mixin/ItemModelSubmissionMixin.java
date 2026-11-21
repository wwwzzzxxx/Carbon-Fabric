package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.compat.ItemGeometry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ItemRenderer.class)
public abstract class ItemModelSubmissionMixin {
    // Sodium can emit the quads without entering renderQuadList.
    @WrapOperation(method = "renderItem", order = 1100, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderQuadList("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Ljava/util/List;[III)V"))
    private static void carbon$submit(PoseStack pose, VertexConsumer vertices, List<BakedQuad> quads,
                                      int[] tints, int light, int overlay, Operation<Void> render,
                                      @Local(argsOnly = true) ItemDisplayContext context,
                                      @Local(argsOnly = true) MultiBufferSource source,
                                      @Local(argsOnly = true) RenderType layer,
                                      @Local(argsOnly = true) FoilType foil) {
        if (Carbon.CONFIG.enabled.get()) {
            RenderType deferredLayer = context == ItemDisplayContext.GROUND && foil == FoilType.NONE
                    && Carbon.Client.IRIS.defersWorldBuffers(source) ? layer : null;
            if (Carbon.Client.RENDERER.submit(ItemGeometry.plain(quads), tints, light, overlay, pose, vertices,
                    deferredLayer)) return;
        }
        render.call(pose, vertices, quads, tints, light, overlay);
    }
}
