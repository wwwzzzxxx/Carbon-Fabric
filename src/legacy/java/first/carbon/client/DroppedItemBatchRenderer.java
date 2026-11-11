package first.carbon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import first.carbon.Carbon;
import first.carbon.compat.OptiFineCompatibility;
import first.carbon.compat.RenderCompat;
import first.carbon.compat.SodiumVisibility;
import first.carbon.compat.TweakerooVisibility;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseInstance;

public final class DroppedItemBatchRenderer {
    private static final int STRIDE = 36;
    private static final int MAX_GROUPS = 256;
    private static final int MAX_DRAW_RANGES = 16;
    private static final long MAX_INSTANCE_BYTES = 32L * 1024 * 1024;
    private static final ResourceLocation DAMAGE = new ResourceLocation("damage");
    private static final ResourceLocation DAMAGED = new ResourceLocation("damaged");
    private static final Comparator<ItemEntity> SPATIAL_ORDER = Comparator
            .comparingInt((ItemEntity item) -> Mth.floor(item.getX() * 0.5))
            .thenComparingInt(item -> Mth.floor(item.getZ() * 0.5))
            .thenComparingInt(item -> Mth.floor(item.getY() * 0.5))
            .thenComparingDouble(ItemEntity::getX)
            .thenComparingDouble(ItemEntity::getZ);

    private final Map<Item, ItemGroups> groups = new IdentityHashMap<>();
    private final Map<RenderType, Layer> layers = new IdentityHashMap<>();
    private final Set<Item> rejected = Collections.newSetFromMap(new IdentityHashMap<Item, Boolean>());
    private final List<Group> used = new ArrayList<>();
    private final List<Group> sceneGroups = new ArrayList<>();
    private final DroppedSceneBounds sceneBounds = new DroppedSceneBounds();
    private final List<Entity> remaining = new ArrayList<>();
    private final List<Entity> remainingView = Collections.unmodifiableList(remaining);
    private final List<Entity> limitedRemaining = new ArrayList<>();
    private final List<Entity> overflow = new ArrayList<>();
    private final List<Entity> overflowView = Collections.unmodifiableList(overflow);
    private final List<Entity> optiFineSelection = new ArrayList<>();
    private final List<ItemEntity> shadowSelection = new ArrayList<>();
    private final LegacyState state = new LegacyState();
    private final FabricLoader loader = FabricLoader.getInstance();
    private final OptiFineCompatibility optiFine = new OptiFineCompatibility();
    private final SodiumVisibility sodiumVisibility = new SodiumVisibility();
    private final boolean entityCulling = loader.isModLoaded("entityculling");
    private final boolean tweakermore = loader.isModLoaded("tweakermore");
    private final Option precise = option("me.fallenbreath.tweakermore.config.TweakerMoreConfigs",
            "PRECISE_ITEM_ENTITY_MODEL", "getBooleanValue", tweakermore);

    private ClientLevel level;
    private Entity cameraEntity;
    private Vec3 camera = Vec3.ZERO;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 nativeShadowOrigin = Vec3.ZERO;
    private Vec3 selectedCamera;
    private Matrix4f selectedView, selectedProjection;
    private double selectedViewScale;
    private Frustum frustum;
    private final float[] viewPose = new float[16], viewNormal = new float[9];
    private float partialTick;
    private float shadowPartialTick;
    private boolean active, cacheAllowed, cacheNativeSelection, collectedThisFrame, sceneReady, failed, replaying;
    private boolean remainingHasItems, detachedCamera, selectedDetachedCamera;
    private int drawLimit = -1, selectedLimit = -1;
    private int optiFineLimit = -1, optiFineCounter = TweakerooVisibility.NO_COUNTER;
    private boolean renderShadows, shadowFailed;
    private boolean shadowCaptureOpen;
    private LegacyDroppedShadows shadows;
    private ItemEntity pendingShadow;
    private MultiBufferSource pendingShadowBuffer;
    private int shadowSkyDarken;
    public volatile boolean sceneDirty = true;
    public volatile boolean sceneMoved;
    private boolean sceneRemoved;
    private boolean lightDirty;
    private boolean shadowLightDirty;
    private int program, frame;
    private int groupCount;
    private long clientTick, sceneTick, originTick, nativeOriginTick;
    private long allocatedBytes;
    private ByteBuffer selectionBuffer;
    private int selectionLastFrame;
    public int entities, collected, instances, drawCalls, rangeDraws, nativeFallbacks;
    public int shadowInstances, shadowDrawCalls;
    public long sceneBuilds, sceneReuses, uploadedBytes;
    public long invisibleFrames;

    public void clientTick() { clientTick++; }

    public void lightingChanged() { lightDirty = shadowLightDirty = true; }

    public void itemMoved() {
        sceneMoved = true;
        if (shadows != null) shadows.selectionChanged();
    }

    public void itemDataChanged() {
        sceneDirty = true;
        if (shadows != null) shadows.selectionChanged();
    }

    public void entityRemoved(Entity entity) {
        if (entity.getClass() == ItemEntity.class) {
            sceneRemoved = true;
            if (shadows != null) shadows.remove((ItemEntity) entity);
        } else sceneDirty = true;
    }

