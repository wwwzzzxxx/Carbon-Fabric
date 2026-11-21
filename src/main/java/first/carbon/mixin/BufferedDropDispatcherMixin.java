package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import first.carbon.client.BufferedDropPoses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
abstract class BufferedDropDispatcherMixin {
    @Redirect(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;"
            + "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void carbon$renderDrop(EntityRenderer<Entity> renderer, Entity entity, float yaw, float partial,
                                   PoseStack pose, MultiBufferSource buffers, int light) {
        BufferedDropPoses poses = Carbon.Client.RENDERER.bufferedDropPoses();
        if (poses != null && renderer.getClass() == ItemEntityRenderer.class && entity instanceof ItemEntity) {
            ItemEntity item = (ItemEntity) entity;
            if (((BufferedDropEntityAccessor) item).carbon$dropStuckSpeed().lengthSqr() == 0
                    && BufferedDropPoses.stationary(item)) {
                BakedModel model = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(item.getItem());
                if (model.getClass() == SimpleBakedModel.class && model.getOverrides() == ItemOverrides.EMPTY) {
                    Level level = item.getCommandSenderWorld();
                    BlockState in = level.getBlockState(item.blockPosition());
                    BlockState below = level.getBlockState(item.blockPosition().below());
                    poses.render(item, pose, buffers, light, model, in, below, 1,
                            BufferedDropPoses.pitch(item), BufferedDropPoses.yaw(item),
                            () -> renderer.render(entity, yaw, partial, pose, buffers, light));
                    return;
                }
            }
        }
        renderer.render(entity, yaw, partial, pose, buffers, light);
    }
}
