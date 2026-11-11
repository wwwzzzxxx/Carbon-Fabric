package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.util.EnumMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.irisshaders.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager", remap = false)
public abstract class IrisEmptyRenderGroupMixin {
    // Carbon instances bypass Iris vertex buffers, leaving their render groups empty.
    @WrapWithCondition(method = {"startGroup", "maybeStartGroup", "endGroup"},
            at = @At(value = "INVOKE", target = "Ljava/util/EnumMap;clear()V"), require = 0, remap = false)
    private boolean carbon$hasRenderTypes(EnumMap<?, ?> group) {
        return !group.isEmpty();
    }
}