    public void beginWorld(Camera camera, PoseStack pose, float partialTick) {
        restoreOptiFineCounter();
        Minecraft minecraft = Minecraft.getInstance();
        frame++;
        entities = collected = instances = drawCalls = rangeDraws = nativeFallbacks = 0;
        shadowInstances = shadowDrawCalls = 0;
        shadowSelection.clear();
        pendingShadow = null;
        pendingShadowBuffer = null;
        shadowCaptureOpen = true;
        uploadedBytes = 0;
        collectedThisFrame = false;
        frustum = null;
        if (!sceneReady) for (Group group : used) group.clearItems();
        used.clear();
        active = !failed && Carbon.CONFIG.enabled.get() && Carbon.CONFIG.optimizeDroppedItems.get()
                && !Carbon.CONFIG.physicalDroppedItems.get() && Carbon.Client.IRIS.canInstance()
                && (!tweakermore || precise != null && !precise.bool(true))
                && GL.getCapabilities().OpenGL33 && glGetInteger(GL_CURRENT_PROGRAM) == 0 && optiFine.canInstance();
        if (active && program == 0) {
            try {
                program = ItemBatchRenderer.createProgram("legacy_dropped.vsh");
                LogManager.getLogger("Carbon").info("Legacy dropped-item instancing ready: {}", glGetString(GL_RENDERER));
            } catch (Exception exception) {
                LogManager.getLogger("Carbon").warn("Dropped-item instancing unavailable", exception);
                active = false;
                failed = true;
            }
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        boolean drawShadows = minecraft.options.entityShadows && dispatcher.shouldRenderShadow;
        if (active && drawShadows && !shadowFailed && shadows == null) {
            try {
                shadows = new LegacyDroppedShadows(ItemBatchRenderer.createProgram("legacy_shadow.vsh"), state);
            } catch (Exception exception) {
                shadowFailed = true;
                LogManager.getLogger("Carbon").warn("Dropped-item shadow instancing unavailable", exception);
            }
        }
        boolean cache = active && Carbon.CONFIG.skipVisibilityChecks.get() && !entityCulling
                && (!drawShadows || shadows != null) && !dispatcher.shouldRenderHitBoxes();
        boolean chooseVisibility = cache && !optiFine.present();
        int limit = chooseVisibility ? TweakerooVisibility.itemDrawLimit() : -1;
        cacheNativeSelection = chooseVisibility && limit < 0 && TweakerooVisibility.requiresNativeSelection();
        cacheAllowed = cache && !cacheNativeSelection;
        drawLimit = limit;
        Vec3 position = camera.getPosition();
        boolean newLevel = level != minecraft.level;
        if (!cacheAllowed || newLevel || camera.getEntity() != cameraEntity
                || position.distanceToSqr(origin) > 128 * 128 || renderShadows != drawShadows) {
            resetScene();
        }
        if (newLevel || position.distanceToSqr(nativeShadowOrigin) > 128 * 128 || clientTick - nativeOriginTick >= 1 << 20) {
            nativeShadowOrigin = position;
            nativeOriginTick = clientTick;
        }
        renderShadows = drawShadows;
        boolean lightWork = active && minecraft.level.getChunkSource().getLightEngine().hasLightWork();
        lightDirty |= lightWork;
        if (renderShadows && shadows != null) {
            int skyDarken = minecraft.level.getSkyDarken();
            if (newLevel || shadowLightDirty || lightWork || skyDarken != shadowSkyDarken) shadows.invalidate();
            shadowLightDirty = false;
            shadowSkyDarken = skyDarken;
        }
        this.partialTick = shadowPartialTick = partialTick;
        this.camera = position;
        cameraEntity = camera.getEntity();
        detachedCamera = camera.isDetached();
        level = minecraft.level;
        if (active) storeMatrices(pose, viewPose, viewNormal);
        if ((frame & 63) == 0) trim();
    }

    public void captureFrustum(Frustum actual) {
        frustum = actual;
    }

    public Iterable<Entity> collect(Iterable<Entity> original, MultiBufferSource buffers, PoseStack pose, Matrix4f projection, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active) storeMatrices(pose, viewPose, viewNormal);
        if (!cacheAllowed || optiFine.present() || frustum == null || minecraft.levelRenderer.capturedFrustum != null) return original;
        this.partialTick = shadowPartialTick = partialTick;
        double viewScale = Entity.getViewScale();
        if (sceneReady && !sceneDirty && !sceneMoved && !sceneRemoved && (drawLimit < 0 || !remainingHasItems)
                && sceneBounds.outside(frustum, camera, viewScale)) {
            collectedThisFrame = true;
            selectedCamera = null;
            for (Group group : sceneGroups) group.touch();
            invisibleFrames++;
            return remainingView;
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        boolean refresh = sceneTick != clientTick || sceneMoved || sceneRemoved || lightDirty;
        boolean rebuild = !sceneReady || sceneDirty || clientTick - originTick >= 1 << 20;
        if (rebuild || refresh && !refreshScene(dispatcher)) {
            buildScene(original, dispatcher);
            rebuild = true;
        } else {
            sceneReuses++;
        }

        Matrix4f view = pose.last().pose();
        boolean sameGraph = sodiumVisibility.unchanged();
        boolean sameSelection = !rebuild && !refresh && sameGraph && drawLimit == selectedLimit
                && detachedCamera == selectedDetachedCamera
                && camera.equals(selectedCamera) && view.equals(selectedView)
                && projection.equals(selectedProjection) && viewScale == selectedViewScale;
        if (!sameSelection) {
            if (drawLimit > 0) {
                int previous = TweakerooVisibility.suspendItemCounting();
                if (previous == TweakerooVisibility.NO_COUNTER) {
                    resetScene();
                    cacheNativeSelection = true;
                    cacheAllowed = false;
                    return original;
                }
                try {
                    // Keep the dispatcher visibility hooks while Carbon allocates the item budget.
                    selectScene(dispatcher);
                } finally {
                    TweakerooVisibility.restoreItemCounting(previous);
                }
            } else {
                selectScene(dispatcher);
            }
            selectedCamera = camera;
            selectedView = view.copy();
            selectedProjection = projection.copy();
            selectedViewScale = viewScale;
            selectedLimit = drawLimit;
            selectedDetachedCamera = detachedCamera;
        }
        return collectReady(buffers, false);
    }

    @SuppressWarnings("unchecked")
    public void collectOptiFine(Map<?, ?> groups, MultiBufferSource buffers, PoseStack pose, float partialTick) {
        if (optiFineLimit >= 0) {
            int budget = optiFineLimit;
            restoreOptiFineCounter();
            for (Object entry : groups.values()) {
                if (entry instanceof List) {
                    List<?> items = (List<?>) entry;
                    if (!items.isEmpty() && items.get(0) instanceof ItemEntity) {
                        int kept = Math.min(items.size(), budget);
                        if (kept < items.size()) items.subList(kept, items.size()).clear();
                        budget -= kept;
                    }
                }
            }
        }
        if (!cacheAllowed || !optiFine.present() || collectedThisFrame) return;
        Object entries = groups.get(ItemEntity.class.getName());
        if (!(entries instanceof List) || ((List<?>) entries).isEmpty()) {
            if (sceneReady) resetScene();
            return;
        }
        List<Entity> selected = (List<Entity>) entries;
        this.partialTick = partialTick;
        storeMatrices(pose, viewPose, viewNormal);
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean refresh = sceneTick != clientTick || sceneMoved || sceneRemoved || lightDirty;
        boolean rebuild = !sceneReady || sceneDirty || clientTick - originTick >= 1 << 20 || !optiFineSelection.equals(selected);
        if (optiFine.dynamicLighting() && sceneTick != clientTick) lightDirty = true;
        if (rebuild || refresh && !refreshScene(dispatcher)) {
            buildScene(selected, dispatcher);
            optiFineSelection.addAll(selected);
        } else {
            sceneReuses++;
        }
        Iterable<Entity> remainder = collectReady(buffers, true);
        selected.clear();
        for (Entity entity : remainder) selected.add(entity);
    }

    public void beginOptiFineLimit() {
        if (cacheAllowed && optiFineCounter == TweakerooVisibility.NO_COUNTER && optiFine.supportsGroupedSelection()) {
            int limit = TweakerooVisibility.itemDrawLimit();
            if (limit >= 0) {
                optiFineCounter = TweakerooVisibility.suspendItemCounting();
                if (optiFineCounter != TweakerooVisibility.NO_COUNTER) optiFineLimit = limit;
            }
        }
    }

    private void restoreOptiFineCounter() {
        TweakerooVisibility.restoreItemCounting(optiFineCounter);
        optiFineCounter = TweakerooVisibility.NO_COUNTER;
        optiFineLimit = -1;
    }

    private void selectScene(EntityRenderDispatcher dispatcher) {
        int budget = drawLimit < 0 ? Integer.MAX_VALUE : drawLimit;
        for (Group group : sceneGroups) {
            group.select(dispatcher, frustum, budget);
            budget -= group.items.size();
        }
        limitedRemaining.clear();
        if (drawLimit >= 0 && remainingHasItems) {
            Entity player = Minecraft.getInstance().player;
            for (Entity entity : remaining) {
                if (!(entity instanceof ItemEntity)) {
                    limitedRemaining.add(entity);
                } else if (budget > 0 && !entity.removed && (entity != cameraEntity || detachedCamera)
                        && (dispatcher.shouldRender(entity, frustum, camera.x, camera.y, camera.z)
                        || entity.hasIndirectPassenger(player))) {
                    limitedRemaining.add(entity);
                    budget--;
                }
            }
        }
    }

    private Iterable<Entity> collectReady(MultiBufferSource buffers, boolean preselected) {
        collectedThisFrame = true;
        overflow.clear();
        List<Entity> remainder = !preselected && drawLimit >= 0 && remainingHasItems ? limitedRemaining : remainingView;
        for (Group group : sceneGroups) {
            if (preselected) group.selectAll();
            if (group.items.isEmpty()) continue;
            if (!group.layer.bind(buffers) || group.needsCompaction() && !reserveSelection(group.items.size())) {
                if (overflow.isEmpty()) overflow.addAll(remainder);
                overflow.addAll(group.items);
                continue;
            }
            used.add(group);
            entities += group.items.size();
            instances += group.items.size() * group.copies;
            if (renderShadows) {
                for (int index = 0; index < group.items.size(); index++) shadowSelection.add(group.items.get(index));
            }
        }
        collected = entities;
        return overflow.isEmpty() ? remainder : overflowView;
    }

    private void buildScene(Iterable<Entity> original, EntityRenderDispatcher dispatcher) {
        resetScene();
        origin = camera;
        originTick = clientTick;
        sceneDirty = false;
        sceneMoved = sceneRemoved = lightDirty = false;
        int shadowItems = 0;
        for (Entity entity : original) {
            if (entity.getClass() == ItemEntity.class && entity != cameraEntity && !entity.isVehicle()) {
                ItemEntity item = (ItemEntity) entity;
                if (item.removed) continue;
                EntityRenderer<?> renderer = dispatcher.getRenderer(item);
                if (renderer.getClass() == ItemEntityRenderer.class && (!renderShadows || shadowItems < LegacyDroppedShadows.MAX_ITEMS
                        && renderer.shadowRadius == 0.15F && renderer.shadowStrength == 0.75F)) {
                    Group group = group(item);
                    if (group != null && group.canAppend(group.cachedItems.size())) {
                        if (group.cachedItems.isEmpty()) sceneGroups.add(group);
                        group.cachedItems.add(item);
                        if (!optiFine.present()) sceneBounds.include(item);
                        shadowItems++;
                        continue;
                    }
                }
            }
            remaining.add(entity);
            remainingHasItems |= entity instanceof ItemEntity;
        }
        for (Group group : sceneGroups) {
            if (!optiFine.present() && GL.getCapabilities().OpenGL42) group.cachedItems.sort(SPATIAL_ORDER);
            for (int i = 0; i < group.cachedItems.size(); i++) {
                ItemEntity item = group.cachedItems.get(i);
                group.write(item, i * STRIDE, dispatcher.getPackedLightCoords(item, partialTick), origin, item.getAge(), false);
            }
            group.touch();
        }
        sceneTick = clientTick;
        sceneReady = true;
        sceneBuilds++;
    }

    public boolean submit(ItemEntity entity, MultiBufferSource buffers, int light, float partialTick) {
        if (!active || collectedThisFrame || replaying || Carbon.Client.RENDERER.capturing || !(buffers instanceof MultiBufferSource.BufferSource)
                || entity.getClass() != ItemEntity.class || entity.level != level || entity == cameraEntity) return false;
        if (sceneReady) resetScene();
        Group group = group(entity);
        if (group == null || group.drawnFrame == frame || !group.layer.bind(buffers) || !group.canAppend(group.items.size())) return false;
        this.partialTick = partialTick;
        group.append(entity, light);
        if (renderShadows && shadows != null) {
            pendingShadow = entity;
            pendingShadowBuffer = buffers;
        }
        return true;
    }

    public boolean submitSelected(Entity entity, MultiBufferSource buffers, float partialTick) {
        if (!cacheNativeSelection || !active || entity.getClass() != ItemEntity.class
                || renderShadows && shadowSelection.size() >= LegacyDroppedShadows.MAX_ITEMS) return false;
        ItemEntity item = (ItemEntity) entity;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<?> renderer = dispatcher.getRenderer(item);
        if (renderer.getClass() != ItemEntityRenderer.class
                || renderShadows && (renderer.shadowRadius != 0.15F || renderer.shadowStrength != 0.75F)
                || !submit(item, buffers, dispatcher.getPackedLightCoords(item, partialTick), partialTick)) return false;
        if (renderShadows) {
            shadowSelection.add(item);
            pendingShadow = null;
            pendingShadowBuffer = null;
        }
        return true;
    }

    public boolean captureShadow(Entity entity, MultiBufferSource buffers, float weight, float partialTick,
                                 LevelReader shadowLevel, float radius) {
        if (!active || !shadowCaptureOpen || collectedThisFrame || entity != pendingShadow || buffers != pendingShadowBuffer
                || shadowLevel != level || radius != 0.15F || partialTick != shadowPartialTick
                || shadowSelection.size() == LegacyDroppedShadows.MAX_ITEMS || Carbon.Client.IRIS.active()) return false;
        float expected = (float) ((1.0 - camera.distanceToSqr(entity.position()) / 256.0) * 0.75F);
        if (weight != expected) return false;
        shadowSelection.add(pendingShadow);
        pendingShadow = null;
        pendingShadowBuffer = null;
        return true;
    }

    private boolean refreshScene(EntityRenderDispatcher dispatcher) {
        boolean refreshLights = lightDirty;
        boolean updateBounds = !optiFine.present() && (sceneMoved || sceneRemoved);
        if (updateBounds) sceneBounds.clear();
        if (sceneRemoved) remaining.removeIf(entity -> entity.removed);
        sceneMoved = sceneRemoved = lightDirty = false;
        float ageOffset = (float) (clientTick - originTick);
        for (Iterator<Group> groups = sceneGroups.iterator(); groups.hasNext();) {
            Group group = groups.next();
            if (group.mesh.closed) return false;
            int kept = 0;
            for (int i = 0; i < group.cachedItems.size(); i++) {
                ItemEntity entity = group.cachedItems.get(i);
                if (entity.removed) continue;
                ItemStack stack = entity.getItem();
                if (stack.getItem() != group.mesh.stack.getItem() || !supportedStack(stack)
                        || copies(stack.getCount()) != group.copies || group.usesDamage && stack.getDamageValue() != group.damage
                        || !eligible(entity)) return false;
                int offset = i * STRIDE;
                int light = refreshLights || entity.xOld != entity.getX() || entity.yOld != entity.getY() || entity.zOld != entity.getZ()
                        ? dispatcher.getPackedLightCoords(entity, partialTick) : group.data.getInt(offset + 32);
                if (kept != i) group.cachedItems.set(kept, entity);
                group.write(entity, kept * STRIDE, light, origin, entity.getAge() - ageOffset, kept == i);
                if (updateBounds) sceneBounds.include(entity);
                kept++;
            }
            if (kept != group.cachedItems.size()) {
                group.cachedItems.subList(kept, group.cachedItems.size()).clear();
                group.invalidateSelection();
                group.dirty = true;
            }
            if (kept == 0) groups.remove();
            else group.touch();
        }
        sceneTick = clientTick;
        return true;
    }

    private boolean reserveSelection(int count) {
        selectionLastFrame = frame;
        int bytes = count * STRIDE;
        int oldSize = selectionBuffer == null ? 0 : selectionBuffer.capacity();
        if (oldSize >= bytes) return true;
        if (allocatedBytes + bytes - oldSize > MAX_INSTANCE_BYTES) return false;
        int capacity = (int) Math.min(Math.max(bytes, oldSize * 2L), MAX_INSTANCE_BYTES - allocatedBytes + oldSize);
        selectionBuffer = selectionBuffer == null ? MemoryUtil.memAlloc(capacity) : MemoryUtil.memRealloc(selectionBuffer, capacity);
        allocatedBytes += capacity - oldSize;
        return true;
    }

    private Group group(ItemEntity entity) {
        if (!eligible(entity)) return null;
        ItemStack stack = entity.getItem();
        if (!supportedStack(stack)) return null;
        Item item = stack.getItem();
        if (rejected.contains(item) || optiFine.hasCustomRule(item)) return null;
        ItemGroups models = groups.get(item);
        if (models == null) {
            BakedModel base = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(stack);
            if (base.getClass() != SimpleBakedModel.class || base.isCustomRenderer() || !stableOverrides(base, item)) {
                rejected.add(item);
                return null;
            }
            models = new ItemGroups(base);
            groups.put(item, models);
        }
        int damage = models.usesDamage ? stack.getDamageValue() : 0;
        if (models.unsupported.contains(damage)) return null;
        int copies = copies(stack.getCount());
        Group[] variants = models.variants.get(damage);
        Group group = variants == null ? null : variants[copies - 1];
        if (group != null && !group.mesh.closed) return group;
        if (group != null) {
            group.close();
            variants[copies - 1] = null;
            groupCount--;
        }
        if (groupCount >= MAX_GROUPS) return null;
        BakedModel model = models.usesDamage ? models.base.getOverrides().resolve(models.base, stack, level, null) : models.base;
        if (model == null || model.getClass() != SimpleBakedModel.class || model.isCustomRenderer()) {
            models.reject(damage);
            return null;
        }
        boolean direct = true;
        if (item instanceof BlockItem) {
            net.minecraft.world.level.block.Block block = ((BlockItem) item).getBlock();
            direct = !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
        }
        RenderType type = ItemBlockRenderTypes.getRenderType(stack, direct);
        if (type != Sheets.solidBlockSheet() && type != Sheets.cutoutBlockSheet() && type != Sheets.translucentCullBlockSheet()) {
            rejected.add(item);
            return null;
        }
        GpuItemBatch mesh = Carbon.Client.RENDERER.mesh(model, stack);
        if (mesh == null) {
            models.reject(damage);
            return null;
        }
        Layer layer = layers.get(type);
        if (layer == null) { layer = new Layer(type); layers.put(type, layer); }
        if (variants == null) { variants = new Group[5]; models.variants.put(damage, variants); }
        group = new Group(mesh, layer, copies, models.usesDamage, damage);
        variants[copies - 1] = group;
        groupCount++;
        return group;
    }

    private static boolean stableOverrides(BakedModel model, Item item) {
        ItemOverrides overrides = model.getOverrides();
        if (overrides == ItemOverrides.EMPTY) return true;
        if (overrides.getClass() != ItemOverrides.class || item.getMaxDamage() <= 0) return false;
        for (ItemOverride override : overrides.overrides) {
            for (ResourceLocation property : override.predicates.keySet()) {
                if (!property.equals(DAMAGE) && !property.equals(DAMAGED)) return false;
            }
        }
        return true;
    }

    private static boolean eligible(ItemEntity item) {
        return !item.hasCustomName() && !item.shouldShowName() && !item.isGlowing()
                && !item.displayFireAnimation() && !item.isVehicle();
    }

    private static boolean supportedStack(ItemStack stack) {
        if (stack.isEmpty() || stack.hasFoil() || stack.getItem() == Items.TRIDENT) return false;
        if (!stack.hasTag()) return true;
        // Vanilla creates this tag even for a brand-new tool. Damage changes the copy seed for multi-copy stacks.
        CompoundTag tag = stack.getTag();
        return stack.getItem().getMaxDamage() > 0 && tag.size() == 1 && tag.contains("Damage", 3)
                && (stack.getCount() == 1 || stack.getDamageValue() == 0)
                && Registry.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft");
    }

    private static int copies(int count) {
        return count > 48 ? 5 : count > 32 ? 4 : count > 16 ? 3 : count > 1 ? 2 : 1;
    }

    public void flush(MultiBufferSource.BufferSource owner, RenderType type, boolean prepared) {
        if (!active || replaying || used.isEmpty()) return;
        boolean pending = false;
        for (Group group : used) if (group.pending(owner, type)) { pending = true; break; }
        if (!pending) return;
        int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        if (!prepared) type.setupRenderState();
        try {
            if (glGetInteger(GL_CURRENT_PROGRAM) != 0 || Carbon.Client.IRIS.active()) {
                replay(owner, type, prepared);
                return;
            }
            state.apply(program, false);
            glUniformMatrix4fv(glGetUniformLocation(program, "ViewPose"), false, viewPose);
            glUniformMatrix3fv(glGetUniformLocation(program, "ViewNormal"), false, viewNormal);
            glUniform1f(glGetUniformLocation(program, "PartialTick"), partialTick);
            float ageOffset = collectedThisFrame ? (float) (clientTick - originTick)
                    : cacheNativeSelection ? (float) (clientTick - nativeOriginTick) : 0;
            glUniform1f(glGetUniformLocation(program, "AgeOffset"), ageOffset);
            Vec3 base = collectedThisFrame ? origin : cacheNativeSelection ? nativeShadowOrigin : camera;
            glUniform3f(glGetUniformLocation(program, "CameraOffset"),
                    (float) (base.x - camera.x), (float) (base.y - camera.y), (float) (base.z - camera.z));
            for (Group group : used) if (group.pending(owner, type)) group.draw();
        } finally {
            glUseProgram(previousProgram);
            if (!prepared) type.clearRenderState();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, buffer);
        }
    }

