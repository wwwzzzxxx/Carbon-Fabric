package first.carbon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import first.carbon.client.ItemBatchRenderer.ComponentVersion;
import first.carbon.client.ItemBatchRenderer.FoilState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.item.Item;
import net.minecraft.client.renderer.block.model.ItemTransforms.TransformType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

public final class DroppedItemBatchRenderer {
    private static final int RETAINED_LIMIT = 32768;
    private static final Set<String> RETAINED_VERSIONS = Set.of(
            "1.17.1", "1.18.2", "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.3");
    private static final Comparator<ItemEntity> SPATIAL_ORDER = Comparator
            .comparingInt((ItemEntity item) -> Mth.floor(item.getX() * 0.5))
            .thenComparingInt(item -> Mth.floor(item.getZ() * 0.5))
            .thenComparingDouble(ItemEntity::getX)
            .thenComparingDouble(ItemEntity::getZ);
    private final Logger logger = LoggerFactory.getLogger("Carbon");
    private final boolean retainedSupported = FabricLoader.getInstance().getModContainer("minecraft")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .filter(RETAINED_VERSIONS::contains).isPresent();
    private final List<ItemEntity> retainedCandidates = new ArrayList<>();
    private final List<RetainedItem> retainedItems = new ArrayList<>();
    private final IdentityHashMap<GpuDroppedItemBatch, VisibleRanges> retainedBatches = new IdentityHashMap<>();
    private BitSet retainedShadowSelection = new BitSet(), visibleShadowSelection = new BitSet();
    private int retainedShadowCount = -1;
    private AABB retainedBounds;
    private boolean retainedAllVisible, retainedSameView;
    private long retainedSelectionTick;
    private int retainedSelectionInstances;
    private final IdentityHashMap<BakedModel, Boolean> durabilityModels = new IdentityHashMap<>();
    private boolean retainedAllowed, retainedReady, retainedFrame;
    private long retainedCheckTick = Long.MIN_VALUE, retainedAttemptTick = Long.MIN_VALUE;
    private int retainedStartClock, retainedAgeDelta, retainedEntityCount, retainedSky;
    private int indirectBuffer;
    private IntBuffer indirectCommands;
    private final Int2ObjectOpenHashMap<List<GpuDroppedItemBatch>> buckets = new Int2ObjectOpenHashMap<>();
    private final IdentityHashMap<Class<?>, Boolean> dynamicFoil = new IdentityHashMap<>();
    private final Set<BakedModel> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
    final IdentityHashMap<TextureAtlasSprite, Boolean> opaqueSprites = new IdentityHashMap<>();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f sceneView = new Matrix4f();
    private final Matrix4f sceneProjection = new Matrix4f();
    private final Matrix4f transform = new Matrix4f();
    private final BlockPos.MutableBlockPos shadowPosition = new BlockPos.MutableBlockPos();
    private final Long2IntOpenHashMap lightCells = new Long2IntOpenHashMap();
    private final List<Entity> remainingEntities = new ArrayList<>();
    private final List<Entity> remainingView = Collections.unmodifiableList(remainingEntities);
    private final List<Entity> limitedRemaining = new ArrayList<>();
    private boolean remainingHasItems;
    private int itemDrawLimit = -1;
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
    private double sceneViewScale, sceneEntityDistance;
    private int sceneRenderDistance;
    private long sceneBuilds;
    private long sceneReuses;
    private long retainedFrames;
    private boolean sceneShadows;
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
    private final DirectShadowSelection directShadows = new DirectShadowSelection();
    private boolean cacheSelectedShadows;
    private int generation;
    private int frame;
    private boolean active;
    private boolean failed;
    private boolean enabled;
    private boolean nativeVisibility;
    private int irisGeneration = -1;
    private final org.joml.Vector3f irisCamera = new org.joml.Vector3f();
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

