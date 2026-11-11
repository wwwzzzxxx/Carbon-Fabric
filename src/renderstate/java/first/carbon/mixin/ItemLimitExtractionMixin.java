package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import first.carbon.client.ExtractionItemLimit;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
abstract class ItemLimitExtractionMixin {
    @Unique private final ExtractionItemLimit carbon$itemLimit = new ExtractionItemLimit();

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/LevelRenderState;)V"))
    private void carbon$selectItems(LevelRenderer renderer, Camera camera, Frustum frustum, DeltaTracker ticks,
                                    LevelRenderState state, Operation<Void> original) {
        carbon$itemLimit.extract(() -> original.call(renderer, camera, frustum, ticks, state));
    }

    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
    private Iterable<Entity> carbon$withinItemLimit(ClientLevel level, Operation<Iterable<Entity>> original) {
        return carbon$itemLimit.candidates(original.call(level));
    }

    @ModifyArg(method = "extractVisibleEntities", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"), index = 0)
    private Entity carbon$countItem(Entity entity) {
        carbon$itemLimit.selected(entity);
        return entity;
    }
}