    private void replay(MultiBufferSource.BufferSource owner, RenderType type, boolean prepared) {
        BufferBuilder builder = prepared ? new BufferBuilder(256) : null;
        MultiBufferSource target = ignored -> builder != null ? builder : owner.getBuffer(type);
        PoseStack pose = new PoseStack();
        RenderCompat.load(pose.last().pose(), FloatBuffer.wrap(viewPose));
        RenderCompat.load(pose.last().normal(), FloatBuffer.wrap(viewNormal));
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        replaying = true;
        boolean capture = Carbon.Client.RENDERER.capturing;
        Carbon.Client.RENDERER.capturing = true;
        try {
            if (builder != null) builder.begin(type.mode(), type.format());
            for (Group group : used) {
                if (!group.pending(owner, type)) continue;
                for (int i = 0; i < group.items.size(); i++) {
                    ItemEntity item = group.items.get(i);
                    pose.pushPose();
                    pose.translate(Mth.lerp(partialTick, item.xOld, item.getX()) - camera.x,
                            Mth.lerp(partialTick, item.yOld, item.getY()) - camera.y,
                            Mth.lerp(partialTick, item.zOld, item.getZ()) - camera.z);
                    dispatcher.getRenderer(item).render(item, 0, partialTick, pose, target, group.data.getInt(group.recordIndex(i) * STRIDE + 32));
                    pose.popPose();
                    nativeFallbacks++;
                }
                group.drawnFrame = frame;
            }
            if (builder != null) { builder.end(); BufferUploader.end(builder); }
        } finally {
            Carbon.Client.RENDERER.capturing = capture;
            replaying = false;
            if (builder != null) RenderCompat.release(builder);
        }
    }

