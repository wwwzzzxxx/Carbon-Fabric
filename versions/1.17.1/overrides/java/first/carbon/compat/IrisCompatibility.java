package first.carbon.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import first.carbon.client.ItemBatchRenderer;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL33.*;

public final class IrisCompatibility {
    private final boolean present = FabricLoader.getInstance().isModLoaded("iris");
    private final boolean supported = Iris1171MixinPlugin.supportsIris();
    private final Method currentPack = present ? resolveCurrentPack() : null;
    private boolean active = present, blocked = present;
    private final Map<Long, Object> programs = new HashMap<>();
    private final IdentityHashMap<MultiBufferSource.BufferSource, Object> layers = new IdentityHashMap<>();
    public int generation, draws;

    private static Method resolveCurrentPack() {
        try {
            return Class.forName("net.coderbot.iris.Iris", false, IrisCompatibility.class.getClassLoader())
                    .getMethod("getCurrentPack");
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    public void beginFrame() {
        draws = 0;
        if (!present) return;
        boolean wasAvailable = canInstance();
        active = blocked = true;
        try {
            if (currentPack != null && currentPack.invoke(null) instanceof Optional<?> pack) {
                active = pack.isPresent();
                blocked = !supported;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Keep Iris in control if its current pipeline cannot be identified.
        }
        if (wasAvailable != canInstance()) generation++;
    }
    public boolean active() { return active; }
    public boolean canInstance() { return !present || !active && !blocked; }
    public boolean shadowPass() { return false; }
    public boolean vanillaShadows() { return !active; }
    public void apply(ShaderInstance shader) { shader.apply(); }
    public void restoreProgram(int program) { glUseProgram(program); }
    public int[] captureItem() { return null; }
    public void restoreCapture(int[] previous) { }
    public long identifiers() { return 0; }
    public VertexConsumer getBuffer(MultiBufferSource source, RenderType type) { return source.getBuffer(type); }
    public RenderType unwrap(RenderType type) {
        while (type instanceof LayerAccess layer) type = layer.carbon$wrapped();
        return type;
    }
    public void marker(ItemBatchRenderer.Target target) { }
    public ShaderInstance shader(ShaderInstance selected, RenderType type, boolean dropped) { return selected; }
    public boolean usesShader(ShaderInstance shader) { return false; }
    public void draw(ShaderInstance shader, int mode, int instanceBuffer, int vertexBuffer, int indexBuffer,
                     VertexFormat format, int indices, int count, Vector3f camera, float clock, float scale,
                     Vector3f center, Vector3f halfSize, boolean flat) { throw new IllegalStateException("Native Iris path cannot submit instanced draws"); }
    public void releaseMesh(int buffer) { }
    public void invalidate() { generation++; }

    public interface LayerAccess {
        RenderType carbon$wrapped();
    }
}
