package first.carbon.compat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.UUID;
public interface ItemStateAccess {
    int carbon$id();
    UUID carbon$uuid();
    float carbon$partial();
    void carbon$bind(ItemEntity entity, float partial);
    default ItemEntity carbon$entity() {
        var level = Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity(carbon$id());
        return entity instanceof ItemEntity item && item.getUUID().equals(carbon$uuid()) ? item : null;
    }
}
