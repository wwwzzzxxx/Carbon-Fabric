package first.carbon.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/** Lets the known legacy Sodium build reuse a selection while its visible section graph is unchanged. */
public final class SodiumVisibility {
    private final boolean supported = FabricLoader.getInstance().getModContainer("sodium")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .filter("0.2.0+build.4"::equals)
            .isPresent();
    private boolean inspected, valid, lastEnabled;
    private Method worldRenderer;
    private Field enabled, manager, culler, visibleFrame;
    private Object lastGraph;
    private int lastFrame;

    public boolean unchanged() {
        if (!supported) return false;
        if (!inspected) inspect();
        if (visibleFrame == null) return false;
        try {
            Object renderer = worldRenderer.invoke(null);
            boolean culling = enabled.getBoolean(renderer);
            Object graph = culling ? culler.get(manager.get(renderer)) : null;
            int frame = culling ? visibleFrame.getInt(graph) : 0;
            boolean same = valid && culling == lastEnabled && graph == lastGraph && frame == lastFrame;
            valid = true;
            lastEnabled = culling;
            lastGraph = graph;
            lastFrame = frame;
            return same;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            clear();
            return false;
        }
    }

    public void clear() {
        valid = false;
        lastGraph = null;
    }

    private void inspect() {
        inspected = true;
        try {
            Class<?> renderer = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            worldRenderer = renderer.getMethod("getInstance");
            enabled = field(renderer, "useEntityCulling");
            manager = field(renderer, "chunkRenderManager");
            culler = field(manager.getType(), "culler");
            Class<?> graph = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.cull.graph.ChunkGraphCuller");
            visibleFrame = field(graph, "activeFrame");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
