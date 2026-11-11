package first.carbon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.compat.RenderCompat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;

// Preserve native entity transforms and batch the resulting model submissions.
public final class ItemBatchRenderer {
    private final List<GpuItemBatch> meshes = new ArrayList<>();
    private final IdentityHashMap<ItemStack, Entry> stacks = new IdentityHashMap<>();
    private final IdentityHashMap<MultiBufferSource.BufferSource, Map<RenderType, IdentityHashMap<GpuItemBatch, Submission>>> sources = new IdentityHashMap<>();
    final IdentityHashMap<TextureAtlasSprite, Boolean> opaqueSprites = new IdentityHashMap<>();
    private final LegacyState state = new LegacyState();
    private int frame, program;
    private boolean failed, renderingWorld;
    private MultiBufferSource.BufferSource pickupSource;
    boolean capturing;
    public int instances, drawCalls, cacheMisses, nativeFallbacks;
    public long uploadedBytes, modelBytes;
    private long cachedMeshBytes, instanceBytes;
    private Method spriteMarker;
    private boolean spriteApiChecked;

    public void beginFrame() {
        renderingWorld = false;
        frame++;
        instances = drawCalls = cacheMisses = nativeFallbacks = 0;
        uploadedBytes = modelBytes = 0;
        if ((frame & 63) == 0) {
            long allocated = 0;
            for (GpuItemBatch mesh : meshes) allocated += mesh.meshBytes;
            Iterator<GpuItemBatch> iterator = meshes.iterator();
            while (iterator.hasNext()) {
                GpuItemBatch mesh = iterator.next();
                if (frame - mesh.lastFrame > 300 || (frame - mesh.lastFrame > 1 && (allocated > 67108864 || meshes.size() > 256))) {
                    allocated -= mesh.meshBytes; cachedMeshBytes -= mesh.meshBytes; mesh.close(); iterator.remove();
                }
            }
            Iterator<Entry> entries = stacks.values().iterator();
            while (entries.hasNext()) {
                Entry entry = entries.next();
                if (entry.mesh != null && entry.mesh.closed || frame - entry.lastFrame > 300) entries.remove();
            }
            Iterator<Map<RenderType, IdentityHashMap<GpuItemBatch, Submission>>> owners = sources.values().iterator();
            while (owners.hasNext()) {
                Map<RenderType, IdentityHashMap<GpuItemBatch, Submission>> owner = owners.next();
                Iterator<IdentityHashMap<GpuItemBatch, Submission>> layers = owner.values().iterator();
                while (layers.hasNext()) {
                    Iterator<Submission> draws = layers.next().values().iterator();
                    while (draws.hasNext()) {
                        Submission draw = draws.next();
                        if (draw.mesh.closed || frame - draw.lastFrame > 2) { draw.close(); draws.remove(); }
                    }
                }
                layers = owner.values().iterator();
                while (layers.hasNext()) if (layers.next().isEmpty()) layers.remove();
                if (owner.isEmpty()) owners.remove();
            }
        }
    }

    public void beginWorld() {
        renderingWorld = true;
    }

    public void endWorld() {
        renderingWorld = false;
    }

    public MultiBufferSource.BufferSource pickupBuffer(RenderBuffers buffers) {
        if (!renderingWorld || !Carbon.CONFIG.enabled.get() || !Carbon.Client.IRIS.defersEntities()) {
            return buffers.bufferSource();
        }
        // Pickup particles flush their source even when Iris still has the world batches queued.
        if (pickupSource == null) pickupSource = MultiBufferSource.immediate(new BufferBuilder(256));
        return pickupSource;
    }

    public boolean setCapturing(boolean value) {
        boolean previous = capturing;
        capturing = value;
        return previous;
    }

