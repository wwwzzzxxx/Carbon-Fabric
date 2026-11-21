package first.carbon.client;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Keep native shadow geometry for the same ordered set of visible item states.
final class NativeItemShadows {
    private final List<ItemEntityRenderState> visible = new ArrayList<>();
    private List<ItemEntityRenderState> retained = List.of();
    private Packet pending;
    private Vec3 retainedCamera;
    private int pieces;
    private int retainedCount = -1;

    void begin() {
        visible.clear();
        pending = null;
        pieces = 0;
    }

    boolean canAdd(ItemEntityRenderState state) {
        return state.shadowPieces.size() <= 65536 - pieces;
    }

    void add(ItemEntityRenderState state) {
        materialize();
        visible.add(state);
        pieces += state.shadowPieces.size();
    }

    Packet packet() {
        if (pending == null) {
            pending = new Packet(List.copyOf(visible), pieces);
            visible.clear();
        }
        return pending;
    }

    void replay(Packet packet) {
        if (pending == null && visible.isEmpty()) {
            pending = packet;
        } else {
            materialize();
            visible.addAll(packet.states);
        }
        pieces += packet.pieces;
    }

    private void materialize() {
        if (pending != null) {
            visible.addAll(pending.states);
            pending = null;
        }
    }

    void prepare(GpuShadowBatch batch, Vec3 camera) {
        List<ItemEntityRenderState> current = pending != null ? pending.states : visible;
        if (current.isEmpty()) return;
        boolean keep = batch.count == 0;
        boolean reused = keep && retainedCount >= 0 && camera.equals(retainedCamera)
                && (current == retained || current.equals(retained));
        if (reused) {
            batch.count = retainedCount;
        } else {
            for (ItemEntityRenderState state : current) batch.addExtracted(state, camera);
        }
        if (keep) {
            if (pending != null) {
                retained = current;
            } else if (!reused) {
                retained = List.copyOf(current);
            }
            retainedCamera = camera;
            retainedCount = batch.count;
        } else {
            retained = List.of();
            retainedCount = -1;
        }
        pending = null;
        visible.clear();
    }

    void clear() {
        begin();
        retained = List.of();
        retainedCamera = null;
        retainedCount = -1;
    }

    record Packet(List<ItemEntityRenderState> states, int pieces) {}
}
