package first.carbon.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import org.slf4j.LoggerFactory;

// Preserve renderer-level transforms supplied by TweakerMore's precise item models.
public final class ItemEntityCompatibility {
    private MethodHandle preciseModel;

    public ItemEntityCompatibility() {
        try {
            Object option = Class.forName("me.fallenbreath.tweakermore.config.TweakerMoreConfigs")
                    .getField("PRECISE_ITEM_ENTITY_MODEL").get(null);
            preciseModel = MethodHandles.publicLookup().unreflect(option.getClass().getMethod("getBooleanValue")).bindTo(option);
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException exception) {
            LoggerFactory.getLogger("Carbon").warn("TweakerMore precise item model bridge unavailable", exception);
        }
    }

    public boolean requiresEntityTransforms() {
        if (preciseModel != null) {
            try {
                return (boolean) preciseModel.invokeExact();
            } catch (Throwable exception) {
                LoggerFactory.getLogger("Carbon").warn("Unable to read precise item model option; preserving entity renderer", exception);
                return true;
            }
        }
        return false;
    }
}
