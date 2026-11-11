package first.carbon.mixin;
import first.carbon.compat.ItemStateAccess;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.UUID;
@Mixin(ItemEntityRenderState.class)
public class ItemRenderStateMixin implements ItemStateAccess {
    @Unique private int carbon$id;
    @Unique private UUID carbon$uuid;
    @Unique private float carbon$partial;
    public int carbon$id() { return carbon$id; }
    public UUID carbon$uuid() { return carbon$uuid; }
    public float carbon$partial() { return carbon$partial; }
    public void carbon$bind(ItemEntity entity, float partial) { carbon$id=entity.getId(); carbon$uuid=entity.getUUID(); carbon$partial=partial; }
}
