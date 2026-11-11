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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackSceneMixin implements ItemBatchRenderer.ComponentVersion {
    @Unique private boolean imo$tracked;
    @Unique private int imo$version, imo$componentHash;
    @Unique private int imo$tick = Integer.MIN_VALUE;
    @Override public void imo$track() { imo$tracked = true; }
    @Override public boolean imo$tracked() { return imo$tracked; }
    @Override public int imo$version() {
        int tick = Minecraft.getInstance().gui.getGuiTicks();
        if (tick != imo$tick) {
            imo$tick = tick;
            int hash = ((ItemStack) (Object) this).getComponentsPatch().hashCode();
            if (hash != imo$componentHash) {
                imo$componentHash = hash;
                imo$version++;
            }
        }
        return imo$version;
    }
    @Unique private void carbon$changed() {
        imo$tick = Integer.MIN_VALUE;
        imo$version++;
        if (imo$tracked && RenderSystem.isOnRenderThread()) Carbon.Client.DROPPED_RENDERER.sceneDirty = true;
    }
    @Inject(method = {"setCount", "applyComponents", "applyComponentsAndValidate"}, at = @At("HEAD"))
    private void carbon$voidChange(CallbackInfo ci) { carbon$changed(); }
    @Inject(method = {"set", "remove"}, at = @At("HEAD"))
    private void carbon$valueChange(CallbackInfoReturnable<Object> ci) { carbon$changed(); }
}
