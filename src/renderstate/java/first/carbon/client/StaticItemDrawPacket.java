package first.carbon.client;

import first.carbon.compat.ItemStateAccess;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Replay an unchanged set of instance buffers from the immediately preceding frame.
final class StaticItemDrawPacket {
    private List<EntityRenderState> states = List.of();
    private final List<Batch> batches = new ArrayList<>();
    private NativeItemShadows.Packet shadows;
    private Vec3 camera;
    private int frame = -1, ticks, instances;
    private long clientTick;
    private boolean recording;
    int replays;

    boolean begin(boolean eligible, List<EntityRenderState> current, int frame, int ticks, long clientTick,
                  Vec3 camera, float partial, NativeItemShadows nativeShadows) {
        recording = eligible && current.size() >= 64;
        if (!recording || this.frame != frame - 1 || this.ticks != ticks || this.clientTick != clientTick
                || !camera.equals(this.camera) || states.size() != current.size()) return false;

        for (int index = 0; index < current.size(); index++) {
            EntityRenderState state = current.get(index);
            if (state != states.get(index) || state.outlineColor != 0 || !samePartial(state, partial)) return false;
        }
        for (Batch batch : batches) {
            if (batch.buffer.closed || batch.buffer.flushed || batch.buffer.count != 0) return false;
        }
        for (Batch batch : batches) {
            batch.buffer.count = batch.count;
            batch.buffer.lastFrame = frame;
        }
        nativeShadows.replay(shadows);
        this.frame = frame;
        recording = false;
        replays++;
        return true;
    }

    void remember(List<EntityRenderState> current, Iterable<List<GpuDroppedItemBatch>> buckets,
                  NativeItemShadows nativeShadows, int frame, int ticks, long clientTick, Vec3 camera,
                  float partial, int instances, int directSubmissions) {
        if (!recording) return;
        recording = false;
        if (directSubmissions != current.size()) return;
        for (EntityRenderState state : current) {
            if (!(state instanceof ItemEntityRenderState) || state.outlineColor != 0 || !samePartial(state, partial)) return;
        }
        batches.clear();
        int count = 0;
        for (List<GpuDroppedItemBatch> bucket : buckets) {
            for (GpuDroppedItemBatch batch : bucket) {
                if (batch.count == 0) continue;
                if (!batch.entityBatch || batch.outlineType != null || batch.flushed) {
                    clear();
                    return;
                }
                batches.add(new Batch(batch, batch.count));
                count += batch.count;
            }
        }
        if (count == 0 || count != instances) {
            clear();
            return;
        }
        this.frame = frame;
        this.ticks = ticks;
        this.clientTick = clientTick;
        this.camera = camera;
        this.instances = instances;
        states = List.copyOf(current);
        shadows = nativeShadows.packet();
    }

    void end(int frame, int instances) {
        if (this.frame != frame || this.instances != instances) clear();
    }

    int instances() { return instances; }

    void clear() {
        frame = -1;
        states = List.of();
        batches.clear();
        shadows = null;
        camera = null;
        recording = false;
    }

    private static boolean samePartial(EntityRenderState state, float partial) {
        return ((ItemStateAccess) state).carbon$partial() == partial;
    }

    private record Batch(GpuDroppedItemBatch buffer, int count) {}
}