    public void endWorld() {
        restoreOptiFineCounter();
        active = false;
    }

    public void flushShadows(MultiBufferSource.BufferSource buffers) {
        shadowCaptureOpen = false;
        pendingShadow = null;
        pendingShadowBuffer = null;
        if (!shadowSelection.isEmpty()) {
            if (glGetInteger(GL_CURRENT_PROGRAM) == 0 && !Carbon.Client.IRIS.active()) {
                buffers.endBatch(EntityRenderDispatcher.SHADOW_RENDER_TYPE);
                Vec3 shadowOrigin = collectedThisFrame ? origin : nativeShadowOrigin;
                uploadedBytes += shadows.draw(shadowSelection, level, viewPose, viewNormal, shadowOrigin, camera, shadowPartialTick);
                shadowInstances = shadows.count();
                shadowDrawCalls = shadowInstances == 0 ? 0 : 1;
            } else {
                PoseStack pose = new PoseStack();
                RenderCompat.load(pose.last().pose(), FloatBuffer.wrap(viewPose));
                RenderCompat.load(pose.last().normal(), FloatBuffer.wrap(viewNormal));
                for (ItemEntity item : shadowSelection) {
                    float weight = (float) ((1.0 - camera.distanceToSqr(item.position()) / 256.0) * 0.75F);
                    if (weight <= 0 || item.isInvisible()) continue;
                    pose.pushPose();
                    pose.translate(Mth.lerp(shadowPartialTick, item.xOld, item.getX()) - camera.x,
                            Mth.lerp(shadowPartialTick, item.yOld, item.getY()) - camera.y,
                            Mth.lerp(shadowPartialTick, item.zOld, item.getZ()) - camera.z);
                    EntityRenderDispatcher.renderShadow(pose, buffers, item, weight, shadowPartialTick, level, 0.15F);
                    pose.popPose();
                }
            }
            shadowSelection.clear();
        }
    }

