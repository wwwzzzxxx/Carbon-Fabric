package first.carbon.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

// Let mods that replace item poses run before Carbon batches their model submissions.
public final class ItemEntityCompatibility {
    private static final boolean externalDropTransforms = detectExternalDropTransforms();
    private MethodHandle preciseModel;

    private static boolean detectExternalDropTransforms() {
        var loader = FabricLoader.getInstance();
        return loader.isModLoaded("itemdrops") || loader.isModLoaded("itemphysic")
                || loader.isModLoaded("itemphysiclite");
    }

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

    public static boolean hasExternalDropTransforms() {
        return externalDropTransforms;
    }

    public boolean requiresEntityTransforms() {
        if (externalDropTransforms) return true;
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

    public boolean requiresNativeVisibility() {
        return TweakerooVisibility.requiresNativeSelection();
    }
}
