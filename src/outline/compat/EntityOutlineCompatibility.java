package first.carbon.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class EntityOutlineCompatibility {
    // The version overlay enables the published providers for its Minecraft target.
    private static final boolean SUPPORTS_LPCTOOLS = false;
    private static final boolean SUPPORTS_REENTITYOUTLINER = false;
    private static final boolean SUPPORTS_ENTITYOUTLINER = true;
    private static final BooleanSupplier NONE = () -> false;
    private static final BooleanSupplier LPC = SUPPORTS_LPCTOOLS
            ? register("lpctools", EntityOutlineCompatibility::lpctools) : NONE;
    private static final BooleanSupplier RE = SUPPORTS_REENTITYOUTLINER
            ? register("reentityoutliner", EntityOutlineCompatibility::reEntityOutliner) : NONE;
    private static final BooleanSupplier LEGACY_RE = SUPPORTS_ENTITYOUTLINER
            ? register("entityoutliner", () -> legacyOutliner("net.entityoutliner", "EntityOutliner")) : NONE;

    private EntityOutlineCompatibility() {
    }

    public static boolean selectsDroppedItems() {
        return LPC.getAsBoolean() || RE.getAsBoolean() || LEGACY_RE.getAsBoolean();
    }

    private static Probe lpctools() throws ReflectiveOperationException {
        Class<?> highlight = Class.forName("lpctools.tools.entityHighlight.EntityHighlight");
        Object config = highlight.getField("EHConfig").get(null);
        Object types = highlight.getField("entityList").get(null);
        Method enabled = config.getClass().getMethod("getBooleanValue");
        Method contains = types.getClass().getMethod("contains", EntityType.class);
        return () -> (boolean) enabled.invoke(config) && (boolean) contains.invoke(types, EntityType.ITEM);
    }

    private static Probe reEntityOutliner() throws ReflectiveOperationException {
        Class<?> manager;
        try {
            manager = Class.forName("net.reentityoutliner.config.ConfigManager");
        } catch (ClassNotFoundException legacy) {
            return legacyOutliner("net.reentityoutliner", "ReEntityOutliner");
        }
        Method enabled = manager.getMethod("isOutliningEntities");
        Map<?, ?> types = (Map<?, ?>) manager.getField("outlinedEntityTypes").get(null);
        Field outlined = Class.forName("net.reentityoutliner.util.EntityTypesProperties").getField("outlined");
        return () -> {
            if (!(boolean) enabled.invoke(null)) return false;
            Object item = types.get("minecraft:item");
            return item != null && outlined.getBoolean(item);
        };
    }

    private static Probe legacyOutliner(String namespace, String entrypoint) throws ReflectiveOperationException {
        Field enabled = Class.forName(namespace + "." + entrypoint).getField("outliningEntities");
        Class<?> selector;
        try {
            selector = Class.forName(namespace + ".ui.EntitySelector");
        } catch (ClassNotFoundException original) {
            selector = Class.forName(namespace + ".EntitySelector");
        }
        Field types = selector.getField("outlinedEntityTypes");
        return () -> {
            if (!enabled.getBoolean(null)) return false;
            Object selected = types.get(null);
            if (selected instanceof Set<?>) return ((Set<?>) selected).contains(EntityType.ITEM);
            return selected instanceof Map<?, ?> && ((Map<?, ?>) selected).containsKey(EntityType.ITEM);
        };
    }

    private static BooleanSupplier register(String mod, ProbeFactory factory) {
        if (!FabricLoader.getInstance().isModLoaded(mod)) return NONE;
        try {
            Probe probe = factory.create();
            return new BooleanSupplier() {
                private boolean failed;

                @Override
                public boolean getAsBoolean() {
                    if (failed) return true;
                    try {
                        return probe.selected();
                    } catch (ReflectiveOperationException | LinkageError exception) {
                        failed = true;
                        warn(mod, exception);
                        return true;
                    }
                }
            };
        } catch (ReflectiveOperationException | LinkageError exception) {
            warn(mod, exception);
            return () -> true;
        }
    }

    private static void warn(String mod, Throwable error) {
        LogManager.getLogger("Carbon").warn("Using vanilla item selection because Carbon could not read {} outline settings", mod, error);
    }

    private interface Probe {
        boolean selected() throws ReflectiveOperationException;
    }

    private interface ProbeFactory {
        Probe create() throws ReflectiveOperationException;
    }
}
