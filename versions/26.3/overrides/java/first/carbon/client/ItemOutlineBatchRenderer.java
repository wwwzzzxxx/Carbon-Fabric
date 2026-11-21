package first.carbon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import first.carbon.Carbon;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ItemOutlineBatchRenderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemOutlineBatchRenderer.class);
    private static final VertexFormat MESH_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .build();
    private static final VertexFormat INSTANCE_FORMAT = VertexFormat.builder(1)
            .addAttribute("PoseRow0", GpuFormat.RGBA32_FLOAT)
            .addAttribute("PoseRow1", GpuFormat.RGBA32_FLOAT)
            .addAttribute("PoseRow2", GpuFormat.RGBA32_FLOAT)
            .addAttribute("OutlineColor", GpuFormat.RGBA8_UNORM)
            .build();
    private static final int INSTANCE_BYTES = INSTANCE_FORMAT.getVertexSize();
    private static final RenderPipeline CULL = outlinePipeline(true);
    private static final RenderPipeline NO_CULL = outlinePipeline(false);

    private final Map<List<BakedQuad>, Mesh> meshes = new IdentityHashMap<>();
    private final Map<Integer, List<Batch>> groups = new HashMap<>();
    private final Map<RenderType, PreparedRenderType> renderTypes = new IdentityHashMap<>();
    private ByteBuffer instances;
    private MappableRingBuffer buffers;
    private GpuBuffer instanceBuffer;
    private RenderSystem.AutoStorageIndexBuffer indices;
    private int frame;
    private int maxIndices;
    private boolean logged;

    public void beginFrame() {
        groups.clear();
        renderTypes.clear();
        maxIndices = 0;
        if (instances != null) instances.clear();
        if ((++frame & 63) == 0) {
            meshes.values().removeIf(mesh -> {
                if (frame - mesh.lastUsed < 120) return false;
                mesh.vertices.close();
                return true;
            });
        }
    }

    public boolean prepare(int group, List<ItemFeatureRenderer.Submit> submits) {
        if (!Carbon.CONFIG.optimizeDroppedItems.get() || Carbon.Client.IRIS.active()
                || Boolean.getBoolean("carbon.test.disableItemOutlineBatching")
                || submits.size() < 8 || submits.size() > 65536) return false;

        int color = submits.getFirst().outlineColor();
        var byModel = new IdentityHashMap<List<BakedQuad>, List<PoseStack.Pose>>();
        for (var submit : submits) {
            // The mask has no depth attachment. Reordering different colors changes overlap.
            if (submit.outlineColor() != color || submit.displayContext() != ItemDisplayContext.GROUND
                    || (submit.pose().pose().properties() & Matrix4fc.PROPERTY_AFFINE) == 0) return false;
            byModel.computeIfAbsent(submit.quads(), quads -> new ArrayList<>()).add(submit.pose());
        }
        int allocated = instances == null ? 0 : instances.position() / INSTANCE_BYTES;
        if (byModel.size() * 2 > submits.size() || allocated + submits.size() > 65536) return false;
        ensureCapacity((allocated + submits.size()) * INSTANCE_BYTES);

        var batches = new ArrayList<Batch>(byModel.size());
        int start = instances.position();
        for (var entry : byModel.entrySet()) {
            Mesh mesh = mesh(entry.getKey());
            if (mesh == null) {
                instances.position(start);
                return false;
            }
            var pipeline = RenderSystem.getCompiledPipelineNullable(mesh.type.pipeline().isCull() ? CULL : NO_CULL);
            if (pipeline == null) {
                instances.position(start);
                return false;
            }

            mesh.lastUsed = frame;
            int offset = instances.position();
            for (PoseStack.Pose pose : entry.getValue()) putInstance(pose.pose(), color);
            PreparedRenderType type = renderTypes.computeIfAbsent(mesh.type, RenderType::prepare);
            batches.add(new Batch(mesh, pipeline, type, offset, entry.getValue().size()));
            maxIndices = Math.max(maxIndices, mesh.indexCount);
        }
        groups.put(group, batches);
        return true;
    }

    public void upload() {
        if (groups.isEmpty()) return;

        instances.flip();
        int bytes = instances.remaining();
        if (buffers == null || buffers.size() < bytes) {
            if (buffers != null) buffers.close();
            buffers = new MappableRingBuffer(() -> "Carbon outline instances",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, instances.capacity());
        }
        instanceBuffer = buffers.currentBuffer();
        try (var mapped = instanceBuffer.map(0, bytes, false, true)) {
            mapped.data().put(instances);
        }
        indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        indices.getBuffer(maxIndices);
    }

    public void draw(int group, RenderPass pass) {
        List<Batch> batches = groups.get(group);
        if (batches == null) return;

        for (Batch batch : batches) {
            pass.setPipeline(batch.pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", batch.type.dynamicTransforms());
            for (var texture : batch.type.textures()) {
                pass.setUniform(texture.name(), texture.textureView(), texture.sampler());
            }
            pass.setVertexBuffer(0, batch.mesh.vertices.slice());
            pass.setVertexBuffer(1, instanceBuffer.slice(batch.offset, (long) batch.count * INSTANCE_BYTES));
            pass.setIndexBuffer(indices.getBuffer(), indices.type());
            pass.drawIndexed(batch.mesh.indexCount, batch.count, 0, 0, 0);
        }
        if (!logged) {
            logged = true;
            LOGGER.info("Carbon drew {} instanced item outlines in {} draws",
                    batches.stream().mapToInt(Batch::count).sum(), batches.size());
        }
    }

    public void finishFrame() {
        if (instanceBuffer != null) {
            buffers.rotate();
            instanceBuffer = null;
        }
    }

    private void ensureCapacity(int bytes) {
        if (instances == null) instances = MemoryUtil.memAlloc(Math.max(4096, bytes));
        if (instances.capacity() < bytes) {
            instances = MemoryUtil.memRealloc(instances, Math.max(bytes, instances.capacity() * 2));
        }
    }

    private void putInstance(Matrix4fc pose, int color) {
        instances.putFloat(pose.m00()).putFloat(pose.m10()).putFloat(pose.m20()).putFloat(pose.m30());
        instances.putFloat(pose.m01()).putFloat(pose.m11()).putFloat(pose.m21()).putFloat(pose.m31());
        instances.putFloat(pose.m02()).putFloat(pose.m12()).putFloat(pose.m22()).putFloat(pose.m32());
        instances.put((byte) ARGB.red(color)).put((byte) ARGB.green(color))
                .put((byte) ARGB.blue(color)).put((byte) 255);
    }

    private Mesh mesh(List<BakedQuad> quads) {
        Mesh mesh = meshes.get(quads);
        if (mesh != null) return mesh;
        if (quads.isEmpty() || quads.size() > 1024 || meshes.size() >= 256) return null;

        RenderType type = quads.getFirst().materialInfo().itemRenderType().outline().orElse(null);
        if (type == null || type.pipeline() != RenderPipelines.OUTLINE_CULL && type.pipeline() != RenderPipelines.OUTLINE_NO_CULL) return null;
        for (BakedQuad quad : quads) {
            if (quad.materialInfo().itemRenderType().outline().orElse(null) != type) return null;
        }

        ByteBuffer vertices = MemoryUtil.memAlloc(quads.size() * 4 * MESH_FORMAT.getVertexSize());
        try {
            for (BakedQuad quad : quads) {
                for (int i = 0; i < 4; i++) {
                    var position = quad.position(i);
                    long uv = quad.packedUV(i);
                    vertices.putFloat(position.x()).putFloat(position.y()).putFloat(position.z());
                    vertices.putFloat(UVPair.unpackU(uv)).putFloat(UVPair.unpackV(uv));
                }
            }
            vertices.flip();
            var buffer = RenderSystem.getDevice().createBuffer(() -> "Carbon outline mesh", GpuBuffer.USAGE_VERTEX, vertices);
            Mesh result = new Mesh(type, buffer, quads.size() * 6, frame);
            meshes.put(quads, result);
            return result;
        } finally {
            MemoryUtil.memFree(vertices);
        }
    }

    private static RenderPipeline outlinePipeline(boolean cull) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("carbon", cull ? "pipeline/outline_cull" : "pipeline/outline"))
                .withVertexShader(Identifier.fromNamespaceAndPath("carbon", "core/item_outline"))
                .withFragmentShader("core/rendertype_outline")
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, MESH_FORMAT)
                .withVertexBinding(1, INSTANCE_FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withCull(cull)
                .build();
    }

    @Override
    public void close() {
        meshes.values().forEach(mesh -> mesh.vertices.close());
        meshes.clear();
        groups.clear();
        renderTypes.clear();
        if (buffers != null) {
            buffers.close();
            buffers = null;
        }
        if (instances != null) {
            MemoryUtil.memFree(instances);
            instances = null;
        }
        instanceBuffer = null;
    }

    private static final class Mesh {
        final RenderType type;
        final GpuBuffer vertices;
        final int indexCount;
        int lastUsed;

        Mesh(RenderType type, GpuBuffer vertices, int indexCount, int lastUsed) {
            this.type = type;
            this.vertices = vertices;
            this.indexCount = indexCount;
            this.lastUsed = lastUsed;
        }
    }

    private record Batch(Mesh mesh, CompiledRenderPipeline pipeline, PreparedRenderType type,
                         int offset, int count) {
    }
}
