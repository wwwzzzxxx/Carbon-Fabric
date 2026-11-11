package first.carbon.mixin;

import first.carbon.client.ItemBatchRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemStack.class)
public abstract class ItemStackFoilMixin implements ItemBatchRenderer.FoilState {
    @Unique
    private int imo$foilTick = Integer.MIN_VALUE;
    @Unique
    private int imo$foilVersion;
    @Unique
    private boolean imo$foil;

    @Override
    public boolean imo$foil(int tick) {
        ItemStack stack = (ItemStack) (Object) this;
        int version = ((ItemBatchRenderer.ComponentVersion) (Object) stack).imo$version();
        if (imo$foilTick != tick || imo$foilVersion != version) {
            imo$foilTick = tick;
            imo$foilVersion = version;
            imo$foil = stack.hasFoil();
        }
        return imo$foil;
    }
}
