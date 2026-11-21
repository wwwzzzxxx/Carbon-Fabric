package first.carbon.mixin;

import first.carbon.Carbon;
import first.carbon.compat.ReEntityOutlinerItemLookup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.reentityoutliner.config.ConfigManager", remap = false)
abstract class ReEntityOutlinerItemLookupMixin {
    @Inject(method = "getOrCreateEntityProperties(Lnet/minecraft/world/entity/EntityType;)Lnet/reentityoutliner/util/EntityTypesProperties;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void carbon$lookupItem(EntityType<?> type, CallbackInfoReturnable<Object> callback) {
        if (type != EntityTypes.ITEM || !Carbon.CONFIG.enabled.get()) return;

        // Read the provider's live entry; the original method still creates missing entries.
        Object properties = ReEntityOutlinerItemLookup.properties();
        if (properties != null) callback.setReturnValue(properties);
    }
}
