package first.carbon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer.ComponentVersion;
import first.carbon.compat.RenderCompat;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import first.carbon.compat.ItemGeometry;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL33.*;

public final class DroppedItemBatchRenderer {
    private final Logger logger = LoggerFactory.getLogger("Carbon");
    private final Int2ObjectOpenHashMap<List<GpuDroppedItemBatch>> buckets = new Int2ObjectOpenHashMap<>();
    private final Set<ItemGeometry> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
    final IdentityHashMap<TextureAtlasSprite, Boolean> opaqueSprites = new IdentityHashMap<>();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f sceneView = new Matrix4f();
    private final Matrix4f sceneProjection = new Matrix4f();
    private final BlockPos.MutableBlockPos shadowPosition = new BlockPos.MutableBlockPos();
    private final Long2IntOpenHashMap lightCells = new Long2IntOpenHashMap();
    private final List<Entity> remainingEntities = new ArrayList<>();
    private final List<Entity> remainingView = Collections.unmodifiableList(remainingEntities);
    private final List<ItemEntity> updateQueue = new ArrayList<>();
    private final first.carbon.compat.EntityCullingCompatibility entityCulling = new first.carbon.compat.EntityCullingCompatibility();
    private final first.carbon.compat.ItemEntityCompatibility entityTransforms = new first.carbon.compat.ItemEntityCompatibility();
    private List<ItemEntity> sceneItems = new ArrayList<>(), updateSceneItems = new ArrayList<>();
    private List<ItemEntity> nbtItems = new ArrayList<>(), updateNbtItems = new ArrayList<>();
    private int nbtTick = Integer.MIN_VALUE;
    private Set<ItemEntity> remainingItems = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<ItemEntity> updateRemainingItems = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean updating, updateStationary, updateTickStable;
    private int updateIndex, updateInstances, updateCollected;
    private double updateDistanceMargin;
    private float partialTick;
    public boolean collecting;
    public int collected;
    public int visibleCollected;
    public volatile boolean sceneDirty = true;
    public volatile boolean blockCacheDirty = true;
    private boolean sceneValid;
    private boolean sceneStationary;
    private boolean sceneReused;
    private boolean sceneTickStable;
    private long sceneTick;
    private long sceneFoilTick;
    private int sceneSkyDarken;
    private int sceneEntityCount;
    private int sceneInstances;
    private int sceneCollected;
    private Vec3 sceneCamera = Vec3.ZERO;
    private Entity sceneCameraEntity;
    private double sceneDistanceMargin, sceneViewScale, sceneEntityDistance;
    private int sceneRenderDistance;
    private long sceneBuilds;
    private long sceneReuses;
    private boolean sceneShadows;
    private boolean sceneSkipVisibilityChecks;
    private ClientLevel level;
    private Vec3 camera = Vec3.ZERO;
    private double originX;
    private double originY;
    private double originZ;
    private float clock;
    private int ticks;
    private long foilTick;
    private int program;
    private int entityProgram;
    private int preparedProgram;
    private GpuDroppedInstanceProcessor processor;
    private GpuShadowBatch shadows;
    private final NativeItemShadows nativeItemShadows = new NativeItemShadows();
    private final boolean nativeSubmission = StaticItemRenderStates.requiresNativeSubmission();
    private boolean retainedSubmission;
    private int directSubmissions;
    private int generation;
    private int frame;
    private boolean active;
    private boolean failed;
    private boolean enabled;
    private int irisGeneration = -1;
    boolean physics;
    boolean capturing;
    public long uploadedBytes;
    public long modelBytes;
    public int instances;
    public int drawCalls;
    public int cacheMisses;
    public int shadowInstances;
    public int shadowDrawCalls;
    public int transformDispatches;
    public long cacheUpdateNs;
    public int pendingUpdates, updatedEntities;

    public void beginItemExtraction(Vec3 position) {
        StaticItemRenderStates.begin(position, generation, !failed && Carbon.CONFIG.enabled.get()
                && Carbon.CONFIG.optimizeDroppedItems.get() && Carbon.CONFIG.skipVisibilityChecks.get()
                && !Carbon.CONFIG.physicalDroppedItems.get() && Carbon.Client.IRIS.canInstance()
                && !Carbon.Client.IRIS.active() && !entityTransforms.requiresEntityTransforms());
    }

