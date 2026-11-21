package first.carbon.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Map;

public final class ReEntityOutlinerItemLookup {
    private static final Map<?, ?> PROPERTIES = findProperties();

    private ReEntityOutlinerItemLookup() {}

    public static Object properties() {
        return PROPERTIES.get("minecraft:item");
    }

    private static Map<?, ?> findProperties() {
        boolean supported = FabricLoader.getInstance().getModContainer("reentityoutliner")
                .map(mod -> "26.3.0.0".equals(mod.getMetadata().getVersion().getFriendlyString()))
                .orElse(false);
        if (!supported) return Map.of();
        try {
            Class<?> manager = Class.forName("net.reentityoutliner.config.ConfigManager");
            Object value = manager.getField("outlinedEntityTypes").get(null);
            if (value instanceof Map<?, ?> properties) return properties;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The original lookup remains available if the optional provider differs.
        }
        return Map.of();
    }
}
