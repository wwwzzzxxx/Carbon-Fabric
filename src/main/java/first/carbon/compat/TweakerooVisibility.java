package first.carbon.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.fabricmc.loader.api.FabricLoader;

public final class TweakerooVisibility {
    public static final int NO_COUNTER = Integer.MIN_VALUE;
    private static final boolean loaded = FabricLoader.getInstance().isModLoaded("tweakeroo");
    private static final Options options = loaded ? findOptions() : null;
    private static final MethodHandle selective = optionalBoolean("FeatureToggle", "TWEAK_SELECTIVE_BLOCKS_RENDERING");
    private static final MethodHandle selectiveHides = optionalBoolean("Configs$Generic", "SELECTIVE_BLOCKS_HIDE_ENTITIES");
    private static final MethodHandle renderAll = optionalBoolean("FeatureToggle", "TWEAK_RENDER_ALL_ENTITIES");
    private static final MethodHandle hideItems = optionalBoolean("Configs$Disable", "DISABLE_ITEM_ENTITY_RENDERING");
    private static final Field itemCounter = loaded ? findItemCounter() : null;

    public static boolean requiresNativeSelection() {
        if (!loaded) return false;
        if (options == null) return true;
        try {
            return (boolean) options.entitiesHidden.invokeExact() || overridesVisibility()
                    || (boolean) options.limitEnabled.invokeExact() && (int) options.itemLimit.invokeExact() >= 0;
        } catch (Throwable exception) {
            return true;
        }
    }

    public static boolean overridesVisibility() {
        try {
            return hideItems != null && (boolean) hideItems.invokeExact()
                    || renderAll != null && (boolean) renderAll.invokeExact()
                    || selective != null && (boolean) selective.invokeExact()
                    && (selectiveHides == null || (boolean) selectiveHides.invokeExact());
        } catch (Throwable exception) {
            return true;
        }
    }

    public static int itemDrawLimit() {
        if (options == null || itemCounter == null || overridesVisibility()) return -1;
        try {
            return (boolean) options.limitEnabled.invokeExact() && !(boolean) options.entitiesHidden.invokeExact()
                    ? Math.max(-1, (int) options.itemLimit.invokeExact()) : -1;
        } catch (Throwable exception) {
            return -1;
        }
    }

    public static int suspendItemCounting() {
        if (itemCounter == null) return NO_COUNTER;
        try {
            int previous = itemCounter.getInt(null);
            if (previous == NO_COUNTER) return NO_COUNTER;
            itemCounter.setInt(null, Integer.MIN_VALUE);
            return previous;
        } catch (IllegalAccessException exception) {
            return NO_COUNTER;
        }
    }

    public static void restoreItemCounting(int previous) {
        if (previous != NO_COUNTER) {
            try {
                itemCounter.setInt(null, previous);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to restore Tweakeroo's dropped-item counter", exception);
            }
        }
    }

    private static Field findItemCounter() {
        for (String name : new String[] {"tweaks.MiscTweaks", "Tweakeroo"}) {
            try {
                Class<?> type = Class.forName("fi.dy.masa.tweakeroo." + name, false, TweakerooVisibility.class.getClassLoader());
                Field field = type.getField("renderCountItems");
                int modifiers = field.getModifiers();
                if (field.getType() == int.class && Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) return field;
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        return null;
    }

    private static MethodHandle optionalBoolean(String config, String field) {
        if (loaded) {
            try {
                return getter(config, field, "getBooleanValue");
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        return null;
    }

    private static Options findOptions() {
        try {
            return new Options(
                    getter("FeatureToggle", "TWEAK_RENDER_LIMIT_ENTITIES", "getBooleanValue"),
                    getter("Configs$Generic", "RENDER_LIMIT_ITEM", "getIntegerValue"),
                    getter("Configs$Disable", "DISABLE_ENTITY_RENDERING", "getBooleanValue"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private static MethodHandle getter(String config, String field, String method) throws ReflectiveOperationException {
        Object option = Class.forName("fi.dy.masa.tweakeroo.config." + config).getField(field).get(null);
        return MethodHandles.publicLookup().unreflect(option.getClass().getMethod(method)).bindTo(option);
    }

    private static final class Options {
        final MethodHandle limitEnabled, itemLimit, entitiesHidden;

        Options(MethodHandle limitEnabled, MethodHandle itemLimit, MethodHandle entitiesHidden) {
            this.limitEnabled = limitEnabled;
            this.itemLimit = itemLimit;
            this.entitiesHidden = entitiesHidden;
        }
    }
}
