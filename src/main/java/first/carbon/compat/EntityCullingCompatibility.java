package first.carbon.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public final class EntityCullingCompatibility {
    private final boolean entityCullingLoaded = FabricLoader.getInstance().isModLoaded("entityculling");

    public boolean requiresNativeVisibility(Minecraft minecraft) {
        // EntityCulling applies asynchronous occlusion inside LevelRenderer.renderEntity.
        return entityCullingLoaded || minecraft.levelRenderer.capturedFrustum != null
                || minecraft.levelRenderer.cullingFrustum == null;
    }

    public boolean isVisible(ItemEntity item, Minecraft minecraft, Vec3 camera) {
        return !item.isRemoved() && item.level == minecraft.level
                && minecraft.getEntityRenderDispatcher().shouldRender(item, minecraft.levelRenderer.cullingFrustum,
                    camera.x, camera.y, camera.z)
                && (minecraft.level.isOutsideBuildHeight(item.blockPosition().getY())
                    || minecraft.levelRenderer.isChunkCompiled(item.blockPosition()));
    }
}
