package first.carbon.client;

import first.carbon.Carbon;
import first.carbon.compat.TweakerooVisibility;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

public final class ExtractionItemLimit {
    private int remaining = -1;

    public void extract(Runnable original) {
        int limit = remaining < 0 && Carbon.CONFIG.enabled.get() && Carbon.CONFIG.optimizeDroppedItems.get()
                && Carbon.CONFIG.skipVisibilityChecks.get() && !Carbon.Client.IRIS.active()
                && !StaticItemRenderStates.requiresNativeSubmission() ? TweakerooVisibility.itemDrawLimit() : -1;
        int previous = limit >= 0 ? TweakerooVisibility.suspendItemCounting() : TweakerooVisibility.NO_COUNTER;
        if (previous == TweakerooVisibility.NO_COUNTER) {
            original.run();
        } else {
            remaining = limit;
            try {
                original.run();
            } finally {
                remaining = -1;
                TweakerooVisibility.restoreItemCounting(previous);
            }
        }
    }

    public Iterable<Entity> candidates(Iterable<Entity> entities) {
        if (remaining < 0) return entities;
        return () -> new Iterator<>() {
            private final Iterator<Entity> source = entities.iterator();
            private Entity next;

            @Override
            public boolean hasNext() {
                while (next == null && source.hasNext()) {
                    Entity candidate = source.next();
                    if (remaining != 0 || !(candidate instanceof ItemEntity)) next = candidate;
                }
                return next != null;
            }

            @Override
            public Entity next() {
                if (!hasNext()) throw new NoSuchElementException();
                Entity entity = next;
                next = null;
                return entity;
            }
        };
    }

    public void selected(Entity entity) {
        if (remaining > 0 && entity instanceof ItemEntity) remaining--;
    }
}
