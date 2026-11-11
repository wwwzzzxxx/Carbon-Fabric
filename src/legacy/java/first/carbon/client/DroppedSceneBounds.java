package first.carbon.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Conservative visibility bound for all of the cached, ordinary dropped items. */
final class DroppedSceneBounds {
    private double minX, minY, minZ, maxX, maxY, maxZ, maxSize;
    private boolean finite, frustumCull;
    private AABB box;

    DroppedSceneBounds() { clear(); }

    void clear() {
        minX = minY = minZ = Double.POSITIVE_INFINITY;
        maxX = maxY = maxZ = Double.NEGATIVE_INFINITY;
        maxSize = 0;
        finite = frustumCull = true;
        box = null;
    }

    void include(ItemEntity item) {
        AABB bounds = item.getBoundingBoxForCulling();
        minX = Math.min(minX, Math.min(bounds.minX - 0.5, item.getX()));
        minY = Math.min(minY, Math.min(bounds.minY - 0.5, item.getY()));
        minZ = Math.min(minZ, Math.min(bounds.minZ - 0.5, item.getZ()));
        maxX = Math.max(maxX, Math.max(bounds.maxX + 0.5, item.getX()));
        maxY = Math.max(maxY, Math.max(bounds.maxY + 0.5, item.getY()));
        maxZ = Math.max(maxZ, Math.max(bounds.maxZ + 0.5, item.getZ()));
        double size = item.getBoundingBox().getSize();
        maxSize = Math.max(maxSize, size);
        finite &= Double.isFinite(size) && Double.isFinite(minX) && Double.isFinite(minY)
                && Double.isFinite(minZ) && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ);
        frustumCull &= !item.noCulling;
        box = null;
    }

    boolean outside(Frustum frustum, Vec3 camera, double viewScale) {
        if (!finite || minX > maxX) return false;
        double distance = squaredGap(camera.x, minX, maxX) + squaredGap(camera.y, minY, maxY) + squaredGap(camera.z, minZ, maxZ);
        double radius = maxSize * (64.0 * viewScale);
        if (distance > radius * radius) return true;
        if (!frustumCull) return false;
        if (box == null) box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        return !frustum.isVisible(box);
    }

    private static double squaredGap(double position, double min, double max) {
        double gap = Math.max(0.0, Math.max(min - position, position - max));
        return gap * gap;
    }
}
