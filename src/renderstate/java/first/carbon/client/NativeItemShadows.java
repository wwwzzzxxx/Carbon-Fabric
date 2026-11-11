package first.carbon.client;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Keep native shadow geometry for the same ordered set of visible item states.
final class NativeItemShadows {
    private final List<ItemEntityRenderState> visible = new ArrayList<>();
    private final List<ItemEntityRenderState> retained = new ArrayList<>();
    private Vec3 retainedCamera;
    private int pieces;
    private int retainedCount = -1;

    void begin() {
        visible.clear();
        pieces = 0;
    }

    boolean canAdd(ItemEntityRenderState state) {
        return state.shadowPieces.size() <= 65536 - pieces;
    }

    void add(ItemEntityRenderState state) {
        visible.add(state);
        pieces += state.shadowPieces.size();
    }

    void prepare(GpuShadowBatch batch, Vec3 camera) {
        if (!visible.isEmpty()) {
            if (batch.count == 0 && retainedCount >= 0 && camera.equals(retainedCamera) && visible.equals(retained)) {
                batch.count = retainedCount;
            } else {
                boolean keep = batch.count == 0;
                retained.clear();
                retainedCount = -1;
                for (ItemEntityRenderState state : visible) batch.addExtracted(state, camera);
                if (keep) {
                    retained.addAll(visible);
                    retainedCamera = camera;
                    retainedCount = batch.count;
                }
            }
            visible.clear();
        }
    }

    void clear() {
        begin();
        retained.clear();
        retainedCamera = null;
        retainedCount = -1;
    }
}
