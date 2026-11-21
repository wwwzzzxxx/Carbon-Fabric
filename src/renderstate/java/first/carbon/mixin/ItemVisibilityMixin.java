package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import first.carbon.client.StaticItemRenderStates;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
abstract class ItemVisibilityMixin {
    @WrapOperation(method = "shouldRender", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    private boolean carbon$reuseItemVisibility(EntityRenderer<?, ?> renderer, Entity entity, Frustum frustum,
                                               double x, double y, double z, Operation<Boolean> original) {
        return StaticItemRenderStates.shouldRender(renderer, entity, frustum, x, y, z, 0.0F, original);
    }
}
