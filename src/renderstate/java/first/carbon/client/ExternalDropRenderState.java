package first.carbon.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// Keep vanilla geometry and shadows cached, but refresh the provider's pose data.
final class ExternalDropRenderState {
    private static final FabricLoader loader = FabricLoader.getInstance();
    private static final boolean itemDrops = loader.isModLoaded("itemdrops");
    private static final boolean itemPhysic = loader.isModLoaded("itemphysic");
    private static final boolean itemPhysicLite = loader.isModLoaded("itemphysiclite");
    private static final MethodHandle[] dropSettings = itemDrops ? loadDropSettings() : null;
    private static final MethodHandle updatePhysics = itemPhysic || itemPhysicLite ? loadPhysicsExtractor() : null;
    private static boolean failed;

    private ExternalDropRenderState() {}

    static int cacheMode() {
        if (failed || itemPhysic && itemPhysicLite) return -1;
        if ((itemPhysic || itemPhysicLite) && updatePhysics == null) return -1;
        if (!itemDrops) return 0;
        if (dropSettings == null) return -1;

        // These options affect the extracted copy count and shadow radius.
        try {
            int mode = 0;
            for (int i = 0; i < dropSettings.length; i++) {
                if ((boolean) dropSettings[i].invokeExact()) mode |= 1 << i;
            }
            return mode;
        } catch (Throwable exception) {
            disableReuse(exception);
            return -1;
        }
    }

    static boolean refresh(ItemEntityRenderState state, ItemEntity entity) {
        if (failed) return false;
        if (updatePhysics == null) return true;
        try {
            updatePhysics.invokeExact(state, entity);
            return true;
        } catch (Throwable exception) {
            disableReuse(exception);
            return false;
        }
    }

    private static MethodHandle[] loadDropSettings() {
        try {
            var config = Class.forName("net.soundsofthesun.itemdrops.client.ItemDropsConfig", false,
                    ExternalDropRenderState.class.getClassLoader());
            var lookup = MethodHandles.publicLookup();
            return new MethodHandle[] {
                    lookup.findStaticGetter(config, "doDroppedItems", boolean.class),
                    lookup.findStaticGetter(config, "doDroppedBlocks", boolean.class),
                    lookup.findStaticGetter(config, "doBillboardItems", boolean.class)
            };
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private static MethodHandle loadPhysicsExtractor() {
        String type = itemPhysic
                ? "team.creative.itemphysic.client.ItemEntityRenderStateExtender"
                : "team.creative.itemphysiclite.ItemEntityRenderStateExtender";
        try {
            var extender = Class.forName(type, false, ExternalDropRenderState.class.getClassLoader());
            return MethodHandles.publicLookup().findVirtual(extender, "extractPhysic",
                    MethodType.methodType(void.class, ItemEntity.class))
                    .asType(MethodType.methodType(void.class, ItemEntityRenderState.class, ItemEntity.class));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private static void disableReuse(Throwable exception) {
        failed = true;
        LoggerFactory.getLogger("Carbon").warn("Could not refresh external dropped-item state; restoring extraction", exception);
    }
}
