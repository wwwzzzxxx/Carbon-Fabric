package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import first.carbon.client.BufferedDropPoses;
import first.carbon.compat.BufferedDropState;
import first.carbon.compat.ItemStateAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
abstract class BufferedDropDispatcherMixin {
    @Redirect(method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;"
            + "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void carbon$renderDrop(EntityRenderer<?, EntityRenderState> renderer, EntityRenderState state,
                                   PoseStack pose, MultiBufferSource buffers, int light) {
        var poses = Carbon.Client.RENDERER.bufferedDropPoses();
        if (poses != null && renderer.getClass() == ItemEntityRenderer.class && state instanceof ItemEntityRenderState drop
                && drop.ageInTicks >= 1 && drop.count == 1 && drop.nameTag == null && !drop.isInvisible
                && drop.item.activeLayerCount == 1) {
            var item = ((ItemStateAccess) drop).carbon$entity();
            var layer = drop.item.layers[0];
            if (item != null && layer.foilType == ItemStackRenderState.FoilType.NONE && layer.specialRenderer == null
                    && BufferedDropPoses.stationary(item)) {
                var id = item.getItem().get(DataComponents.ITEM_MODEL);
                var model = id == null ? null : Minecraft.getInstance().getModelManager().getItemModel(id);
                if (model != null && model.getClass() == BlockModelWrapper.class) {
                    var plain = (BlockModelWrapper) model;
                    if (plain.tints.isEmpty() && plain.model == layer.model
                            && BufferedDropState.render(poses, drop, item, pose, buffers, light,
                            layer.model, layer.renderType, null, () -> renderer.render(state, pose, buffers, light))) return;
                }
            }
        }
        renderer.render(state, pose, buffers, light);
    }
}