    private void resetScene() {
        sceneReady = false;
        sceneBounds.clear();
        selectedCamera = null;
        sodiumVisibility.clear();
        remaining.clear();
        limitedRemaining.clear();
        remainingHasItems = false;
        overflow.clear();
        optiFineSelection.clear();
        for (Group group : sceneGroups) group.clearItems();
        sceneGroups.clear();
        for (Group group : used) group.clearItems();
        used.clear();
    }

    private void trim() {
        if (frame - selectionLastFrame > 300) releaseSelection();
        for (ItemGroups models : groups.values()) {
            Iterator<Group[]> iterator = models.variants.values().iterator();
            while (iterator.hasNext()) {
                Group[] variants = iterator.next();
                boolean any = false;
                for (int i = 0; i < variants.length; i++) {
                    Group group = variants[i];
                    if (group != null && (group.mesh.closed || frame - group.lastFrame > 300)) {
                        if (!group.cachedItems.isEmpty()) sceneDirty = true;
                        group.close();
                        variants[i] = null;
                        groupCount--;
                    }
                    any |= variants[i] != null;
                }
                if (!any) iterator.remove();
            }
        }
    }

    public void invalidate() {
        if (!RenderSystem.isOnRenderThreadOrInit()) { RenderSystem.recordRenderCall(this::invalidate); return; }
        if (shadows != null) {
            shadows.close();
            shadows = null;
        }
        shadowSelection.clear();
        pendingShadow = null;
        pendingShadowBuffer = null;
        resetScene();
        for (ItemGroups models : groups.values()) {
            for (Group[] variants : models.variants.values()) for (Group group : variants) if (group != null) group.close();
        }
        groupCount = 0;
        groups.clear();
        layers.clear();
        rejected.clear();
        releaseSelection();
        level = null;
        cameraEntity = null;
        if (program != 0) glDeleteProgram(program);
        program = 0;
        failed = active = false;
        sceneDirty = true;
        sceneMoved = sceneRemoved = lightDirty = shadowLightDirty = false;
    }

