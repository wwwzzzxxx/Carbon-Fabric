package first.carbon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.compat.ItemStateAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class ExternalDropPoses {
    private final Matrix4f worldPose = new Matrix4f();
    private ItemEntityRenderState current;
    private int frameStyle;
    private int reuses;

    void beginFrame(boolean retainPoses) {
        frameStyle = retainPoses ? RetainedExternalDropPose.frameStyle() : 0;
        reuses = 0;
    }

    boolean canReuse() {
        return frameStyle != 0;
    }

    int reuses() {
        return reuses;
    }

    RetainedExternalDropPose retain(RetainedExternalDropPose cached, GpuDroppedItemBatch batch,
                                   StaticItemRenderStates.Entry entry, ItemEntity item, int light) {
        int style = RetainedExternalDropPose.style(frameStyle, entry);
        if (style == 0) return cached;
        if (cached == null) cached = new RetainedExternalDropPose(batch.instanceStride);
        cached.capture(batch, entry, item, style, light);
        return cached;
    }

    GpuDroppedItemBatch reuse(RetainedExternalDropPose cached, StaticItemRenderStates.Entry entry, ItemEntity item,
                             PoseStack pose, SubmitNodeCollector collector) {
        if (cached == null || !(collector instanceof SubmitNodeStorage)
                || (pose.last().pose().properties() & Matrix4fc.PROPERTY_IDENTITY) == 0) return null;
        var batch = cached.reuse(entry, item, RetainedExternalDropPose.style(frameStyle, entry));
        if (batch != null) reuses++;
        return batch;
    }

    ItemEntityRenderState capture(ItemEntityRenderState state) {
        ItemEntityRenderState previous = current;
        current = state;
        return previous;
    }

    ItemEntity owner(ItemStackRenderState model, int overlay, int outline) {
        if (current == null || current.item != model || overlay != OverlayTexture.NO_OVERLAY || outline != 0) return null;
        ItemEntity entity = ((ItemStateAccess) current).carbon$entity();
        return entity != null && entity.getClass() == ItemEntity.class ? entity : null;
    }

    void add(GpuDroppedItemBatch batch, PoseStack pose, int light, Vec3 camera,
             double originX, double originY, double originZ) {
        // Vanilla supplies a camera-relative pose. Carbon's instances use a stable world origin.
        var source = pose.last();
        worldPose.set(source.pose());
        worldPose.m30(worldPose.m30() + (float) (camera.x - originX));
        worldPose.m31(worldPose.m31() + (float) (camera.y - originY));
        worldPose.m32(worldPose.m32() + (float) (camera.z - originZ));
        batch.add(worldPose, source.normal(), 0, 0, false, light, false, 0, 0, 0);
    }

    static boolean matchesShadowPosition(ItemEntityRenderState state, PoseStack pose, Vec3 camera) {
        var matrix = pose.last().pose();
        return (matrix.properties() & Matrix4fc.PROPERTY_TRANSLATION) != 0
                && matrix.m30() == (float) (state.x - camera.x)
                && matrix.m31() == (float) (state.y - camera.y)
                && matrix.m32() == (float) (state.z - camera.z);
    }
}
