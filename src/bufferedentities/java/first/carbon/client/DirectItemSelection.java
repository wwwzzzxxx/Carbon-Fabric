package first.carbon.client;

import com.google.common.collect.Iterables;
import first.carbon.compat.TweakerooVisibility;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Replay an uploaded instance buffer for an unchanged native selection within the same client tick.
final class DirectItemSelection {
    final StaticItemVisibility visibility = new StaticItemVisibility();
    private List<Entity> retained = List.of();
    private final List<Entity> nativeItems = new ArrayList<>();
    private final List<Entity> nativeRemainder = new ArrayList<>();
    private final Map<GpuDroppedItemBatch, Integer> counts = new IdentityHashMap<>();
    private List<Entity> recording = List.of();
    private int submitted;
    private long tick;
    private int gameTick;
    private int skyDarken;
    private boolean shadowsEnabled;
    private int shadowCount;
    private int instanceCount;
    private int nativeBudget, nativeAccepted;

    static boolean requiresNativeDispatch() {
        var loader = FabricLoader.getInstance();
        return loader.isModLoaded("entityculling")
                || loader.isModLoaded("itemphysic") || loader.isModLoaded("itemphysiclite")
                || loader.isModLoaded("lambdynlights") || loader.isModLoaded("ryoamiclights");
    }

    static boolean requiresNativeVisibility() {
        return TweakerooVisibility.requiresNativeSelection();
    }

    int restoreOrRecord(List<Entity> visible, long tick, int gameTick, int skyDarken,
                        boolean drawShadows, GpuShadowBatch shadows, int frame) {
        if (matches(tick, gameTick, skyDarken, drawShadows) && sameSelection(visible)) {
            int restored = restore(shadows, frame);
            if (restored > 0) return restored;
        }
        clearPacket();
        recording = visible;
        this.tick = tick;
        this.gameTick = gameTick;
        this.skyDarken = skyDarken;
        shadowsEnabled = drawShadows;
        return 0;
    }

    int fixedSelectionCount() {
        return visibility.fixedSelectionCount(retained);
    }

    List<Entity> selectItems(List<Entity> visible) {
        nativeItems.clear();
        nativeRemainder.clear();
        for (int index = 0; index < visible.size(); index++) {
            if (!(visible.get(index) instanceof ItemEntity)) {
                nativeItems.addAll(visible.subList(0, index));
                for (; index < visible.size(); index++) {
                    Entity entity = visible.get(index);
                    (entity instanceof ItemEntity ? nativeItems : nativeRemainder).add(entity);
                }
                return nativeItems;
            }
        }
        return visible;
    }

    List<Entity> remainder() {
        return nativeRemainder;
    }

    Iterable<Entity> limitCandidates(Iterable<Entity> entities, int budget) {
        nativeBudget = budget;
        nativeAccepted = 0;
        return Iterables.filter(entities, entity -> nativeBudget > 0 || !(entity instanceof ItemEntity));
    }

    void nativeItemSelected(Object entity) {
        if (nativeBudget > 0 && entity instanceof ItemEntity) {
            nativeBudget--;
            nativeAccepted++;
        }
    }

    boolean acceptedNativeItems() {
        return nativeAccepted > 0;
    }

    boolean rememberCappedVisibility(List<Entity> visible) {
        if (visible.size() < 256 || visible.size() > 16384) return false;
        List<Entity> items = selectItems(visible);
        for (Entity entity : items) {
            if (!(entity instanceof ItemEntity item) || item.getClass() != ItemEntity.class || item.tickCount == 0
                    || item.xOld != item.getX() || item.yOld != item.getY() || item.zOld != item.getZ()) {
                visibility.discard();
                return false;
            }
        }
        List<Entity> previous = visibility.selection();
        List<Entity> cached = visibility.remember(items);
        return cached != null && cached != previous;
    }

    int restoreFixed(long tick, int gameTick, int skyDarken, boolean drawShadows, GpuShadowBatch shadows, int frame) {
        return matches(tick, gameTick, skyDarken, drawShadows) ? restore(shadows, frame) : 0;
    }

    private boolean matches(long tick, int gameTick, int skyDarken, boolean drawShadows) {
        return this.tick == tick && this.gameTick == gameTick && this.skyDarken == skyDarken
                && shadowsEnabled == drawShadows;
    }

    private int restore(GpuShadowBatch shadows, int frame) {
        if (shadows == null ? shadowCount != 0 : shadows.count != 0 || shadows.flushed) return 0;
        for (GpuDroppedItemBatch batch : counts.keySet()) {
            if (batch.closed || batch.count != 0 || batch.flushed) return 0;
        }
        counts.forEach((batch, count) -> {
            batch.count = count;
            batch.lastFrame = frame;
        });
        if (shadows != null) shadows.count = shadowCount;
        return instanceCount;
    }

    private boolean sameSelection(List<Entity> visible) {
        if (retained.isEmpty() || retained.size() != visible.size()) return false;
        for (int index = 0; index < visible.size(); index++) {
            if (retained.get(index) != visible.get(index)) return false;
        }
        return true;
    }

    void add(ItemEntity item, GpuDroppedItemBatch batch, boolean staticModel) {
        if (!recording.isEmpty()) {
            if (submitted < recording.size() && recording.get(submitted) == item && staticModel
                    && item.getClass() == ItemEntity.class && item.tickCount > 0
                    && item.xOld == item.getX() && item.yOld == item.getY() && item.zOld == item.getZ()) {
                submitted++;
                counts.putIfAbsent(batch, 0);
            } else {
                clearPacket();
            }
        }
    }

    boolean finish(int instances, int shadows, boolean rememberVisibility) {
        if (!recording.isEmpty()) {
            if (submitted == recording.size() && counts.keySet().stream().allMatch(batch -> batch.flushed)
                    && counts.keySet().stream().mapToInt(batch -> batch.count).sum() == instances) {
                List<Entity> previous = visibility.selection();
                List<Entity> cached = rememberVisibility ? visibility.remember(recording) : null;
                retained = cached == null ? List.copyOf(recording) : cached;
                counts.replaceAll((batch, ignored) -> batch.count);
                instanceCount = instances;
                shadowCount = shadows;
                recording = List.of();
                return cached != null && cached != previous;
            } else {
                clearPacket();
            }
        }
        return false;
    }

    void clear() {
        clearPacket();
        nativeItems.clear();
        nativeRemainder.clear();
        visibility.discard();
        visibility.endFrame();
    }

    void clearPacket() {
        recording = List.of();
        retained = List.of();
        counts.clear();
        submitted = 0;
    }
}
