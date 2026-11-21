package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import first.carbon.compat.LegacyItemPhysicFireCache;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "team.creative.itemphysic.server.ItemPhysicServer", remap = false)
public abstract class LegacyItemPhysicFireMixin {
    @WrapOperation(method = "fireImmune", at = @At(value = "INVOKE",
            target = "Lteam/creative/creativecore/common/util/type/list/SortingList;canPass", remap = false),
            remap = false, allow = 1)
    private static boolean carbon$cachedCanBurn(@Coerce Object rules, ItemStack stack, Operation<Boolean> original) {
        return LegacyItemPhysicFireCache.canBurn(rules, stack, original);
    }
}