    public void beginFrame(Camera camera, Matrix4f view, Matrix4f projection, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (irisGeneration != Carbon.Client.IRIS.generation) {
            clear();
            irisGeneration = Carbon.Client.IRIS.generation;
        }
        boolean enabled = Carbon.Client.IRIS.canInstance() && Carbon.CONFIG.enabled.get() && Carbon.CONFIG.optimizeDroppedItems.get()
                && !entityTransforms.requiresEntityTransforms();
        boolean physics = enabled && Carbon.CONFIG.physicalDroppedItems.get();
        if (this.enabled != enabled || this.physics != physics) {
            clear();
            this.enabled = enabled;
            this.physics = physics;
        }
        this.camera = camera.position();
        if (level != minecraft.level || Math.abs(this.camera.x - originX) > 1024
                || Math.abs(this.camera.y - originY) > 1024 || Math.abs(this.camera.z - originZ) > 1024) {
            clear();
            level = minecraft.level;
            originX = Math.floor(this.camera.x / 256) * 256;
            originY = Math.floor(this.camera.y / 256) * 256;
            originZ = Math.floor(this.camera.z / 256) * 256;
        }
        this.view.set(view);
        this.projection.set(projection);
        this.partialTick = partialTick;
        ticks = level == null ? 0 : (int) (level.getGameTime() % 24000);
        foilTick = first.carbon.compat.RenderCompat.guiTick();
        clock = ticks + partialTick;
        frame++;
        uploadedBytes = 0;
        modelBytes = 0;
        instances = 0;
        collected = 0;
        visibleCollected = 0;
        drawCalls = 0;
        cacheMisses = 0;
        transformDispatches = 0;
        shadowDrawCalls = 0;
        cacheUpdateNs = 0;
        updatedEntities = 0;
        active = false;
        if (!failed && enabled && GL.getCapabilities().OpenGL33) {
            if (program == 0) {
                int fragment = 0;
                int shadowFragment = 0;
                try {
                    fragment = compile(GL_FRAGMENT_SHADER, "dropped_instanced.fsh");
                    program = createProgram("dropped_instanced.vsh", fragment);
                    entityProgram = createProgram(physics ? "physics_instanced.vsh" : "entity_instanced.vsh", fragment);
                    shadowFragment = compile(GL_FRAGMENT_SHADER, "shadow.fsh");
                    shadows = new GpuShadowBatch(createProgram("dropped_shadow.vsh", shadowFragment));
                    if (GL.getCapabilities().OpenGL43) {
                        try {
                            preparedProgram = createProgram(physics ? "physics_prepared.vsh" : "entity_prepared.vsh", fragment);
                            processor = new GpuDroppedInstanceProcessor(physics);
                        } catch (Exception exception) {
                            if (preparedProgram != 0) glDeleteProgram(preparedProgram);
                            preparedProgram = 0;
                            logger.warn("Compute animation unavailable; using vertex animation", exception);
                        }
                    }
                    logger.info("GPU item instancing ready: {} / {}", glGetString(GL_RENDERER), glGetString(GL_VERSION));
                } catch (Exception exception) {
                    if (program != 0) {
                        glDeleteProgram(program);
                        program = 0;
                    }
                    if (entityProgram != 0) {
                        glDeleteProgram(entityProgram);
                        entityProgram = 0;
                    }
                    failed = true;
                    logger.error("Instancing unavailable; retaining vanilla rendering", exception);
                } finally {
                    if (fragment != 0) {
                        glDeleteShader(fragment);
                    }
                    if (shadowFragment != 0) glDeleteShader(shadowFragment);
                }
            }
            active = program != 0 && entityProgram != 0;
        }
        boolean skipVisibility = Carbon.CONFIG.skipVisibilityChecks.get();
        nativeItemShadows.begin();
        directSubmissions = 0;
        retainedSubmission = active && skipVisibility && !nativeSubmission && !physics
                && !Carbon.Client.IRIS.active() && !Carbon.Client.IRIS.shadowPass();
        boolean dedicated = !Carbon.Client.IRIS.shadowPass();
        if (!dedicated) {
            updating = false;
            updateQueue.clear();
            pendingUpdates = 0;
            sceneValid = false;
            sceneDirty = true;
        }
        if (updating && (!this.camera.equals(sceneCamera) || !this.view.equals(sceneView) || !projection.equals(sceneProjection))) {
            sceneDirty = true;
        }
        sceneReused = active && dedicated && !updating
                && (minecraft.options.entityShadows().get() && ((ShadowSettings) minecraft.getEntityRenderDispatcher()).imo$shadows()) == sceneShadows
                && skipVisibility == sceneSkipVisibilityChecks
                && (skipVisibility || this.camera.equals(sceneCamera) && this.view.equals(sceneView) && projection.equals(sceneProjection))
                && sceneValid && sceneStationary && !sceneDirty && !blockCacheDirty && level.getSkyDarken() == sceneSkyDarken
                && minecraft.getCameraEntity() == sceneCameraEntity
                && (sceneTickStable || level.getGameTime() == sceneTick && foilTick == sceneFoilTick)
                && level.getGameTime() / 24000 == sceneTick / 24000
                && level.getEntityCount() == sceneEntityCount && Entity.getViewScale() == sceneViewScale
                && minecraft.options.renderDistance().get() == sceneRenderDistance
                && minecraft.options.entityDistanceScaling().get() == sceneEntityDistance
                && camera.position().distanceToSqr(sceneCamera) < sceneDistanceMargin * sceneDistanceMargin
                && !level.getChunkSource().getLightEngine().hasLightWork();
        if (sceneReused && !nbtItems.isEmpty() && nbtTick != first.carbon.compat.RenderCompat.guiTick()) {
            nbtTick = first.carbon.compat.RenderCompat.guiTick();
            for (ItemEntity item : nbtItems) {
                EntityCache cached = ((EntityCacheAccess) item).imo$cache();
                if (cached.version != ((ComponentVersion) (Object) cached.stack).imo$version()) {
                    sceneReused = false;
                    break;
                }
            }
        }
        boolean retained = active && dedicated && sceneValid;
        if (retained) {
            instances = sceneInstances;
            collected = sceneCollected;
        }
        if (sceneReused) sceneReuses++;
        if (shadows != null) {
            if (!sceneValid) shadows.retainedCount = 0;
            shadows.count = retained ? shadows.retainedCount : 0;
            shadows.flushed = false;
        }
        if (blockCacheDirty) {
            lightCells.clear();
            lightCells.defaultReturnValue(-1);
            if (shadows != null) shadows.receivers.clear();
            blockCacheDirty = false;
        } else if (shadows != null && level != null && level.getSkyDarken() != sceneSkyDarken) {
            shadows.receivers.clear();
        }
        shadowInstances = shadows == null ? 0 : shadows.count;
        for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
            for (GpuDroppedItemBatch batch : bucket) {
                if (!sceneValid) batch.retainedCount = 0;
                batch.count = retained && batch.entityBatch ? batch.retainedCount : 0;
                if (!retained) batch.physicsMoving = false;
                if (batch.count > 0 || updating && batch.updateCount > 0) batch.lastFrame = frame;
                batch.flushed = false;
                batch.processed = false;
            }
        }
    }

    public boolean submitEntity(ItemEntity entity, ItemStackRenderState itemState, float partialTick,
                                int light, PoseStack pose, Object buffers) {
        boolean submitted = false;
        boolean shadow = Carbon.Client.IRIS.shadowPass();
        if (active && !capturing && entity.level() == level && level.getEntity(entity.getId()) == entity
                && !entity.isCurrentlyGlowing() && (!shadow || physics)) {
            if (entity.tickCount == 0) {
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
            }
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                return false;
            }
            var layer = RenderCompat.batchableLayer(itemState);
            if (layer == null) return false;
            int version = ((ComponentVersion) (Object) stack).imo$version();
            EntityCache cached = ((EntityCacheAccess) entity).imo$cache();

            if (cached.stack != stack || cached.version != version || cached.generation != generation
                    || (cached.batch != null && cached.batch.closed)) {
                cached.stack = stack;
                cached.version = version;
                cached.generation = generation;
                cached.batch = null;
                cached.motionTick = Long.MIN_VALUE;
                cached.roll = 0;
                cached.rate = 0;
            }
            GpuDroppedItemBatch batch = cached.batch;
            // Model choices and tints can change without an ItemStack mutation.
            if (batch == null || itemState.isAnimated() || cached.quads != layer.quads
                    || cached.itemTransform != layer.itemTransform || cached.extents != layer.extents
                    || !sameTints(batch.tintLayers, layer.tintLayers)) {
                batch = find(stack, RenderCompat.resolveItem(itemState, layer));
                cached.batch = batch;
                cached.quads = layer.quads;
                cached.itemTransform = layer.itemTransform;
                cached.extents = layer.extents;
            }
            if (batch != null && (shadow || !batch.flushed)) {
                ((ComponentVersion) (Object) stack).imo$track();
                int count = stack.getCount();
                int copies = count <= 1 ? 1 : count <= 16 ? 2 : count <= 32 ? 3 : count <= 48 ? 4 : 5;
                if (shadow || batch.count + copies <= 65536) {
                    boolean spread = true;
                    boolean bob = true;
                    float ageOffset = entity.tickCount + partialTick - clock;
                    if (physics && cached.motionTick != level.getGameTime()) {
                        if (cached.motionTick != Long.MIN_VALUE) {
                            cached.roll = (cached.roll + cached.rate * Math.min(20, level.getGameTime() - cached.motionTick)) % (float) (Math.PI * 2);
                        }
                        double dx = entity.getX() - entity.xOld;
                        double dy = entity.getY() - entity.yOld;
                        double dz = entity.getZ() - entity.zOld;
                        double speed = dx * dx + dy * dy + dz * dz;
                        cached.rate = !entity.onGround() && speed > 1.0E-8 ? (float) Math.min(0.8, Math.sqrt(speed) * 4) : 0;
                        if (entity.isInWater()) cached.rate *= 0.35f;
                        if (entity.isInLava()) cached.rate *= 0.15f;
                        if (entity.onGround() && !batch.gui3d) cached.roll = 0;
                        cached.motionTick = level.getGameTime();
                    }
                    if (shadow) {
                        var rotation = new org.joml.Quaternionf().rotationY(-entity.getYRot() * Mth.DEG_TO_RAD);
                        if (batch.flat) rotation.rotateX((float) (Math.PI / 2));
                        rotation.rotateY(cached.roll + cached.rate * (clock - (float) Math.floor(clock)));
                        var matrix = new org.joml.Matrix3f().rotation(rotation);
                        float height = Math.abs(matrix.m01()) * batch.halfSize.x + Math.abs(matrix.m11()) * batch.halfSize.y + Math.abs(matrix.m21()) * batch.halfSize.z;
                        var center = rotation.transform(batch.center, new org.joml.Vector3f());
                        var offset = new org.joml.Vector3f();
                        capturing = true;
                        try {
                            for (int copy = 0; copy < copies; copy++) {
                                int index = copy * 3;
                                offset.set(spread ? batch.copyOffsets[index] : 0, spread ? batch.copyOffsets[index + 1] : 0, batch.gui3d && spread ? batch.copyOffsets[index + 2] : 0);
                                rotation.transform(offset);
                                offset.y = height + (batch.gui3d ? Math.abs(offset.y) : copy * (height * 2 + 0.002f)) + 0.002f;
                                offset.sub(center);
                                pose.pushPose();
                                pose.translate(offset.x, offset.y + ((entity.getId() * 0x9E3779B9) >>> 16) * (0.003f / 65535), offset.z);
                                pose.rotate(rotation);
                                first.carbon.compat.RenderCompat.renderItem(batch.model,batch.tintLayers,batch.baseType,pose,buffers,light,OverlayTexture.NO_OVERLAY,batch.foil);
                                pose.popPose();
                            }
                        } finally { capturing = false; }
                    } else {
                        batch.addEntity((float) (Mth.lerp(partialTick, entity.xOld, entity.getX()) - originX),
                                (float) (Mth.lerp(partialTick, entity.yOld, entity.getY()) - originY)
                                        + (physics ? ((entity.getId() * 0x9E3779B9) >>> 16) * (0.003f / 65535) : 0),
                                (float) (Mth.lerp(partialTick, entity.zOld, entity.getZ()) - originZ),
                                physics ? cached.roll : ageOffset, physics ? -entity.getYRot() * Mth.DEG_TO_RAD : entity.bobOffs, light, bob, spread, copies,
                                cached.rate);
                        instances += copies;
                    }
                    batch.lastFrame = frame;
                    submitted = true;
                }
            }
        }
        return submitted;
    }

    public boolean submitRetained(net.minecraft.client.renderer.entity.state.ItemEntityRenderState state, PoseStack pose) {
        if (!active || !retainedSubmission || state.nameTag != null || state.displayFireAnimation
                || state.isInvisible || state.appearsGlowing()) return false;
        var access = (first.carbon.compat.ItemStateAccess) state;
        ItemEntity item = access.carbon$entity();
        if (item == null) return false;
        var cached = ((EntityCacheAccess) item).imo$cache();
        var entry = cached.staticState;
        if (entry == null || entry.state() != state) return false;
        boolean drawShadow = !state.shadowPieces.isEmpty();
        if (drawShadow && (shadows == null || shadows.flushed || !shadows.vanillaTexture
                || !nativeItemShadows.canAdd(state)
                || (pose.last().pose().properties() & org.joml.Matrix4fc.PROPERTY_IDENTITY) == 0)) return false;
        GpuDroppedItemBatch batch = cached.batch;
        int copies = net.minecraft.client.renderer.entity.state.ItemClusterRenderState.getRenderedAmount(entry.count());
        if (batch != null && batch == cached.directBatch && cached.directModel == state.item
                && cached.generation == generation && !batch.closed && !batch.flushed && batch.count + copies <= 65536) {
            float ageOffset = item.tickCount + access.carbon$partial() - clock;
            batch.addEntity((float) (state.x - originX), (float) (state.y - originY), (float) (state.z - originZ),
                    ageOffset, item.bobOffs, state.lightCoords, true, true, copies, cached.rate);
            batch.lastFrame = frame;
            instances += copies;
        } else {
            if (!submitEntity(item, state.item, access.carbon$partial(), state.lightCoords, pose, null)) return false;
            cached.directModel = state.item;
            cached.directBatch = cached.batch;
        }
        if (drawShadow) nativeItemShadows.add(state);
        directSubmissions++;
        return true;
    }

    public void addShadow(ItemEntity entity, float partialTick, boolean enabled) {
        if (enabled && !Carbon.Client.IRIS.active() && shadows != null && !shadows.flushed) {
            shadows.add(entity, partialTick, 0, originX, originY, originZ);
            shadowInstances = shadows.count;
        }
    }

    public Iterable<Entity> collect(Iterable<Entity> entities) {return entities;}

    private static boolean sameTints(int[] cached, IntList current) {
        if (current == null) return cached.length == 0;
        if (cached.length != current.size()) return false;
        for (int i = 0; i < cached.length; i++) if (cached[i] != current.getInt(i)) return false;
        return true;
    }

    private GpuDroppedItemBatch find(ItemStack stack, RenderCompat.ResolvedItem resolved) {
        if (resolved == null || stack.is(Items.TRIDENT) || stack.is(Items.SPYGLASS)) return null;
        ItemGeometry model = resolved.model();
        if (rejected.contains(model)) return null;
        int hash = 31 * (31 * stack.getItem().hashCode() + stack.getComponentsPatch().hashCode())
                + java.util.Arrays.hashCode(resolved.tints());
        List<GpuDroppedItemBatch> bucket = buckets.get(hash);
        if (bucket != null) {
            for (GpuDroppedItemBatch candidate : bucket) {
                if (candidate.model == model && candidate.baseType == resolved.type()
                        && java.util.Arrays.equals(candidate.tintLayers, resolved.tints())
                        && ItemStack.isSameItemSameComponents(candidate.stack, stack)) return candidate;
            }
        }
        if (!RenderCompat.itemType(resolved.type())) {
            rejected.add(model);
            return null;
        }
        try {
            var batch = new GpuDroppedItemBatch(stack, resolved, this, true, false);
            buckets.computeIfAbsent(hash, ignored -> new ArrayList<>()).add(batch);
            cacheMisses++;
            modelBytes += batch.meshBytes;
            return batch;
        } catch (Exception exception) {
            rejected.add(model);
            logger.debug("Keeping vanilla renderer for {}: {}", stack.getItem(), exception.toString());
            return null;
        }
    }

    public void flush() {
        if (active && shadows != null && !shadows.flushed) {
            nativeItemShadows.prepare(shadows, camera);
            shadowInstances = shadows.count;
        }
        if (!active) return;
        int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        try (MemoryStack memory = MemoryStack.stackPush()) {
            FloatBuffer matrix = memory.mallocFloat(16);
            for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
                for (GpuDroppedItemBatch batch : bucket) {
                    if (batch.count > 0 && !batch.flushed) drawBatch(batch, matrix);
                }
            }
            if (shadows != null && shadows.count > 0 && !shadows.flushed) {
                uploadedBytes += shadows.draw(view, projection, camera, originX, originY, originZ);
                shadowDrawCalls++;
            }
        } finally {
            Carbon.Client.IRIS.restoreProgram(previousProgram);
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, buffer);
        }
    }

    private void drawBatch(GpuDroppedItemBatch batch, FloatBuffer matrix) {
        batch.processed = batch.entityBatch && processor != null;
        int drawProgram = batch.entityBatch ? batch.processed ? preparedProgram : entityProgram : program;
        uploadedBytes += batch.upload();
        if (batch.processed && processor.prepare(batch, clock)) transformDispatches++;
        Carbon.Client.RENDERER.markSprites(batch.sprites);
        for (GpuDroppedItemBatch.Mesh mesh : batch.meshes) {
            RenderCompat.setupType(mesh.type);
            try {
                bindBatchProgram(batch, drawProgram, matrix);
                mesh.bind();
                glDrawElementsInstanced(GL_TRIANGLES, mesh.indices, GL_UNSIGNED_INT, 0, batch.count);
                drawCalls++;
            } finally {
                RenderCompat.clearType(mesh.type);
            }
        }
        batch.flushed = true;
    }

    private void bindBatchProgram(GpuDroppedItemBatch batch, int drawProgram, FloatBuffer matrix) {
        glUseProgram(drawProgram);
        glUniform1i(glGetUniformLocation(drawProgram, "GlintPass"), 0);
        glUniformMatrix4fv(glGetUniformLocation(drawProgram, "TextureMat"), false,
                RenderCompat.floats(RenderCompat.textureMatrix(), matrix));
        glUniform1f(glGetUniformLocation(drawProgram, "GlintAlpha"), RenderCompat.glintAlpha());
        glUniformMatrix4fv(glGetUniformLocation(drawProgram, "View"), false, view.get(matrix));
        glUniformMatrix4fv(glGetUniformLocation(drawProgram, "Projection"), false, projection.get(matrix));
        glUniform3f(glGetUniformLocation(drawProgram, "Camera"), (float) (camera.x - originX),
                (float) (camera.y - originY), (float) (camera.z - originZ));
        glUniform1f(glGetUniformLocation(drawProgram, "Clock"), clock);
        glUniform1f(glGetUniformLocation(drawProgram, "GroundScaleY"), batch.groundScaleY);
        if (batch.physics) {
            glUniform3f(glGetUniformLocation(drawProgram, "Center"), batch.center.x, batch.center.y, batch.center.z);
            glUniform3f(glGetUniformLocation(drawProgram, "HalfSize"), batch.halfSize.x, batch.halfSize.y, batch.halfSize.z);
            glUniform1i(glGetUniformLocation(drawProgram, "Flat"), batch.flat ? 1 : 0);
        }
        for (int sampler = 0; sampler < 3; sampler++) {
            glUniform1i(glGetUniformLocation(drawProgram, "Sampler" + sampler), sampler);
        }
        float[] fog = RenderCompat.fogColor();
        glUniform4f(glGetUniformLocation(drawProgram, "FogColor"), fog[0], fog[1], fog[2], fog[3]);
        float[] color = RenderCompat.color();
        glUniform4f(glGetUniformLocation(drawProgram, "ColorModulator"), color[0], color[1], color[2], color[3]);
        glUniform1f(glGetUniformLocation(drawProgram, "FogStart"), RenderCompat.fogStart());
        glUniform1f(glGetUniformLocation(drawProgram, "FogEnd"), RenderCompat.fogEnd());
        glUniform1i(glGetUniformLocation(drawProgram, "FogShape"), RenderCompat.fogShape());
        RenderCompat.bindShared(drawProgram);
    }

    public void endFrame() {
        active = false;
        long allocated = 0;
        int count = 0;
        for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
            for (GpuDroppedItemBatch batch : bucket) {
                allocated += batch.meshBytes + (batch.capacity + batch.updateCapacity) * (long) batch.instanceStride + batch.preparedCapacity * 40L;
                count++;
            }
        }
        Iterator<List<GpuDroppedItemBatch>> bucketsIterator = buckets.values().iterator();
        while (bucketsIterator.hasNext()) {
            List<GpuDroppedItemBatch> bucket = bucketsIterator.next();
            Iterator<GpuDroppedItemBatch> iterator = bucket.iterator();
            while (iterator.hasNext()) {
                GpuDroppedItemBatch batch = iterator.next();
                if (frame - batch.lastFrame > 300 || (batch.lastFrame != frame && (allocated > 67108864 || count > 256))) {
                    allocated -= batch.meshBytes + (batch.capacity + batch.updateCapacity) * (long) batch.instanceStride + batch.preparedCapacity * 40L;
                    count--;
                    batch.close();
                    iterator.remove();
                }
            }
            if (bucket.isEmpty()) {
                bucketsIterator.remove();
            }
        }
    }

    public String statistics() {
        return String.format(Locale.ROOT, "%d instances (%d collected), %d instanced draws, %d shadow instances/%d shadow draws, %d new meshes, %d instance bytes/frame, %d mesh bytes/frame, %d scene builds/%d reuses, %d transform dispatches, %d queued/%d updated, %.3f ms cache update, %d item state reuses, %d direct item submits, %d early state hits",
                instances, collected, drawCalls, shadowInstances, shadowDrawCalls, cacheMisses, uploadedBytes, modelBytes, sceneBuilds, sceneReuses, transformDispatches, pendingUpdates, updatedEntities, cacheUpdateNs / 1_000_000.0, StaticItemRenderStates.reused(), directSubmissions, StaticItemRenderStates.earlyReuses());
    }

    public void invalidate() {
        if (RenderSystem.isOnRenderThread()) {
            clear();
        } else {
            net.minecraft.client.Minecraft.getInstance().execute(this::clear);
        }
    }

    public boolean capturingModel() {
        return capturing;
    }

    private void clear() {
        nativeItemShadows.clear();
        first.carbon.compat.ItemGeometry.clear();
        active = false;
        level = null;
        generation++;
        for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
            for (GpuDroppedItemBatch batch : bucket) {
                batch.close();
            }
        }
        buckets.clear();
        rejected.clear();
        opaqueSprites.clear();
        lightCells.clear();
        remainingEntities.clear();
        updateQueue.clear();
        sceneItems.clear();
        updateSceneItems.clear();
        remainingItems.clear();
        updateRemainingItems.clear();
        nbtItems.clear();
        updateNbtItems.clear();
        updating = false;
        pendingUpdates = updateIndex = 0;
        sceneInstances = sceneCollected = 0;
        collecting = false;
        sceneValid = false;
        sceneCameraEntity = null;
        sceneDirty = true;
        blockCacheDirty = true;
        sceneBuilds = 0;
        sceneReuses = 0;
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
        if (entityProgram != 0) {
            glDeleteProgram(entityProgram);
            entityProgram = 0;
        }
        if (processor != null) {
            processor.close();
            processor = null;
        }
        if (shadows != null) {
            shadows.close();
            shadows = null;
        }
        if (preparedProgram != 0) {
            glDeleteProgram(preparedProgram);
            preparedProgram = 0;
        }
        failed = false;
    }

    private int compile(int type, String name) throws IOException {
        String source;
        try (InputStream stream = getClass().getResourceAsStream("/assets/carbon/shaders/" + name)) {
            if (stream == null) {
                throw new IOException("Missing shader " + name);
            }
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IOException(error);
        }
        return shader;
    }

    private int createProgram(String name, int fragment) throws IOException {
        int vertex = compile(GL_VERTEX_SHADER, name);
        int result = glCreateProgram();
        try {
            glAttachShader(result, vertex);
            glAttachShader(result, fragment);
            glLinkProgram(result);
            if (glGetProgrami(result, GL_LINK_STATUS) == GL_FALSE) {
                throw new IOException(glGetProgramInfoLog(result));
            }
        } catch (IOException exception) {
            glDeleteProgram(result);
            throw exception;
        } finally {
            glDeleteShader(vertex);
        }
        return result;
    }

    public interface ShadowSettings {
        boolean imo$shadows();
    }

    public interface EntityCacheAccess {
        EntityCache imo$cache();
        boolean imo$eligible();
        boolean imo$eligible(ItemStack stack, int version);
        boolean imo$hasFire();
        void imo$invalidate();
    }

    public static final class EntityCache {
        StaticItemRenderStates.Entry staticState;
        private ItemStackRenderState directModel;
        private GpuDroppedItemBatch directBatch;
        private ItemStack stack;
        private GpuDroppedItemBatch batch;
        private int version;
        private int generation = -1;
        private ItemQuads quads;
        private ItemTransform itemTransform;
        private Supplier<org.joml.Vector3fc[]> extents;
        private long motionTick = Long.MIN_VALUE;
        private float roll, rate;
    }

}