    public boolean submit(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose, VertexConsumer vertices) {
        if (!renderingWorld || capturing || failed || !Carbon.CONFIG.enabled.get() || !Carbon.Client.IRIS.canInstance()
                || stack.isEmpty() || model.getClass() != SimpleBakedModel.class || !GL.getCapabilities().OpenGL33
                || glGetInteger(GL_CURRENT_PROGRAM) != 0) return false;
        Target first = vertices instanceof Target ? (Target) vertices : null;
        Target second = null;
        if (vertices instanceof VertexPair) {
            VertexPair pair = (VertexPair) vertices;
            first = pair.imo$first() instanceof Target ? (Target) pair.imo$first() : null;
            second = pair.imo$second() instanceof Target ? (Target) pair.imo$second() : null;
            if (second == null) return false;
        }
        if (first == null || first.imo$source() == null || !supported(first.imo$type())
                || second != null && (first.imo$source() != second.imo$source() || !supported(second.imo$type()))) return false;
        if (stacks.size() >= 16384 && !stacks.containsKey(stack)) {
            Iterator<Entry> old = stacks.values().iterator();
            while (old.hasNext()) if (frame - old.next().lastFrame > 1) old.remove();
            if (stacks.size() >= 16384) return false;
        }
        if (program == 0) {
            try {
                program = createProgram("legacy_instanced.vsh");
                LogManager.getLogger("Carbon").info("Legacy GPU model batching ready: {}", glGetString(GL_RENDERER));
            }
            catch (Exception exception) { failed = true; LogManager.getLogger("Carbon").warn("Legacy model batching unavailable; retaining native rendering", exception); return false; }
        }
        Entry entry = stacks.get(stack);
        if (entry == null || entry.model != model || !same(entry.stack, stack) || entry.mesh != null && entry.mesh.closed) {
            entry = new Entry(model, stack.copy(), mesh(model, stack)); stacks.put(stack, entry);
        }
        entry.lastFrame = frame;
        if (entry.mesh == null) return false;
        Submission a = submission(first, entry.mesh);
        Submission b = second == null ? null : submission(second, entry.mesh);
        if (a == b || instanceBytes + a.growth() + (b == null ? 0 : b.growth()) > 67108864) return false;
        a.add(pose.last(), light, overlay, frame);
        if (b != null) b.add(pose.last(), light, overlay, frame);
        touchMesh(entry.mesh);
        instances++;
        return true;
    }

    private boolean same(ItemStack first, ItemStack second) {
        return first.getItem() == second.getItem() && ItemStack.tagMatches(first, second);
    }

    void touchMesh(GpuItemBatch mesh) {
        mesh.lastFrame = frame;
    }

    GpuItemBatch mesh(BakedModel model, ItemStack stack) {
        for (GpuItemBatch candidate : meshes) if (candidate.model == model && same(candidate.stack, stack)) return candidate;
        if (meshes.size() >= 1024 || cachedMeshBytes >= 67108864) return null;
        try {
            GpuItemBatch mesh = new GpuItemBatch(stack, model, this);
            if (cachedMeshBytes + mesh.meshBytes > 67108864) { mesh.close(); return null; }
            meshes.add(mesh);
            cachedMeshBytes += mesh.meshBytes;
            modelBytes += mesh.meshBytes;
            cacheMisses++;
            return mesh;
        } catch (Exception exception) {
            LogManager.getLogger("Carbon").debug("Retaining native model {}: {}", stack.getItem(), exception.toString());
            return null;
        }
    }

    private Submission submission(Target target, GpuItemBatch mesh) {
        Map<RenderType, IdentityHashMap<GpuItemBatch, Submission>> owner = sources.get(target.imo$source());
        // Iris uses equality to group distinct wrapper objects for the same layer.
        if (owner == null) { owner = new HashMap<>(); sources.put(target.imo$source(), owner); }
        IdentityHashMap<GpuItemBatch, Submission> layer = owner.get(target.imo$type());
        if (layer == null) { layer = new IdentityHashMap<>(); owner.put(target.imo$type(), layer); }
        Submission draw = layer.get(mesh);
        if (draw == null) { draw = new Submission(mesh); layer.put(mesh, draw); }
        return draw;
    }

