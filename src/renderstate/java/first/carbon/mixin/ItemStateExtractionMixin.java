package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import first.carbon.client.StaticItemRenderStates;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class ItemStateExtractionMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private void carbon$reuseBeforeDispatcher(Entity entity, float partial, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = StaticItemRenderStates.reuseBeforeDispatcher(entity, partial);
        if (state != null) cir.setReturnValue(state);
    }

    @WrapOperation(method = "extractEntity", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private EntityRenderState carbon$retainPlainItem(EntityRenderer<?, ?> renderer, Entity entity, float partial,
                                                      Operation<EntityRenderState> original) {
        return StaticItemRenderStates.extract(renderer, entity, partial, original);
    }
}
