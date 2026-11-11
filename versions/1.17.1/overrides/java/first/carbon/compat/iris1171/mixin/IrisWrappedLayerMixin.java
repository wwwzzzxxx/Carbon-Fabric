package first.carbon.compat.iris1171.mixin;

import first.carbon.compat.IrisCompatibility;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "net.coderbot.iris.layer.OuterWrappedRenderType", remap = false)
public interface IrisWrappedLayerMixin extends IrisCompatibility.LayerAccess {
    @Override
    @Accessor(value = "wrapped", remap = false)
    RenderType carbon$wrapped();
}
