package first.carbon.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Reuses the renderer's packed pose for settled, single-model drops. */
final class RetainedExternalDropPose {
    private static final Settings settings = loadSettings();

    private final byte[] data;
    private StaticItemRenderStates.Entry entry;
    private GpuDroppedItemBatch batch;
    private int style, light;
    private float pitch, yaw, bob;

    RetainedExternalDropPose(int stride) {
        data = new byte[stride];
    }

    void capture(GpuDroppedItemBatch batch, StaticItemRenderStates.Entry entry, ItemEntity item, int style, int light) {
        this.entry = entry;
        this.batch = batch;
        this.style = style;
        this.light = light;
        pitch = item.getXRot();
        yaw = item.getYRot();
        bob = entry.state().bobOffset;
        batch.copyLastInstance(data);
    }

    GpuDroppedItemBatch reuse(StaticItemRenderStates.Entry entry, ItemEntity item, int style) {
        if (style == 0 || this.entry != entry || this.style != style || light != entry.state().lightCoords
                || pitch != item.getXRot() || yaw != item.getYRot() || bob != entry.state().bobOffset
                || batch.closed || batch.flushed || batch.count >= 65536) return null;
        batch.addRetainedInstance(data);
        return batch;
    }

    static int frameStyle() {
        if (settings == null) return 0;
        try {
            if (settings.flat != null) {
                return (boolean) settings.flat.invokeExact()
                        ? Float.floatToIntBits((float) settings.scale.invokeExact()) + 1 : 0;
            }
            return settings.vanillaRendering == null || !(boolean) settings.vanillaRendering.invokeExact() ? 1 : 0;
        } catch (Throwable unavailable) {
            return 0;
        }
    }

    static int style(int frameStyle, StaticItemRenderStates.Entry entry) {
        if (frameStyle == 0 || entry.count() != 1) return 0;
        ItemEntityRenderState state = entry.state();
        if (settings.flat != null) {
            // Item Drops randomizes block scale and billboards follow the camera.
            return state.item.usesBlockLight() ? 0 : frameStyle;
        }
        if (state.ageInTicks < 1) return 0;
        try {
            if (settings.skipRendering != null && (boolean) settings.skipRendering.invokeExact(state)) return 0;
            return (boolean) settings.additionalOffset.invokeExact(state) ? 2 : 1;
        } catch (Throwable unavailable) {
            return 0;
        }
    }

    private static Settings loadSettings() {
        var loader = FabricLoader.getInstance();
        boolean drops = loader.isModLoaded("itemdrops");
        boolean full = loader.isModLoaded("itemphysic");
        boolean lite = loader.isModLoaded("itemphysiclite");
        if ((drops ? 1 : 0) + (full ? 1 : 0) + (lite ? 1 : 0) != 1 || loader.isModLoaded("entityculling")) return null;

        // Other releases keep the live capture path until their renderer inputs have been verified.
        String minecraft = loader.getModContainer("minecraft").map(container ->
                container.getMetadata().getVersion().getFriendlyString()).orElse("");
        Releases releases = switch (minecraft) {
            case "1.21.10" -> new Releases(null, "1.8.11", "1.6.10", false);
            case "1.21.11" -> new Releases("0.2.2", "1.8.14", "1.6.11", false);
            case "26.1.2" -> new Releases("0.2.2", "1.8.15", "1.6.11", false);
            case "26.2" -> new Releases("0.2.2", "1.8.15", "1.6.12", true);
            case "26.3" -> new Releases("0.2.2", "1.8.18", "1.6.13", true);
            default -> null;
        };
        if (releases == null) return null;
        String mod = drops ? "itemdrops" : full ? "itemphysic" : "itemphysiclite";
        String testedVersion = drops ? releases.drops : full ? releases.full : releases.lite;
        if (testedVersion == null || loader.getModContainer(mod).filter(container ->
                container.getMetadata().getVersion().getFriendlyString().equals(testedVersion)).isEmpty()) return null;

        var lookup = MethodHandles.publicLookup();
        try {
            if (drops) {
                var config = Class.forName("net.soundsofthesun.itemdrops.client.ItemDropsConfig", false,
                        RetainedExternalDropPose.class.getClassLoader());
                return new Settings(lookup.findStaticGetter(config, "doDroppedItems", boolean.class),
                        lookup.findStaticGetter(config, "droppedItemSize", float.class), null, null, null);
            }
            String extenderName = full ? "team.creative.itemphysic.client.ItemEntityRenderStateExtender"
                    : "team.creative.itemphysiclite.ItemEntityRenderStateExtender";
            var extender = Class.forName(extenderName, false, RetainedExternalDropPose.class.getClassLoader());
            var getter = MethodType.methodType(boolean.class);
            var stateGetter = MethodType.methodType(boolean.class, ItemEntityRenderState.class);
            MethodHandle vanilla = null;
            if (full) {
                var modClass = Class.forName("team.creative.itemphysic.ItemPhysic", false,
                        RetainedExternalDropPose.class.getClassLoader());
                var config = modClass.getField("CONFIG");
                var rendering = config.getType().getField("rendering");
                vanilla = MethodHandles.filterReturnValue(
                        MethodHandles.filterReturnValue(lookup.unreflectGetter(config), lookup.unreflectGetter(rendering)),
                        lookup.unreflectGetter(rendering.getType().getField("vanillaRendering")));
            }
            MethodHandle skip = full || releases.liteCanSkip
                    ? lookup.findVirtual(extender, "skipRendering", getter).asType(stateGetter) : null;
            return new Settings(null, null, vanilla, skip,
                    lookup.findVirtual(extender, "hasAdditionalOffset", getter).asType(stateGetter));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private record Releases(String drops, String full, String lite, boolean liteCanSkip) {}

    private record Settings(MethodHandle flat, MethodHandle scale, MethodHandle vanillaRendering,
                            MethodHandle skipRendering, MethodHandle additionalOffset) {}
}
