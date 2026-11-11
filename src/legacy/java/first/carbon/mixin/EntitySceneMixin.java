package first.carbon.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import first.carbon.Carbon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntitySceneMixin {
    @Inject(method = "setPosRaw", at = @At("HEAD"))
    private void carbon$position(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity && RenderSystem.isOnRenderThread()) {
            Entity entity = (Entity) (Object) this;
            if (entity.getX() != x || entity.getY() != y || entity.getZ() != z) Carbon.Client.DROPPED_RENDERER.itemMoved();
        }
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("HEAD"))
    private void carbon$itemData(CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity && RenderSystem.isOnRenderThread()) Carbon.Client.DROPPED_RENDERER.itemDataChanged();
    }
}
