package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void carbon$collectSelectedShadow(PoseStack pose, MultiBufferSource buffers, Entity entity,
                                                     float weight, float partialTick, LevelReader level, float radius,
                                                     CallbackInfo ci) {
        if (Carbon.Client.DROPPED_RENDERER.captureShadow(entity, buffers, weight, partialTick, level, radius)) {
            ci.cancel();
        }
    }
}
