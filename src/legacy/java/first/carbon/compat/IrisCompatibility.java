package first.carbon.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderType;

public final class IrisCompatibility {
    private final boolean present = FabricLoader.getInstance().isModLoaded("iris");
    private Method pack;
    private Class<?> wrappedLayer;
    private Method unwrapLayer;
    private final IdentityHashMap<RenderType, RenderType> originalLayers = new IdentityHashMap<>();
    private boolean active;
    public IrisCompatibility() {
        if (present) {
            try { pack = Class.forName("net.coderbot.iris.Iris").getMethod("getCurrentPack"); }
            catch (ReflectiveOperationException ignored) { active = true; }
            try {
                Class<?> wrapper = Class.forName("net.coderbot.iris.layer.OuterWrappedRenderType", false,
                        IrisCompatibility.class.getClassLoader());
                unwrapLayer = wrapper.getMethod("unwrap");
                wrappedLayer = wrapper;
            } catch (ReflectiveOperationException ignored) { }
        }
    }
    public void beginFrame() {
        if (pack != null) {
            try { active = ((Optional<?>) pack.invoke(null)).isPresent(); }
            catch (ReflectiveOperationException exception) { active = true; }
        }
    }
    public boolean active() { return active; }
    public boolean canInstance() { return !active; }
    public boolean defersEntities() { return present && !active; }
    public RenderType unwrap(RenderType layer) {
        if (layer == null || layer.getClass() != wrappedLayer) return layer;
        RenderType original = originalLayers.get(layer);
        if (original != null) return original;
        try {
            original = (RenderType) unwrapLayer.invoke(layer);
            if (originalLayers.size() < 256) originalLayers.put(layer, original);
            return original;
        } catch (ReflectiveOperationException exception) {
            return layer;
        }
    }
    public void invalidate() {
        if (RenderSystem.isOnRenderThreadOrInit()) originalLayers.clear();
        else RenderSystem.recordRenderCall(originalLayers::clear);
    }
    public void releaseMesh(int buffer) { }
}
