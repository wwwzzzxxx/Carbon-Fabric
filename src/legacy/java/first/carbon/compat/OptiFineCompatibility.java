package first.carbon.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;

/** The old OptiFabric pipeline can instance standard models selected by its entity renderer. */
public final class OptiFineCompatibility {
    private final boolean present = FabricLoader.getInstance().isModLoaded("optifabric");
    private boolean inspected;
    private Method shaders, emissive, multiTexture, fastRender, customItems, dynamicLights;
    private Field itemProperties;
    private Object[] customRules;
    private boolean dynamicLighting;
    private boolean groupedSelection;

    public boolean present() {
        return present;
    }

    public boolean supportsGroupedSelection() {
        if (!inspected) inspect();
        return groupedSelection;
    }

    public boolean canInstance() {
        if (!present) return true;
        if (!inspected) inspect();
        if (itemProperties == null) return false;
        try {
            if (enabled(shaders) || enabled(emissive) || enabled(multiTexture) || enabled(fastRender)) return false;
            customRules = enabled(customItems) ? (Object[]) itemProperties.get(null) : null;
            dynamicLighting = enabled(dynamicLights);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    public boolean dynamicLighting() {
        return dynamicLighting;
    }

    public boolean hasCustomRule(Item item) {
        if (customRules == null) return false;
        int id = Item.getId(item);
        return id >= 0 && id < customRules.length && customRules[id] != null;
    }

    private void inspect() {
        inspected = true;
        try {
            Class<?> config = Class.forName("net.optifine.Config");
            groupedSelection = "OptiFine_1.16.5_HD_U_G8".equals(config.getMethod("getVersion").invoke(null));
            shaders = config.getMethod("isShaders");
            multiTexture = config.getMethod("isMultiTexture");
            fastRender = config.getMethod("isFastRender");
            customItems = config.getMethod("isCustomItems");
            dynamicLights = config.getMethod("isDynamicLights");
            emissive = Class.forName("net.optifine.EmissiveTextures").getMethod("isActive");
            Field rules = Class.forName("net.optifine.CustomItems").getDeclaredField("itemProperties");
            rules.setAccessible(true);
            itemProperties = rules;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
    }

    private static boolean enabled(Method method) throws ReflectiveOperationException {
        return (Boolean) method.invoke(null);
    }
}
