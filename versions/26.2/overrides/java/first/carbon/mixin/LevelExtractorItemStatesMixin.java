package first.carbon.mixin;

import first.carbon.Carbon;
import first.carbon.client.StaticItemRenderStates;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
abstract class LevelExtractorItemStatesMixin {
    @Unique private Vec3 carbon$pendingCamera;

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void carbon$beginItemStates(Camera camera, Frustum frustum, DeltaTracker tracker, LevelRenderState state,
                                        CallbackInfo ci) {
        StaticItemRenderStates.begin(null, 0, false);
        carbon$pendingCamera = camera.position();
    }

    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void carbon$prepareFirstVisibleItem(Entity entity, float partial, CallbackInfoReturnable<EntityRenderState> cir) {
        if (carbon$pendingCamera != null && entity.getClass() == ItemEntity.class) {
            Carbon.Client.DROPPED_RENDERER.beginItemExtraction(carbon$pendingCamera);
            carbon$pendingCamera = null;
        }
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void carbon$endItemStates(CallbackInfo ci) {
        carbon$pendingCamera = null;
        StaticItemRenderStates.end();
    }
}
