package first.carbon.client;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

final class ItemRenderLimit {
    static void trim(List<Entity> visible, int budget) {
        int kept = 0;
        for (int index = 0; index < visible.size(); index++) {
            Entity entity = visible.get(index);
            if (entity instanceof ItemEntity) {
                if (budget == 0) continue;
                budget--;
            }
            if (kept != index) visible.set(kept, entity);
            kept++;
        }
        if (kept < visible.size()) visible.subList(kept, visible.size()).clear();
    }

    static void select(Iterable<Entity> entities, int budget, Predicate<ItemEntity> visible, List<Entity> selected) {
        selected.clear();
        for (Entity entity : entities) {
            if (!(entity instanceof ItemEntity)) {
                selected.add(entity);
            } else if (budget > 0 && visible.test((ItemEntity) entity)) {
                selected.add(entity);
                budget--;
            }
        }
    }
}
