package first.carbon.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/** Only renderer releases whose ground pose inputs are understood can skip a render callback. */
public final class BufferedDropProvider {
    private static final String PROVIDER = findProvider();

    private BufferedDropProvider() {}

    public static String provider() { return PROVIDER; }

    public static boolean physicalStyle() {
        try {
            MethodHandle vanilla = Settings.VANILLA_RENDERING;
            return "itemphysiclite".equals(PROVIDER) || vanilla != null && !(boolean) vanilla.invokeExact();
        } catch (Throwable unavailable) {
            return false;
        }
    }

    private static String findProvider() {
        FabricLoader loader = FabricLoader.getInstance();
        boolean full = loader.isModLoaded("itemphysic");
        boolean lite = loader.isModLoaded("itemphysiclite");
        if (full == lite || loader.isModLoaded("itemdrops") || loader.isModLoaded("entityculling")) return "";
        String minecraft = release(loader, "minecraft");
        String mod = full ? "itemphysic" : "itemphysiclite";
        String tested;
        switch (minecraft) {
            case "1.16.5": tested = full ? "" : "1.4.0"; break;
            case "1.18.2": tested = full ? "" : "1.5.0"; break;
            case "1.19.4": tested = full ? "1.6.9" : "1.6.1"; break;
            case "1.20.1": tested = full ? "1.8.17" : "1.6.13"; break;
            case "1.20.2": tested = full ? "1.6.12" : "1.6.3"; break;
            case "1.20.4":
            case "1.20.6": tested = full ? "1.7.1" : "1.6.4"; break;
            case "1.21.1": tested = full ? "1.8.17" : "1.6.13"; break;
            case "1.21.4": tested = full ? "1.8.4" : "1.6.7"; break;
            case "1.21.5": tested = full ? "1.8.8" : "1.6.9"; break;
            case "1.21.8": tested = full ? "1.8.11" : "1.6.10"; break;
            default: return "";
        }
        return !tested.isEmpty() && tested.equals(release(loader, mod)) ? mod : "";
    }

    private static String release(FabricLoader loader, String mod) {
        return loader.getModContainer(mod).map(container ->
                container.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    private static final class Settings {
        private static final MethodHandle VANILLA_RENDERING = vanillaRendering();

        private static MethodHandle vanillaRendering() {
            if (!"itemphysic".equals(PROVIDER)) return null;
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> mod = Class.forName("team.creative.itemphysic.ItemPhysic", false, BufferedDropProvider.class.getClassLoader());
                Field config = mod.getField("CONFIG");
                Field rendering = config.getType().getField("rendering");
                return MethodHandles.filterReturnValue(
                        MethodHandles.filterReturnValue(lookup.unreflectGetter(config), lookup.unreflectGetter(rendering)),
                        lookup.unreflectGetter(rendering.getType().getField("vanillaRendering")));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