    public void beginFrame(Camera camera, Matrix4f view, Matrix4f projection, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (irisGeneration != Carbon.Client.IRIS.generation) {
            clear();
            irisGeneration = Carbon.Client.IRIS.generation;
        }
        boolean enabled = Carbon.Client.IRIS.canInstance() && Carbon.CONFIG.enabled.get() && Carbon.CONFIG.optimizeDroppedItems.get()
                && !entityTransforms.requiresEntityTransforms();
        boolean physics = enabled && Carbon.CONFIG.physicalDroppedItems.get();
        boolean nativeCulling = !Carbon.CONFIG.skipVisibilityChecks.get() || entityCulling.requiresNativeVisibility(minecraft);
        int drawLimit = enabled && !nativeCulling ? first.carbon.compat.TweakerooVisibility.itemDrawLimit() : -1;
        boolean tweakerooSelection = enabled && drawLimit < 0 && entityTransforms.requiresNativeVisibility();
        boolean nativeVisibility = enabled && (nativeCulling || tweakerooSelection);
        if (this.enabled != enabled || this.physics != physics || this.nativeVisibility != nativeVisibility || itemDrawLimit != drawLimit) {
            clear();
            this.enabled = enabled;
            this.physics = physics;
            this.nativeVisibility = nativeVisibility;
            itemDrawLimit = drawLimit;
        }
        this.camera = camera.getPosition();
        if (level != minecraft.level || Math.abs(this.camera.x - originX) > 1024
                || Math.abs(this.camera.y - originY) > 1024 || Math.abs(this.camera.z - originZ) > 1024) {
            clear();
            level = minecraft.level;
            originX = Math.floor(this.camera.x / 256) * 256;
            originY = Math.floor(this.camera.y / 256) * 256;
            originZ = Math.floor(this.camera.z / 256) * 256;
        }
        irisCamera.set((float) (this.camera.x - originX), (float) (this.camera.y - originY), (float) (this.camera.z - originZ));
        this.view.set(view);
        this.projection.set(projection);
        this.partialTick = partialTick;
        ticks = level == null ? 0 : (int) (level.getGameTime() % 24000);
        foilTick = minecraft.gui.getGuiTicks();
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
        boolean dedicated = !nativeVisibility && !minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes() && !Carbon.Client.IRIS.shadowPass();
        if (!retainedFrame) retainedShadowCount = -1;
        retainedAllowed = retainedSupported && active && dedicated && !physics && !Carbon.Client.IRIS.active() && GL.getCapabilities().OpenGL43
                && level != null && !level.getChunkSource().getLightEngine().hasLightWork();
        if (retainedReady && (!retainedAllowed || sceneDirty || blockCacheDirty || retainedEntityCount != level.getEntityCount()
                || retainedSky != level.getSkyDarken())) discardRetained();
        boolean sameVisibility = this.camera.equals(sceneCamera) && this.view.equals(sceneView) && projection.equals(sceneProjection)
                && minecraft.getCameraEntity() == sceneCameraEntity && Entity.getViewScale() == sceneViewScale
                && minecraft.options.renderDistance == sceneRenderDistance
                && minecraft.options.entityDistanceScaling == sceneEntityDistance;
        retainedSameView = retainedFrame && sameVisibility;
        retainedFrame = false;
        if (!dedicated || !sameVisibility || sceneDirty || blockCacheDirty) {
            updating = false;
            updateQueue.clear();
            pendingUpdates = 0;
            sceneValid = false;
            sceneDirty = true;
        }
        sceneReused = active && dedicated && !updating
                && (minecraft.options.entityShadows && ((ShadowSettings) minecraft.getEntityRenderDispatcher()).imo$shadows()) == sceneShadows
                && sceneValid && sceneStationary && !sceneDirty && !blockCacheDirty && level.getSkyDarken() == sceneSkyDarken
                && (sceneTickStable || level.getGameTime() == sceneTick && foilTick == sceneFoilTick)
                && level.getGameTime() / 24000 == sceneTick / 24000
                && level.getEntityCount() == sceneEntityCount
                && !level.getChunkSource().getLightEngine().hasLightWork();
        if (sceneReused && !nbtItems.isEmpty() && nbtTick != minecraft.gui.getGuiTicks()) {
            nbtTick = minecraft.gui.getGuiTicks();
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
        cacheSelectedShadows = active && tweakerooSelection && !physics && Carbon.CONFIG.skipVisibilityChecks.get()
                && !Carbon.Client.IRIS.active() && !minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes();
        boolean lightWork = cacheSelectedShadows && level.getChunkSource().getLightEngine().hasLightWork();
        directShadows.begin(!cacheSelectedShadows || blockCacheDirty || lightWork, level == null ? 0 : level.getSkyDarken());
        if (shadows != null) {
            if (!sceneValid) shadows.retainedCount = 0;
            shadows.count = retained ? shadows.retainedCount : 0;
            shadows.flushed = false;
            if (lightWork) shadows.receivers.clear();
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

    public boolean submit(ItemStack stack, TransformType context, boolean leftHand, PoseStack pose,
                          int light, int overlay, BakedModel model) {
        boolean submitted = false;
        if (active && !capturing && context == TransformType.GROUND && !leftHand
                && overlay == OverlayTexture.NO_OVERLAY) {
            GpuDroppedItemBatch batch = find(stack, model, false);
            if (batch != null && !batch.flushed && batch.count < 65536) {
                transform.set(first.carbon.compat.RenderCompat.matrix(pose.last().pose()));
                transform.m30(transform.m30() + (float) (camera.x - originX));
                transform.m31(transform.m31() + (float) (camera.y - originY));
                transform.m32(transform.m32() + (float) (camera.z - originZ));
                batch.add(transform, first.carbon.compat.RenderCompat.matrix(pose.last().normal()), 0, 0, false, light, false, 0, 0, 0);
                batch.lastFrame = frame;
                instances++;
                submitted = true;
            }
        }
        return submitted;
    }

    public boolean submitEntity(ItemEntity entity, float partialTick, int light, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers) {
        if (retainedFrame) return false;
        boolean submitted = false;
        boolean shadow = Carbon.Client.IRIS.shadowPass();
        if (active && !capturing && entity.level == level && level.getEntity(entity.getId()) == entity
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
            int version = ((ComponentVersion) (Object) stack).imo$version();
            EntityCache cached = ((EntityCacheAccess) entity).imo$cache();
            var itemRenderer = Minecraft.getInstance().getItemRenderer();
            if (cached.stack != stack || cached.version != version || cached.generation != generation
                    || (cached.batch != null && cached.batch.closed)) {
                cached.stack = stack;
                cached.version = version;
                cached.generation = generation;
                cached.batch = null;
                cached.motionTick = Long.MIN_VALUE;
                cached.roll = 0;
                cached.rate = 0;
                BakedModel base = itemRenderer.getItemModelShaper().getItemModel(stack);
                cached.dynamicModel = base.getClass() == SimpleBakedModel.class && base.getOverrides().overrides.length != 0;
                if (base.getClass() == SimpleBakedModel.class && !cached.dynamicModel) {
                    cached.batch = find(stack, base, true);
                }
                cached.dynamicFoil = dynamicFoil.getOrDefault(stack.getItem().getClass(), true);
            }
            if (cached.batch == null && !cached.dynamicModel && cached.dynamicFoil) {
                BakedModel model = itemRenderer.getModel(stack, level, null, entity.getId());
                cached.batch = find(stack, model, true);
            }
            if (cached.dynamicModel) {
                cached.batch = find(stack, itemRenderer.getModel(stack, level, null, entity.getId()), true);
            }
            GpuDroppedItemBatch batch = cached.batch;
            if (batch != null && cached.dynamicFoil) {
                batch = find(stack, batch.model, true);
                cached.batch = batch;
            }
            if (batch != null && (shadow || !batch.flushed)) {
                ((ComponentVersion) (Object) stack).imo$track();
                int count = stack.getCount();
                int copies = count <= 1 ? 1 : count <= 16 ? 2 : count <= 32 ? 3 : count <= 48 ? 4 : 5;
                if (shadow || batch.count + copies <= 65536) {
                    boolean spread = true;
                    boolean bob = true;
                    float ageOffset = entity.getAge() - (float) ticks;
                    if (physics && cached.motionTick != level.getGameTime()) {
                        if (cached.motionTick != Long.MIN_VALUE) {
                            cached.roll = (cached.roll + cached.rate * Math.min(20, level.getGameTime() - cached.motionTick)) % (float) (Math.PI * 2);
                        }
                        double dx = entity.getX() - entity.xOld;
                        double dy = entity.getY() - entity.yOld;
                        double dz = entity.getZ() - entity.zOld;
                        double speed = dx * dx + dy * dy + dz * dz;
                        cached.rate = !entity.isOnGround() && speed > 1.0E-8 ? (float) Math.min(0.8, Math.sqrt(speed) * 4) : 0;
                        if (entity.isInWater()) cached.rate *= 0.35f;
                        if (entity.isInLava()) cached.rate *= 0.15f;
                        if (entity.isOnGround() && !batch.gui3d) cached.roll = 0;
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
                                pose.mulPose(new com.mojang.math.Quaternion(rotation.x, rotation.y, rotation.z, rotation.w));
                                itemRenderer.render(stack, TransformType.GROUND, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, batch.model);
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

    public void addShadow(ItemEntity entity, float partialTick, boolean enabled) {
        if (enabled && !Carbon.Client.IRIS.active() && shadows != null && !shadows.flushed) {
            if (cacheSelectedShadows) {
                directShadows.add(entity, partialTick);
            } else {
                shadows.add(entity, partialTick, 0, originX, originY, originZ);
                shadowInstances = shadows.count;
            }
        }
    }

    public Iterable<Entity> collect(Iterable<Entity> entities) {
        if (!active || itemDrawLimit < 0) return collectScene(entities);
        int previous = first.carbon.compat.TweakerooVisibility.suspendItemCounting();
        if (previous == first.carbon.compat.TweakerooVisibility.NO_COUNTER) {
            clear();
            return entities;
        }
        try {
            return collectScene(entities);
        } finally {
            first.carbon.compat.TweakerooVisibility.restoreItemCounting(previous);
        }
    }

    private Iterable<Entity> collectScene(Iterable<Entity> entities) {
        if (!active || nativeVisibility) return entities;
        var minecraft = Minecraft.getInstance();
        var dispatcher = minecraft.getEntityRenderDispatcher();
        if (dispatcher.shouldRenderHitBoxes() || Carbon.Client.IRIS.shadowPass()) return entities;
        boolean renderShadows = updating ? sceneShadows : minecraft.options.entityShadows && ((ShadowSettings) dispatcher).imo$shadows();
        if (retainedAllowed && (retainedReady || !sceneReused) && collectRetained(entities, minecraft, renderShadows)) return remainingForLimit(minecraft);
        if (sceneReused) {
            visibleCollected = sceneItems.size();
            return remainingForLimit(minecraft);
        }
        long start = System.nanoTime();
        boolean realtime = itemDrawLimit >= 0 || !sceneValid || !sceneStationary;
        for (Entity entity : entities) {
            if (entity instanceof ItemEntity item && (item.tickCount < 2
                    || item.xOld != item.getX() || item.yOld != item.getY() || item.zOld != item.getZ())) {
                realtime = true;
            }
        }
        if (realtime) {
            updating = false;
            updateQueue.clear();
        }
        long budget = realtime ? 0 : (long) (Carbon.CONFIG.cacheUpdateBudgetMs.get() * 1_000_000);
        boolean starting = !updating;
        boolean rendererChecked = false;
        boolean standardRenderer = false;
        if (starting) {
            updating = true;
            updateIndex = updateInstances = updateCollected = 0;
            updateStationary = updateTickStable = true;
            updateRemainingItems.clear();
            updateNbtItems.clear();
            updateSceneItems.clear();
            sceneDirty = false;
            for (Entity entity : entities) {
                if (entity instanceof ItemEntity item) updateQueue.add(item);
            }
            sceneTick = level.getGameTime();
            sceneFoilTick = foilTick;
            sceneSkyDarken = level.getSkyDarken();
            sceneEntityCount = level.getEntityCount();
            rememberView(minecraft);
            sceneShadows = renderShadows;
        }
        for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
            for (GpuDroppedItemBatch batch : bucket) {
                if (batch.entityBatch) {
                    if (starting) batch.updateCount = 0;
                    batch.swapUpdate();
                    if (starting) batch.physicsMoving = false;
                }
            }
        }
        if (shadows != null) {
            if (starting) shadows.updateCount = 0;
            shadows.swapUpdate();
        }
        instances = updateInstances;
        collected = updateCollected;
        while (updateIndex < updateQueue.size() && (itemDrawLimit < 0 || collected < itemDrawLimit)
                && (budget == 0 || updatedEntities == 0 || System.nanoTime() - start < budget)) {
            ItemEntity item = updateQueue.get(updateIndex++);
            updatedEntities++;
            boolean submitted = false;
            boolean culled = item.isRemoved();
            if (!culled && item.getClass() == ItemEntity.class && item.getType() == EntityType.ITEM
                    && item != minecraft.getCameraEntity() && !item.isVehicle() && ((EntityCacheAccess) item).imo$eligible()) {
                if (!rendererChecked) {
                    standardRenderer = dispatcher.getRenderer(item).getClass() == ItemEntityRenderer.class;
                    rendererChecked = true;
                }
                if (standardRenderer) {
                    culled = !entityCulling.isVisible(item, minecraft, camera);
                    if (!culled) {
                        Vec3 probe = item.getLightProbePosition(partialTick);
                        shadowPosition.set(Mth.floor(probe.x), Mth.floor(probe.y), Mth.floor(probe.z));
                        long key = shadowPosition.asLong();
                        int light = lightCells.get(key);
                        if (light == -1) {
                            light = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, shadowPosition), level.getBrightness(LightLayer.SKY, shadowPosition));
                            lightCells.put(key, light);
                        }
                        if (((EntityCacheAccess) item).imo$hasFire()) light = (light & 0xFFFF0000) | 240;
                        submitted = submitEntity(item, partialTick, light, null, null);
                        if (submitted) {
                            collected++;
                            updateSceneItems.add(item);
                            if (item.getItem().getTag() != null) updateNbtItems.add(item);
                            addShadow(item, partialTick, renderShadows);
                            EntityCache cached = ((EntityCacheAccess) item).imo$cache();
                            updateStationary &= item.xOld == item.getX() && item.yOld == item.getY() && item.zOld == item.getZ()
                                    && (!physics || cached.rate == 0)
                                    && !cached.dynamicModel;
                            updateTickStable &= !cached.dynamicFoil && (physics || item.getAge() != -32768);
                        }
                    }
                }
            }
            if (!submitted && !culled) {
                updateRemainingItems.add(item);
                updateStationary = false;
            }
        }
        if (itemDrawLimit >= 0 && collected == itemDrawLimit) updateIndex = updateQueue.size();
        updateInstances = instances;
        updateCollected = collected;
        pendingUpdates = updateQueue.size() - updateIndex;
        updating = pendingUpdates > 0;
        for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
            for (GpuDroppedItemBatch batch : bucket) {
                if (batch.entityBatch) {
                    if (updating) batch.swapUpdate();
                    else batch.retainedCount = batch.count;
                    if (batch.count > 0 || batch.updateCount > 0) batch.lastFrame = frame;
                }
            }
        }
        if (shadows != null) {
            if (updating) shadows.swapUpdate();
            else shadows.retainedCount = shadows.count;
        }
        shadowInstances = shadows == null ? 0 : shadows.count;
        if (updating) {
            instances = sceneValid ? sceneInstances : 0;
            collected = sceneValid ? sceneCollected : 0;
        } else {
            sceneValid = true;
            sceneBuilds++;
            sceneStationary = updateStationary;
            sceneTickStable = updateTickStable;
            sceneInstances = instances;
            sceneCollected = collected;
            Set<ItemEntity> remaining = remainingItems;
            remainingItems = updateRemainingItems;
            updateRemainingItems = remaining;
            List<ItemEntity> previousNbt = nbtItems;
            nbtItems = updateNbtItems;
            updateNbtItems = previousNbt;
            List<ItemEntity> previousItems = sceneItems;
            sceneItems = updateSceneItems;
            updateSceneItems = previousItems;
            updateQueue.clear();
        }
        visibleCollected = sceneItems.size();
        cacheUpdateNs = System.nanoTime() - start;
        remainingEntities.clear();
        for (Entity entity : entities) {
            if (!(entity instanceof ItemEntity) || remainingItems.contains(entity)) remainingEntities.add(entity);
        }
        remainingHasItems = !remainingItems.isEmpty();
        return remainingForLimit(minecraft);
    }

    private Iterable<Entity> remainingForLimit(Minecraft minecraft) {
        if (itemDrawLimit < 0 || !remainingHasItems) return remainingView;
        ItemRenderLimit.select(remainingEntities, itemDrawLimit - visibleCollected,
                item -> (item != minecraft.getCameraEntity() || minecraft.gameRenderer.getMainCamera().isDetached())
                        && (entityCulling.isVisible(item, minecraft, camera) || item.hasIndirectPassenger(minecraft.player)),
                limitedRemaining);
        return limitedRemaining;
    }

    private void rememberView(Minecraft minecraft) {
        sceneCamera = camera;
        sceneCameraEntity = minecraft.getCameraEntity();
        sceneView.set(view);
        sceneProjection.set(projection);
        sceneViewScale = Entity.getViewScale();
        sceneRenderDistance = minecraft.options.renderDistance;
        sceneEntityDistance = minecraft.options.entityDistanceScaling;
    }

    private boolean collectRetained(Iterable<Entity> entities, Minecraft minecraft, boolean renderShadows) {
        long start = System.nanoTime();
        if (retainedReady && retainedCheckTick != foilTick && !checkRetainedItems(minecraft)) discardRetained();
        if (!retainedReady) {
            if (sceneValid || updating || retainedAttemptTick == foilTick || !buildRetained(entities, minecraft)) return false;
        } else {
            sceneReuses++;
        }
        retainedFrame = true;
        retainedFrames++;
        sceneDirty = false;
        sceneValid = false;
        updating = false;
        updateQueue.clear();
        pendingUpdates = 0;
        boolean collectShadows = renderShadows && shadows != null && !shadows.flushed;
        // Refresh visibility each tick, and immediately when the camera or selected scene changes.
        if (retainedAllVisible && retainedSameView && retainedSelectionTick == foilTick
                && collectShadows == (retainedShadowCount >= 0)) {
            instances = retainedSelectionInstances;
            collected = sceneItems.size();
            if (shadows != null) shadows.count = collectShadows ? retainedShadowCount : 0;
        } else {
            selectRetainedItems(minecraft, collectShadows);
        }
        for (var entry : retainedBatches.entrySet()) {
            GpuDroppedItemBatch batch = entry.getKey();
            VisibleRanges ranges = entry.getValue();
            batch.count = ranges.visible == 0 ? 0 : ranges.capacity;
            batch.lastFrame = frame;
        }
        visibleCollected = collected;
        shadowInstances = shadows == null ? 0 : shadows.count;
        cacheUpdateNs = System.nanoTime() - start;
        return true;
    }

    private void selectRetainedItems(Minecraft minecraft, boolean collectShadows) {
        instances = collected = 0;
        sceneItems.clear();
        if (shadows != null) shadows.count = 0;
        if (collectShadows) visibleShadowSelection.clear();
        for (VisibleRanges ranges : retainedBatches.values()) ranges.clear();
        if (retainedBounds == null || minecraft.levelRenderer.cullingFrustum.isVisible(retainedBounds)) {
            for (int index = 0; index < retainedItems.size() && (itemDrawLimit < 0 || collected < itemDrawLimit); index++) {
                RetainedItem entry = retainedItems.get(index);
                ItemEntity item = entry.item;
                if (!entityCulling.isVisible(item, minecraft, camera)) continue;
                entry.ranges.add(entry.first, entry.copies);
                sceneItems.add(item);
                instances += entry.copies;
                collected++;
                if (collectShadows) visibleShadowSelection.set(index);
            }
        }
        if (collectShadows) {
            // Retained items are stationary. Camera rotation alone leaves these shadow vertices unchanged.
            if (retainedShadowCount >= 0 && retainedShadowSelection.equals(visibleShadowSelection)) {
                shadows.count = retainedShadowCount;
            } else {
                for (int index = visibleShadowSelection.nextSetBit(0); index >= 0; index = visibleShadowSelection.nextSetBit(index + 1)) {
                    shadows.add(retainedItems.get(index).item, partialTick, 0, originX, originY, originZ);
                }
                BitSet previous = retainedShadowSelection;
                retainedShadowSelection = visibleShadowSelection;
                visibleShadowSelection = previous;
                retainedShadowCount = shadows.count;
            }
        } else {
            retainedShadowCount = -1;
        }
        retainedAllVisible = collected == retainedItems.size() || collected == itemDrawLimit;
        retainedSelectionInstances = instances;
        retainedSelectionTick = foilTick;
        rememberView(minecraft);
    }

    private boolean buildRetained(Iterable<Entity> entities, Minecraft minecraft) {
        retainedAttemptTick = foilTick;
        var dispatcher = minecraft.getEntityRenderDispatcher();
        retainedCandidates.clear();
        boolean standard = false;
        for (Entity entity : entities) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (item.tickCount < 2 || item.xOld != item.getX() || item.yOld != item.getY() || item.zOld != item.getZ()
                    || retainedCandidates.size() == RETAINED_LIMIT) {
                retainedCandidates.clear();
                return false;
            }
            if (item.getClass() != ItemEntity.class || item.getType() != EntityType.ITEM || item.isRemoved()
                    || item == minecraft.getCameraEntity() || item.isVehicle() || !((EntityCacheAccess) item).imo$eligible()) continue;
            if (!standard && !(standard = dispatcher.getRenderer(item).getClass() == ItemEntityRenderer.class)) {
                retainedCandidates.clear();
                return false;
            }
            retainedCandidates.add(item);
        }
        if (retainedCandidates.size() < 256) {
            retainedCandidates.clear();
            return false;
        }
        for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
            for (GpuDroppedItemBatch batch : bucket) if (batch.entityBatch) batch.count = 0;
        }
        instances = collected = 0;
        retainedCandidates.sort(SPATIAL_ORDER);
        Set<ItemEntity> submitted = Collections.newSetFromMap(new IdentityHashMap<>());
        AABB combinedBounds = null;
        boolean boundsValid = true;
        for (ItemEntity item : retainedCandidates) {
            int previous = instances;
            if (!submitEntity(item, partialTick, dispatcher.getPackedLightCoords(item, partialTick), null, null)) continue;
            EntityCache cached = ((EntityCacheAccess) item).imo$cache();
            GpuDroppedItemBatch batch = cached.batch;
            int copies = instances - previous;
            BakedModel durabilityModel = cached.dynamicModel ? batch.model : null;
            if (batch.foil || durabilityModel != null && !hasDurabilityModel(item.getItem(), minecraft)) {
                batch.count -= copies;
                instances = previous;
                continue;
            }
            VisibleRanges ranges = retainedBatches.computeIfAbsent(batch, unused -> new VisibleRanges());
            ranges.capacity = batch.count;
            retainedItems.add(new RetainedItem(item, item.getItem(), cached.version, ranges, batch.count - copies, copies, item.getAge(), durabilityModel));
            if (boundsValid) {
                AABB bounds = item.getBoundingBoxForCulling();
                double size = bounds.getSize();
                boundsValid = !item.noCulling && Double.isFinite(size) && size > 0;
                if (boundsValid) combinedBounds = combinedBounds == null ? bounds : combinedBounds.minmax(bounds);
            }
            submitted.add(item);
        }
        retainedCandidates.clear();
        if (retainedItems.isEmpty()) return false;
        remainingEntities.clear();
        remainingHasItems = false;
        for (Entity entity : entities) {
            if (!submitted.contains(entity)) {
                remainingEntities.add(entity);
                remainingHasItems |= entity instanceof ItemEntity;
            }
        }
        retainedStartClock = ticks;
        retainedAgeDelta = 0;
        retainedSky = level.getSkyDarken();
        retainedEntityCount = level.getEntityCount();
        retainedCheckTick = foilTick;
        // Enclose vanilla's inflated per-item boxes. An intersecting scene still uses vanilla selection.
        retainedBounds = boundsValid && combinedBounds != null ? combinedBounds.inflate(1.0) : null;
        retainedReady = true;
        sceneBuilds++;
        return true;
    }

    private boolean hasDurabilityModel(ItemStack stack, Minecraft minecraft) {
        if (!stack.isDamageableItem()) return false;
        BakedModel model = minecraft.getItemRenderer().getItemModelShaper().getItemModel(stack);
        return durabilityModels.computeIfAbsent(model, base -> {
            var overrides = base.getOverrides();
            if (overrides.getClass() != net.minecraft.client.renderer.block.model.ItemOverrides.class) return false;
            for (var property : overrides.properties) {
                if (!property.getNamespace().equals("minecraft")
                        || !(property.getPath().equals("damage") || property.getPath().equals("damaged"))) return false;
            }
            return true;
        });
    }

    private boolean checkRetainedItems(Minecraft minecraft) {
        int delta = retainedItems.get(0).item.getAge() - retainedItems.get(0).age;
        for (RetainedItem entry : retainedItems) {
            if (entry.item.isRemoved() || entry.item.getItem() != entry.stack || entry.item.getAge() - entry.age != delta
                    || ((ComponentVersion) (Object) entry.stack).imo$version() != entry.version
                    || ((FoilState) (Object) entry.stack).imo$foil((int) foilTick)) return false;
            if (entry.durabilityModel != null && minecraft.getItemRenderer().getModel(entry.stack, level, null,
                    entry.item.getId()) != entry.durabilityModel) return false;
            if (entry.item.noCulling) retainedBounds = null;
        }
        retainedAgeDelta = delta;
        retainedCheckTick = foilTick;
        return true;
    }

    private void discardRetained() {
        retainedReady = false;
        retainedAllVisible = false;
        retainedShadowCount = -1;
        retainedBounds = null;
        retainedItems.clear();
        retainedBatches.clear();
    }

    private record RetainedItem(ItemEntity item, ItemStack stack, int version, VisibleRanges ranges, int first,
                                int copies, int age, BakedModel durabilityModel) {}

    private static final class VisibleRanges {
        final IntArrayList spans = new IntArrayList();
        int capacity, visible;

        void clear() {
            spans.clear();
            visible = 0;
        }

        void add(int first, int copies) {
            int last = spans.size() - 1;
            if (last >= 0 && spans.getInt(last - 1) + spans.getInt(last) == first) spans.set(last, spans.getInt(last) + copies);
            else {
                spans.add(first);
                spans.add(copies);
            }
            visible += copies;
        }
    }

    private void drawRanges(VisibleRanges ranges, int indexCount) {
        int ints = ranges.spans.size() / 2 * 5;
        if (indirectCommands == null || indirectCommands.capacity() < ints) {
            if (indirectCommands != null) MemoryUtil.memFree(indirectCommands);
            indirectCommands = MemoryUtil.memAllocInt(Integer.highestOneBit(ints - 1) * 2);
        }
        indirectCommands.clear();
        for (int i = 0; i < ranges.spans.size(); i += 2) {
            indirectCommands.put(indexCount).put(ranges.spans.getInt(i + 1)).put(0).put(0).put(ranges.spans.getInt(i));
        }
        indirectCommands.flip();
        if (indirectBuffer == 0) indirectBuffer = glGenBuffers();
        int previous = glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING);
        try {
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
            glBufferData(GL_DRAW_INDIRECT_BUFFER, indirectCommands, GL_STREAM_DRAW);
            glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, ints / 5, 0);
        } finally {
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, previous);
        }
    }

    private GpuDroppedItemBatch find(ItemStack stack, BakedModel model, boolean entityBatch) {
        if (stack.isEmpty()) {
            return null;
        }
        boolean foil = ((FoilState) (Object) stack).imo$foil((int) foilTick);
        GpuDroppedItemBatch result = null;
        if (model.getClass() == SimpleBakedModel.class && !rejected.contains(model)
                && !stack.is(Items.TRIDENT) && !stack.is(Items.SPYGLASS)
                && !(foil && (stack.is(Items.COMPASS) || stack.is(Items.CLOCK)))) {
            dynamicFoil.computeIfAbsent(stack.getItem().getClass(), type -> {
                try {
                    return type.getMethod("isFoil", ItemStack.class).getDeclaringClass() != Item.class;
                } catch (ReflectiveOperationException exception) {
                    return true;
                }
            });
            int hash = (31 * stack.getItem().hashCode() + (stack.getTag() == null ? 0 : stack.getTag().hashCode()));
            List<GpuDroppedItemBatch> bucket = buckets.get(hash);
            if (bucket != null) {
                for (GpuDroppedItemBatch candidate : bucket) {
                    if (candidate.model == model && candidate.entityBatch == entityBatch && candidate.foil == foil && ItemStack.isSameItemSameTags(candidate.stack, stack)) {
                        result = candidate;
                        break;
                    }
                }
            }
            if (result == null) {
                boolean supported = true;
                for (BakedModel pass : List.of(model)) {
                    for (RenderType type : List.of(net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderType(stack, false))) {
                        supported &= type == RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS) || type == RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS) || type == RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS) || type == RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS) || type == RenderType.itemEntityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
                    }
                }
                if (supported) {
                    try {
                        result = new GpuDroppedItemBatch(stack, model, this, entityBatch, foil);
                        if (bucket == null) {
                            bucket = new ArrayList<>();
                            buckets.put(hash, bucket);
                        }
                        bucket.add(result);
                        cacheMisses++;
                        modelBytes += result.meshBytes;
                    } catch (Exception exception) {
                        rejected.add(model);
                        logger.debug("Keeping vanilla renderer for {}: {}", stack.getItem(), exception.toString());
                    }
                } else {
                    rejected.add(model);
                }
            }
        }
        if (result != null) result.lastFrame = frame;
        return result;
    }

    private boolean hasPendingDraws() {
        if (shadows != null && shadows.count > 0 && !shadows.flushed) return true;
        for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
            for (GpuDroppedItemBatch batch : bucket) {
                if (batch.count > 0 && !batch.flushed) return true;
            }
        }
        return false;
    }

    public void flush() {
        if (active && cacheSelectedShadows && shadows != null && !shadows.flushed) {
            directShadows.prepare(shadows, originX, originY, originZ);
            shadowInstances = shadows.count;
        }
        if (active && hasPendingDraws()) {
            int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            int buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
            int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
            try (MemoryStack memory = MemoryStack.stackPush()) {
                FloatBuffer matrix = memory.mallocFloat(16);
                for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
                    for (GpuDroppedItemBatch batch : bucket) {
                        if (batch.count > 0 && !batch.flushed) {
                            VisibleRanges ranges = retainedFrame ? retainedBatches.get(batch) : null;
                            float batchClock = ranges == null ? clock : retainedStartClock + retainedAgeDelta + partialTick;
                            batch.processed = batch.entityBatch && processor != null;
                            int drawProgram = batch.entityBatch ? batch.processed ? preparedProgram : entityProgram : program;
                            uploadedBytes += batch.upload();
                            if (batch.processed && processor.prepare(batch, batchClock)) transformDispatches++;
                            Carbon.Client.RENDERER.markSprites(batch.sprites);
                            for (int pass = 0; pass < (batch.foil ? 2 : 1); pass++) {
                                for (GpuDroppedItemBatch.Mesh mesh : batch.meshes) {
                                    RenderType type = pass == 0 ? mesh.type : RenderType.glint();
                                    type.setupRenderState();
                                    try {
                                        ShaderInstance vanilla = Carbon.Client.IRIS.shader(RenderSystem.getShader(), type, true);
                                        if (vanilla != null) {
                                            if (vanilla.MODEL_VIEW_MATRIX != null) vanilla.MODEL_VIEW_MATRIX.set(first.carbon.compat.RenderCompat.minecraft(view)); if (vanilla.PROJECTION_MATRIX != null) vanilla.PROJECTION_MATRIX.set(first.carbon.compat.RenderCompat.minecraft(projection));
                                            RenderSystem.setupShaderLights(vanilla);
                                            for (int sampler = 0; sampler < 3; sampler++) {
                                                vanilla.setSampler("Sampler" + sampler, RenderSystem.getShaderTexture(sampler));
                                            }
                                            Carbon.Client.IRIS.apply(vanilla);
                                        }
                                        if (Carbon.Client.IRIS.usesShader(vanilla)) {
                                            int mode = batch.processed ? batch.physics ? 2 : 1 : batch.physics ? 4 : 3;
                                            Carbon.Client.IRIS.draw(vanilla, mode, batch.processed ? batch.preparedBuffer : batch.instanceBuffer,
                                                    mesh.vertexBuffer, mesh.indexBuffer, mesh.format, mesh.indices, batch.count,
                                                    irisCamera, clock, batch.groundScaleY, batch.center, batch.halfSize, batch.flat);
                                        } else {
                                            glUseProgram(drawProgram);
                                            glUniform1i(glGetUniformLocation(drawProgram, "GlintPass"), pass);
                                            glUniformMatrix4fv(glGetUniformLocation(drawProgram, "TextureMat"), false, first.carbon.compat.RenderCompat.floats(RenderSystem.getTextureMatrix(), matrix));
                                            glUniform1f(glGetUniformLocation(drawProgram, "GlintAlpha"), 1.0f);
                                            glUniformMatrix4fv(glGetUniformLocation(drawProgram, "View"), false, view.get(matrix));
                                            glUniformMatrix4fv(glGetUniformLocation(drawProgram, "Projection"), false, projection.get(matrix));
                                            glUniform3f(glGetUniformLocation(drawProgram, "Camera"), (float) (camera.x - originX),
                                                    (float) (camera.y - originY), (float) (camera.z - originZ));
                                            glUniform1f(glGetUniformLocation(drawProgram, "Clock"), batchClock);
                                            glUniform1f(glGetUniformLocation(drawProgram, "GroundScaleY"), batch.groundScaleY);
                                            if (batch.physics) {
                                                glUniform3f(glGetUniformLocation(drawProgram, "Center"), batch.center.x, batch.center.y, batch.center.z);
                                                glUniform3f(glGetUniformLocation(drawProgram, "HalfSize"), batch.halfSize.x, batch.halfSize.y, batch.halfSize.z);
                                                glUniform1i(glGetUniformLocation(drawProgram, "Flat"), batch.flat ? 1 : 0);
                                            }
                                            for (int sampler = 0; sampler < 3; sampler++) {
                                                glUniform1i(glGetUniformLocation(drawProgram, "Sampler" + sampler), sampler);
                                            }
                                            float[] fog = RenderSystem.getShaderFogColor();
                                            glUniform4f(glGetUniformLocation(drawProgram, "FogColor"), fog[0], fog[1], fog[2], fog[3]);
                                            float[] color = RenderSystem.getShaderColor();
                                            glUniform4f(glGetUniformLocation(drawProgram, "ColorModulator"), color[0], color[1], color[2], color[3]);
                                            glUniform1f(glGetUniformLocation(drawProgram, "FogStart"), RenderSystem.getShaderFogStart());
                                            glUniform1f(glGetUniformLocation(drawProgram, "FogEnd"), RenderSystem.getShaderFogEnd());
                                            glUniform1i(glGetUniformLocation(drawProgram, "FogShape"), RenderSystem.getShaderFogShape().getIndex());
                                            if (vanilla != null && vanilla.LIGHT0_DIRECTION != null && vanilla.LIGHT1_DIRECTION != null) {
                                                glUniform3fv(glGetUniformLocation(drawProgram, "Light0"), vanilla.LIGHT0_DIRECTION.getFloatBuffer());
                                                glUniform3fv(glGetUniformLocation(drawProgram, "Light1"), vanilla.LIGHT1_DIRECTION.getFloatBuffer());
                                            }
                                            mesh.bind();
                                            if (ranges != null && ranges.visible < batch.count) drawRanges(ranges, mesh.indices);
                                            else glDrawElementsInstanced(GL_TRIANGLES, mesh.indices, GL_UNSIGNED_INT, 0, batch.count);
                                        }
                                        drawCalls++;
                                        if (vanilla != null) {
                                            vanilla.clear();
                                        }
                                    } finally {
                                        type.clearRenderState();
                                    }
                                }
                            }
                            batch.flushed = true;
                        }
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
        return String.format(Locale.ROOT, "%d instances (%d collected), %d instanced draws, %d shadow instances/%d shadow draws, %d new meshes, %d instance bytes/frame, %d mesh bytes/frame, %d scene builds/%d reuses, %d transform dispatches, %d queued/%d updated, %.3f ms cache update, %d retained-view frames",
                instances, collected, drawCalls, shadowInstances, shadowDrawCalls, cacheMisses, uploadedBytes, modelBytes, sceneBuilds, sceneReuses, transformDispatches, pendingUpdates, updatedEntities, cacheUpdateNs / 1_000_000.0, retainedFrames)
                + (cacheSelectedShadows ? ", " + directShadows.reused + " shadow item reuses" : "");
    }

    public void invalidate() {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            clear();
        } else {
            RenderSystem.recordRenderCall(this::clear);
        }
    }

    public boolean capturingModel() {
        return capturing;
    }

    private void clear() {
        limitedRemaining.clear();
        remainingHasItems = false;
        directShadows.begin(true, 0);
        active = false;
        retainedFrame = false;
        discardRetained();
        retainedCandidates.clear();
        durabilityModels.clear();
        retainedAttemptTick = Long.MIN_VALUE;
        if (indirectBuffer != 0) glDeleteBuffers(indirectBuffer);
        if (indirectCommands != null) MemoryUtil.memFree(indirectCommands);
        indirectBuffer = 0;
        indirectCommands = null;
        level = null;
        generation++;
        for (List<GpuDroppedItemBatch> bucket : buckets.values()) {
            for (GpuDroppedItemBatch batch : bucket) {
                batch.close();
            }
        }
        buckets.clear();
        dynamicFoil.clear();
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
        retainedFrames = 0;
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
        boolean imo$hasFire();
        void imo$invalidate();
    }

    public static final class EntityCache {
        private ItemStack stack;
        private GpuDroppedItemBatch batch;
        private int version;
        private int generation = -1;
        private boolean dynamicModel;
        private boolean dynamicFoil;
        private long motionTick = Long.MIN_VALUE;
        private float roll, rate;
    }

}