    private void releaseSelection() {
        if (selectionBuffer == null) return;
        allocatedBytes -= selectionBuffer.capacity();
        MemoryUtil.memFree(selectionBuffer);
        selectionBuffer = null;
    }

    public String statistics() {
        return String.format(Locale.ROOT, "%d instances (%d dropped entities, %d collected), %d instanced draws, %d scene builds/%d reuses, %d instance bytes/frame, %d native fallbacks, %d shadow instances/%d shadow draws",
                instances, entities, collected, drawCalls, sceneBuilds, sceneReuses, uploadedBytes, nativeFallbacks, shadowInstances, shadowDrawCalls);
    }

    private static Option option(String type, String field, String getter, boolean present) {
        if (!present) return null;
        try {
            Object option = Class.forName(type).getField(field).get(null);
            return new Option(option, option.getClass().getMethod(getter));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void storeMatrices(PoseStack pose, float[] matrix, float[] normal) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(16);
            pose.last().pose().store(values);
            values.rewind();
            values.get(matrix);
            values.clear();
            RenderCompat.store(pose.last().normal(), values);
            values.get(normal);
        }
    }

    private static final class Option {
        private final Object option;
        private final Method getter;
        Option(Object option, Method getter) { this.option = option; this.getter = getter; }
        Object read() throws ReflectiveOperationException { return getter.invoke(option); }
        boolean bool(boolean fallback) {
            try { return (Boolean) read(); }
            catch (ReflectiveOperationException exception) { return fallback; }
        }
    }

    private final class ItemGroups {
        final BakedModel base;
        final boolean usesDamage;
        final Map<Integer, Group[]> variants = new HashMap<>();
        final Set<Integer> unsupported = new HashSet<>();

