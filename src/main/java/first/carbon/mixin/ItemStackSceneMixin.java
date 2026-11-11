package first.carbon.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackSceneMixin implements ItemBatchRenderer.ComponentVersion {
    @Unique private boolean imo$tracked;
    @Unique private int imo$version, imo$tagHash;
    @Unique private int imo$tick = Integer.MIN_VALUE;
    @Override public void imo$track() { imo$tracked = true; }
    @Override public boolean imo$tracked() { return imo$tracked; }
    @Override public int imo$version() {
        int tick = Minecraft.getInstance().gui.getGuiTicks();
        if (tick != imo$tick) {
            imo$tick = tick;
            var tag = ((ItemStack) (Object) this).getTag();
            int hash = tag == null ? 0 : tag.hashCode();
            if (hash != imo$tagHash) {
                imo$tagHash = hash;
                imo$version++;
            }
        }
        return imo$version;
    }
    @Inject(method = {"setCount", "setTag"}, at = @At("HEAD"))
    private void imo$changed(CallbackInfo ci) {
        imo$tick = Integer.MIN_VALUE;
        imo$version++;
        if (imo$tracked && RenderSystem.isOnRenderThread()) Carbon.Client.DROPPED_RENDERER.sceneDirty = true;
    }
}
