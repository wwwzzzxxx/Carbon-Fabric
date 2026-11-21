package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import first.carbon.client.StaticItemRenderStates;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class ItemSectionVisibilityMixin {
    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiledAndVisible(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean carbon$reuseSectionVisibility(LevelRenderer renderer, BlockPos pos, Operation<Boolean> original) {
        if (!StaticItemRenderStates.canReuseVisibility()) return original.call(renderer, pos);
        byte cached = StaticItemRenderStates.sectionVisibility(pos);
        return cached != 0 ? cached > 0 : StaticItemRenderStates.rememberSectionVisibility(pos, original.call(renderer, pos));
    }
}