        ItemGroups(BakedModel base) {
            this.base = base;
            usesDamage = base.getOverrides() != ItemOverrides.EMPTY;
        }

        void reject(int damage) {
            if (unsupported.size() < MAX_GROUPS) unsupported.add(damage);
        }
    }

    private final class Layer {
        final RenderType requested;
        MultiBufferSource source;
        MultiBufferSource.BufferSource owner;
        RenderType actual;
        int boundFrame = -1;
        Layer(RenderType requested) { this.requested = requested; }
        boolean bind(MultiBufferSource buffer) {
            if (boundFrame == frame) return source == buffer && owner != null;
            source = buffer;
            boundFrame = frame;
            owner = null;
            VertexConsumer vertices = buffer.getBuffer(requested);
            if (vertices instanceof ItemBatchRenderer.Target) {
                ItemBatchRenderer.Target target = (ItemBatchRenderer.Target) vertices;
                if (Carbon.Client.IRIS.unwrap(target.imo$type()) == requested) {
                    owner = target.imo$source();
                    actual = target.imo$type();
                }
            }
            return owner != null;
        }
    }

    private final class Group {
        final GpuItemBatch mesh;
        final Layer layer;
        final int copies, damage;
        final boolean usesDamage;
        final float[] groundPose = new float[16], groundNormal = new float[9], copyOffsets;
        final float groundHeight;
        final List<ItemEntity> cachedItems = new ArrayList<>();
        final List<ItemEntity> partialItems = new ArrayList<>();
        List<ItemEntity> items = partialItems;
        IntArrayList indices = new IntArrayList(), nextIndices = new IntArrayList();
        final IntArrayList ranges = new IntArrayList();
        ByteBuffer data;
        int capacity, vao, buffer, uploadedRecords, lastFrame, drawnFrame = -1;
        boolean dirty, selectionDirty, partialSelection, compactedBuffer;

        Group(GpuItemBatch mesh, Layer layer, int copies, boolean usesDamage, int damage) {
            this.mesh = mesh;
            this.layer = layer;
            this.copies = copies;
            this.usesDamage = usesDamage;
            this.damage = damage;
            lastFrame = frame;
            PoseStack ground = new PoseStack();
            mesh.model.getTransforms().ground.apply(false, ground);
            ground.translate(-0.5, -0.5, -0.5);
            storeMatrices(ground, groundPose, groundNormal);
            groundHeight = mesh.model.getTransforms().ground.scale.y() * 0.25F;
            float spacing = 0.09375F * mesh.model.getTransforms().ground.scale.z();
            Random random = new Random(Item.getId(mesh.stack.getItem()) + mesh.stack.getDamageValue());
            copyOffsets = new float[copies * 3];
            boolean volume = mesh.model.isGui3d();
            for (int i = 0; i < copies; i++) {
                float spread = volume ? 0.15F : 0.075F;
                if (i != 0) {
                    copyOffsets[i * 3] = (random.nextFloat() * 2 - 1) * spread;
                    copyOffsets[i * 3 + 1] = (random.nextFloat() * 2 - 1) * spread;
                    if (volume) copyOffsets[i * 3 + 2] = (random.nextFloat() * 2 - 1) * spread;
                }
                if (!volume) copyOffsets[i * 3 + 2] = (i - (copies - 1) * 0.5F) * spacing;
            }
        }

        boolean canAppend(int size) {
            if (size < capacity) return true;
            int next = Math.max(64, capacity * 2);
            if (next > 65536 || allocatedBytes + (long) (next - capacity) * STRIDE > MAX_INSTANCE_BYTES) return false;
            data = data == null ? MemoryUtil.memAlloc(next * STRIDE) : MemoryUtil.memRealloc(data, next * STRIDE);
            allocatedBytes += (long) (next - capacity) * STRIDE;
            capacity = next;
            return true;
        }

        void append(ItemEntity item, int light) {
            int index = items.size();
            if (index == 0) used.add(this);
            Vec3 base = cacheNativeSelection ? nativeShadowOrigin : camera;
            float age = cacheNativeSelection ? item.getAge() - (float) (clientTick - nativeOriginTick) : item.getAge();
            write(item, index * STRIDE, light, base, age, cacheNativeSelection && index < uploadedRecords);
            items.add(item);
            entities++;
            instances += copies;
            touch();
        }

        void touch() {
            lastFrame = frame;
            Carbon.Client.RENDERER.touchMesh(mesh);
        }

        void select(EntityRenderDispatcher dispatcher, Frustum frustum, int limit) {
            nextIndices.clear();
            boolean all = true;
            int selected = 0;
            for (int i = 0; i < cachedItems.size() && selected < limit; i++) {
                ItemEntity item = cachedItems.get(i);
                if (dispatcher.shouldRender(item, frustum, camera.x, camera.y, camera.z)) {
                    selected++;
                    if (!all) nextIndices.add(i);
                } else if (all) {
                    for (int j = 0; j < i; j++) nextIndices.add(j);
                    all = false;
                }
            }
            if (all) {
                if (selected == cachedItems.size()) {
                    selectAll();
                    return;
                }
                for (int i = 0; i < selected; i++) nextIndices.add(i);
            }
            if (!partialSelection || !indices.equals(nextIndices)) {
                partialItems.clear();
                for (int i = 0; i < nextIndices.size(); i++) partialItems.add(cachedItems.get(nextIndices.getInt(i)));
                IntArrayList previous = indices;
                indices = nextIndices;
                nextIndices = previous;
                updateDrawRanges();
                selectionDirty = true;
            }
            items = partialItems;
            partialSelection = true;
        }

        void selectAll() {
            partialSelection = false;
            items = cachedItems;
            partialItems.clear();
        }

