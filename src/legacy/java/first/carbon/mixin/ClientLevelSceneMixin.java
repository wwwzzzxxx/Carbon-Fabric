package first.carbon.mixin;

import first.carbon.Carbon;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelSceneMixin {
    @Inject(method = {"addEntity", "onChunkLoaded", "unload"}, at = @At("HEAD"))
    private void carbon$invalidate(CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.sceneDirty = true;
    }

    @Inject(method = "onEntityRemoved", at = @At("HEAD"))
    private void carbon$removeEntity(Entity entity, CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.entityRemoved(entity);
    }

    @Inject(method = {"sendBlockUpdated", "onChunkLoaded", "unload"}, at = @At("HEAD"))
    private void carbon$lightChanged(CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.lightingChanged();
    }
}
