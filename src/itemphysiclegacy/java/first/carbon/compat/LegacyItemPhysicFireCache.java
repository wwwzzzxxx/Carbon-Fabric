package first.carbon.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import first.carbon.Carbon;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LegacyItemPhysicFireCache {
    private static final String INGREDIENT = "team.creative.creativecore.common.util.ingredient.CreativeIngredient";
    private static final Set<String> STACK_RULES = Set.of(
            INGREDIENT + "Block", INGREDIENT + "BlockTag", INGREDIENT + "Fuel", INGREDIENT + "Item",
            INGREDIENT + "ItemTag", INGREDIENT + "ItemStack", INGREDIENT + "Material");
    private static final Map<Item, Decision> decisions = new IdentityHashMap<>();
    private static boolean rendering;
    private static Object currentRules;
    private static boolean cacheable;

    private LegacyItemPhysicFireCache() {}

    public static void beginPass() {
        endPass();
        rendering = Carbon.CONFIG.enabled.get();
    }

    public static void endPass() {
        rendering = false;
        currentRules = null;
        cacheable = false;
        decisions.clear();
    }

    public static boolean canBurn(Object rules, ItemStack stack, Operation<Boolean> original) {
        if (!RenderSystem.isOnRenderThread() || !rendering || rules == null || stack.isEmpty()) {
            return original.call(rules, stack);
        }
        if (currentRules != rules) {
            currentRules = rules;
            cacheable = usesStackRules(rules);
            decisions.clear();
        }
        if (!cacheable) return original.call(rules, stack);

        Item item = stack.getItem();
        Decision decision = decisions.get(item);
        if (decision != null && ItemStack.matches(decision.stack(), stack)) return decision.canBurn();

        boolean canBurn = original.call(rules, stack);
        decisions.put(item, new Decision(stack.copy(), canBurn));
        return canBurn;
    }

    private static boolean usesStackRules(Object rules) {
        if (!rules.getClass().getName().equals("team.creative.creativecore.common.util.type.list.SortingList")
                || !(rules instanceof List<?> entries)) return false;
        // CreativeCore can edit these rules in place. Only share stock stack rules for this render pass.
        for (Object entry : entries) {
            if (entry == null || !STACK_RULES.contains(entry.getClass().getName())) return false;
        }
        return true;
    }

    private record Decision(ItemStack stack, boolean canBurn) {}
}
