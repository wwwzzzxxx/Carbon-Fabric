package first.carbon.client;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Retain shadow geometry while the ordered native selection and positions match.
final class DirectShadowSelection {
    private final List<ItemEntity> visible = new ArrayList<>();
    private final FloatArrayList partials = new FloatArrayList();
    private final List<ItemEntity> retained = new ArrayList<>();
    private final List<Vec3> positions = new ArrayList<>();
    private int retainedCount = -1;
    private int skyDarken;
    int reused;

    void begin(boolean blockChanged, int skyDarken) {
        if (blockChanged || this.skyDarken != skyDarken) invalidate();
        this.skyDarken = skyDarken;
        visible.clear();
        partials.clear();
        reused = 0;
    }

    void add(ItemEntity item, float partial) {
        if (!item.isInvisible()) {
            visible.add(item);
            partials.add(partial);
        }
    }

    void prepare(GpuShadowBatch batch, double x, double y, double z) {
        if (visible.isEmpty()) return;
        int size = visible.size();
        boolean same = batch.count == 0 && retainedCount >= 0 && retained.size() == size;
        for (int index = 0; same && index < size; index++) {
            ItemEntity item = visible.get(index);
            same = retained.get(index) == item && positions.get(index).equals(item.position()) && stationary(item);
        }
        if (same) {
            batch.count = retainedCount;
            reused = size;
        } else {
            invalidate();
            boolean retain = batch.count == 0 && size <= 16384;
            for (int index = 0; index < size; index++) {
                ItemEntity item = visible.get(index);
                batch.add(item, partials.getFloat(index), 0, x, y, z);
                if (retain && stationary(item)) {
                    retained.add(item);
                    positions.add(item.position());
                } else retain = false;
            }
            if (retain) retainedCount = batch.count;
            else invalidate();
        }
        visible.clear();
        partials.clear();
    }

    private static boolean stationary(ItemEntity item) {
        return item.xOld == item.getX() && item.yOld == item.getY() && item.zOld == item.getZ();
    }

    void invalidate() {
        retainedCount = -1;
        retained.clear();
        positions.clear();
    }
}