    private boolean supported(RenderType type) {
        type = Carbon.Client.IRIS.unwrap(type);
        return type == RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS) || type == RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS)
                || type == RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS) || type == RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)
                || type == RenderType.itemEntityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)
                || isGlint(type);
    }

    private boolean isGlint(RenderType type) {
        return type == RenderType.glint() || type == RenderType.glintDirect() || type == RenderType.glintTranslucent()
                || type == RenderType.entityGlint() || type == RenderType.entityGlintDirect();
    }

    public void flush(MultiBufferSource.BufferSource owner, RenderType type) {
        flush(owner, type, false);
    }

    public void flushPreparedLayer(MultiBufferSource.BufferSource owner, RenderType type) {
        flush(owner, type, true);
    }

    private void flush(MultiBufferSource.BufferSource owner, RenderType type, boolean prepared) {
        Carbon.Client.DROPPED_RENDERER.flush(owner, type, prepared);
        Map<RenderType, IdentityHashMap<GpuItemBatch, Submission>> layers = sources.get(owner);
        IdentityHashMap<GpuItemBatch, Submission> draws = layers == null ? null : layers.get(type);
        if (draws == null) return;
        boolean pending = false;
        for (Submission draw : draws.values()) pending |= draw.count != 0;
        if (!pending) return;
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING), previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING), previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        if (!prepared) type.setupRenderState();
        try {
            if (glGetInteger(GL_CURRENT_PROGRAM) != 0 || Carbon.Client.IRIS.active()) {
                replay(owner, type, draws, prepared);
            } else {
                state.apply(program, isGlint(Carbon.Client.IRIS.unwrap(type)));
                for (Submission draw : draws.values()) if (draw.count > 0) {
                    markSprites(draw.mesh.sprites);
                    glBindVertexArray(draw.mesh.vao);
                    if (draw.buffer == 0) draw.buffer = glGenBuffers();
                    glBindBuffer(GL_ARRAY_BUFFER, draw.buffer);
                    glBufferData(GL_ARRAY_BUFFER, (long) draw.capacity * 128, GL_STREAM_DRAW);
                    glBufferSubData(GL_ARRAY_BUFFER, 0, RenderCompat.slice(draw.data, 0, draw.count * 128));
                    for (int column = 0; column < 4; column++) glVertexAttribPointer(6 + column, 4, GL_FLOAT, false, 128, column * 16L);
                    for (int column = 0; column < 3; column++) glVertexAttribPointer(10 + column, 3, GL_FLOAT, false, 128, 64 + column * 12L);
                    glVertexAttribIPointer(13, 2, GL_UNSIGNED_SHORT, 128, 100);
                    glVertexAttribIPointer(14, 2, GL_UNSIGNED_SHORT, 128, 104);
                    glDrawElementsInstanced(GL_TRIANGLES, draw.mesh.indices, GL_UNSIGNED_INT, 0, draw.count);
                    uploadedBytes += (long) draw.count * 128;
                    drawCalls++; draw.count = 0;
                }
            }
        } finally {
            glUseProgram(previousProgram);
            if (!prepared) type.clearRenderState();
            glBindVertexArray(previousVao); glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    private void replay(MultiBufferSource.BufferSource owner, RenderType type, Map<GpuItemBatch, Submission> draws, boolean prepared) {
        // Iris has already collected its segments at this point. Draw any fallback immediately in its prepared layer.
        BufferBuilder immediate = prepared ? new BufferBuilder(256) : null;
        capturing = true;
        try {
            for (Submission draw : draws.values()) {
                for (int i = 0; i < draw.count; i++) {
                    int offset = i * 128;
                    PoseStack pose = new PoseStack();
                    RenderCompat.load(pose.last().pose(), RenderCompat.slice(draw.data, offset, 64).asFloatBuffer());
                    RenderCompat.load(pose.last().normal(), RenderCompat.slice(draw.data, offset + 64, 36).asFloatBuffer());
                    if (immediate != null) immediate.begin(type.mode(), type.format());
                    Minecraft.getInstance().getItemRenderer().renderModelLists(draw.mesh.model, draw.mesh.stack,
                            draw.data.getInt(offset + 100), draw.data.getInt(offset + 104), pose,
                            immediate == null ? owner.getBuffer(type) : immediate);
                    if (immediate != null) {
                        immediate.end();
                        BufferUploader.end(immediate);
                    }
                    nativeFallbacks++;
                }
                draw.count = 0;
            }
        } finally {
            capturing = false;
            if (immediate != null) RenderCompat.release(immediate);
        }
    }

    public void markSprites(List<TextureAtlasSprite> sprites) {
        if (!spriteApiChecked) {
            spriteApiChecked = true;
            try { spriteMarker = Class.forName("me.jellysquid.mods.sodium.client.render.texture.SpriteUtil").getMethod("markSpriteActive", TextureAtlasSprite.class); }
            catch (ReflectiveOperationException ignored) { }
        }
        if (spriteMarker != null) {
            try { for (TextureAtlasSprite sprite : sprites) spriteMarker.invoke(null, sprite); }
            catch (ReflectiveOperationException exception) { spriteMarker = null; }
        }
    }

    public void invalidate() {
        if (RenderSystem.isOnRenderThreadOrInit()) clear(); else RenderSystem.recordRenderCall(this::clear);
    }
    private void clear() {
        pickupSource = null;
        Carbon.Client.DROPPED_RENDERER.invalidate();
        for (Map<RenderType, IdentityHashMap<GpuItemBatch, Submission>> owner : sources.values())
            for (IdentityHashMap<GpuItemBatch, Submission> layer : owner.values()) for (Submission draw : layer.values()) draw.close();
        for (GpuItemBatch mesh : meshes) mesh.close();
        sources.clear(); meshes.clear(); stacks.clear(); opaqueSprites.clear();
        cachedMeshBytes = instanceBytes = 0;
        if (program != 0) glDeleteProgram(program);
        program = 0; failed = false;
    }
    static int createProgram(String vertexShader) throws Exception {
        int vertex = 0, fragment = 0, result = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, vertexShader); fragment = compile(GL_FRAGMENT_SHADER, "legacy_instanced.fsh");
            result = glCreateProgram(); glAttachShader(result, vertex); glAttachShader(result, fragment); glLinkProgram(result);
            if (glGetProgrami(result, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(result));
            return result;
        } catch (Exception exception) { if (result != 0) glDeleteProgram(result); throw exception; }
        finally { if (vertex != 0) glDeleteShader(vertex); if (fragment != 0) glDeleteShader(fragment); }
    }
    private static int compile(int type, String name) throws Exception {
        int shader = glCreateShader(type);
        try (InputStream stream = ItemBatchRenderer.class.getResourceAsStream("/assets/carbon/shaders/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing shader " + name);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] block = new byte[4096]; int size;
            while ((size = stream.read(block)) >= 0) bytes.write(block, 0, size);
            glShaderSource(shader, new String(bytes.toByteArray(), StandardCharsets.UTF_8)); glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) throw new IllegalStateException(glGetShaderInfoLog(shader));
        } catch (Exception exception) { glDeleteShader(shader); throw exception; }
        return shader;
    }
    public String statistics() { return String.format(Locale.ROOT, "%d model submissions, %d instanced draws, %d native fallbacks, %d new meshes, %d instance bytes/frame", instances, drawCalls, nativeFallbacks, cacheMisses, uploadedBytes); }
    public interface Target { MultiBufferSource.BufferSource imo$source(); RenderType imo$type(); void imo$target(MultiBufferSource.BufferSource source, RenderType type); }
    public interface VertexPair { VertexConsumer imo$first(); VertexConsumer imo$second(); }
    private static final class Entry {
        final BakedModel model; final ItemStack stack; final GpuItemBatch mesh; int lastFrame;
        Entry(BakedModel model, ItemStack stack, GpuItemBatch mesh) { this.model = model; this.stack = stack; this.mesh = mesh; }
    }
    private final class Submission {
        final GpuItemBatch mesh; ByteBuffer data; int capacity, buffer, count, lastFrame;
        Submission(GpuItemBatch mesh) { this.mesh = mesh; }
        long growth() { return count == capacity ? (long) (Math.max(64, capacity * 2) - capacity) * 128 : 0; }
        void add(PoseStack.Pose pose, int light, int overlay, int frame) {
            if (count == capacity) { instanceBytes += growth(); capacity = Math.max(64, capacity * 2); data = data == null ? MemoryUtil.memAlloc(capacity * 128) : MemoryUtil.memRealloc(data, capacity * 128); }
            int offset = count++ * 128;
            pose.pose().store(RenderCompat.slice(data, offset, 64).asFloatBuffer());
            RenderCompat.store(pose.normal(), RenderCompat.slice(data, offset + 64, 36).asFloatBuffer());
            data.putInt(offset + 100, light); data.putInt(offset + 104, overlay); lastFrame = frame;
        }
        void close() { if (buffer != 0) glDeleteBuffers(buffer); if (data != null) MemoryUtil.memFree(data); instanceBytes -= (long) capacity * 128; buffer = capacity = 0; data = null; }
    }
}