        private void updateDrawRanges() {
            ranges.clear();
            if (indices.isEmpty() || !GL.getCapabilities().OpenGL42) return;
            int start = indices.getInt(0), end = start + 1;
            for (int i = 1; i < indices.size(); i++) {
                int index = indices.getInt(i);
                if (index == end) {
                    end++;
                    continue;
                }
                ranges.add(start);
                ranges.add(end - start);
                if (ranges.size() == MAX_DRAW_RANGES * 2) {
                    ranges.clear();
                    return;
                }
                start = index;
                end = index + 1;
            }
            ranges.add(start);
            ranges.add(end - start);
        }

        boolean needsCompaction() {
            return partialSelection && ranges.isEmpty();
        }

        int recordIndex(int index) {
            return partialSelection ? indices.getInt(index) : index;
        }

        void write(ItemEntity item, int offset, int light, Vec3 base, float age, boolean compare) {
            float x = (float) (item.getX() - base.x), y = (float) (item.getY() - base.y), z = (float) (item.getZ() - base.z);
            float previousX = item.tickCount == 0 ? x : (float) (item.xOld - base.x);
            float previousY = item.tickCount == 0 ? y : (float) (item.yOld - base.y);
            float previousZ = item.tickCount == 0 ? z : (float) (item.zOld - base.z);
            if (compare && data.getFloat(offset) == previousX && data.getFloat(offset + 4) == previousY && data.getFloat(offset + 8) == previousZ
                    && data.getFloat(offset + 12) == x && data.getFloat(offset + 16) == y && data.getFloat(offset + 20) == z
                    && data.getFloat(offset + 24) == age && data.getFloat(offset + 28) == item.bobOffs && data.getInt(offset + 32) == light) return;
            data.putFloat(offset, previousX);
            data.putFloat(offset + 4, previousY);
            data.putFloat(offset + 8, previousZ);
            data.putFloat(offset + 12, x);
            data.putFloat(offset + 16, y);
            data.putFloat(offset + 20, z);
            data.putFloat(offset + 24, age);
            data.putFloat(offset + 28, item.bobOffs);
            data.putInt(offset + 32, light);
            dirty = true;
        }

        boolean pending(MultiBufferSource.BufferSource owner, RenderType type) {
            return drawnFrame != frame && layer.boundFrame == frame && layer.owner == owner && type.equals(layer.actual) && !items.isEmpty();
        }

        void draw() {
            if (vao == 0) {
                vao = glGenVertexArrays();
                buffer = glGenBuffers();
                glBindVertexArray(vao);
                mesh.bindGeometry();
                glBindBuffer(GL_ARRAY_BUFFER, buffer);
                glVertexAttribPointer(6, 3, GL_FLOAT, false, STRIDE, 0);
                glVertexAttribPointer(7, 3, GL_FLOAT, false, STRIDE, 12);
                glVertexAttribPointer(8, 2, GL_FLOAT, false, STRIDE, 24);
                glVertexAttribIPointer(9, 2, GL_UNSIGNED_SHORT, STRIDE, 32);
                for (int i = 6; i <= 9; i++) { glEnableVertexAttribArray(i); glVertexAttribDivisor(i, copies); }
            } else glBindVertexArray(vao);
            boolean compact = needsCompaction();
            if (dirty || compact != compactedBuffer || compact && selectionDirty) {
                glBindBuffer(GL_ARRAY_BUFFER, buffer);
                int size = (partialSelection && !compact ? cachedItems.size() : items.size()) * STRIDE;
                ByteBuffer source = data;
                if (compact) {
                    source = selectionBuffer;
                    long input = MemoryUtil.memAddress(data), output = MemoryUtil.memAddress(source);
                    for (int i = 0; i < items.size(); i++) {
                        MemoryUtil.memCopy(input + (long) indices.getInt(i) * STRIDE, output + (long) i * STRIDE, STRIDE);
                    }
                }
                glBufferData(GL_ARRAY_BUFFER, RenderCompat.slice(source, 0, size), GL_STREAM_DRAW);
                uploadedRecords = size / STRIDE;
                uploadedBytes += size;
                dirty = false;
                compactedBuffer = compact;
            }
            selectionDirty = false;
            if (mesh.spriteFrame != frame) {
                Carbon.Client.RENDERER.markSprites(mesh.sprites);
                mesh.spriteFrame = frame;
            }
            glUniformMatrix4fv(glGetUniformLocation(program, "GroundPose"), false, groundPose);
            glUniformMatrix3fv(glGetUniformLocation(program, "GroundNormal"), false, groundNormal);
            glUniform1f(glGetUniformLocation(program, "GroundHeight"), groundHeight);
            glUniform3fv(glGetUniformLocation(program, "CopyOffsets[0]"), copyOffsets);
            glUniform1i(glGetUniformLocation(program, "Copies"), copies);
            if (partialSelection && !compact) {
                for (int i = 0; i < ranges.size(); i += 2) {
                    glDrawElementsInstancedBaseInstance(GL_TRIANGLES, mesh.indices, GL_UNSIGNED_INT, 0,
                            ranges.getInt(i + 1) * copies, ranges.getInt(i));
                    drawCalls++;
                    rangeDraws++;
                }
            } else {
                glDrawElementsInstanced(GL_TRIANGLES, mesh.indices, GL_UNSIGNED_INT, 0, items.size() * copies);
                drawCalls++;
            }
            drawnFrame = frame;
            touch();
        }

        void clearItems() {
            cachedItems.clear();
            invalidateSelection();
        }

        void invalidateSelection() {
            partialItems.clear();
            indices.clear();
            nextIndices.clear();
            ranges.clear();
            items = partialItems;
            partialSelection = false;
            selectionDirty = false;
        }

        void close() {
            clearItems();
            if (vao != 0) glDeleteVertexArrays(vao);
            if (buffer != 0) glDeleteBuffers(buffer);
            if (data != null) MemoryUtil.memFree(data);
            allocatedBytes -= (long) capacity * STRIDE;
            data = null;
            capacity = vao = buffer = 0;
        }
    }
}
