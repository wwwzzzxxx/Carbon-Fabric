package first.carbon.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import first.carbon.compat.ItemStateAccess;
import first.carbon.compat.ItemEntityCompatibility;
import first.carbon.compat.TweakerooVisibility;
import first.carbon.mixin.ItemFrustumAccess;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

// Retain ordinary item states and the positive visibility tests for an unchanged view.
public final class StaticItemRenderStates {
    private static final AtomicInteger environmentChanges = new AtomicInteger();
    private static final boolean entityCulling = FabricLoader.getInstance().isModLoaded("entityculling");
    private static final boolean nativeSubmission = detectNativeSubmission();
    private static int externalMode;
    private static Vec3 cameraPosition;
    private static int environment, skyDarken, generation, epoch, reused, earlyReuses;
    private static boolean shadows, active;
    private static int clientTick;
    private static long extractionPass;
    private static final Matrix4f frustumMatrix = new Matrix4f();
    private static final Long2ByteOpenHashMap sections = new Long2ByteOpenHashMap();
    private static long lastSection;
    private static byte lastSectionVisibility;
    private static Vec3 visibilityCamera;
    private static double frustumX, frustumY, frustumZ, visibilityScale;
    private static Frustum frameFrustum;
    private static int visibilityEpoch;
    private static boolean retainVisibility;
    private static VisibleBatch visibleBatch;
    private static int lastCompletedView = -1;

    public static void begin(Vec3 position, int modelGeneration, boolean enabled) {
        reused = 0;
        earlyReuses = 0;
        int currentMode = enabled ? ExternalDropRenderState.cacheMode() : -1;
        active = currentMode >= 0;
        retainVisibility = active && !nativeSubmission && !ItemEntityCompatibility.hasExternalDropTransforms()
                && !TweakerooVisibility.requiresNativeSelection();
        frameFrustum = null;
        sections.clear();
        lastSectionVisibility = 0;
        extractionPass++;
        if (!retainVisibility) visibleBatch = null;
        if (active) {
            Minecraft minecraft = Minecraft.getInstance();
            int currentEnvironment = environmentChanges.get();
            int currentSky = minecraft.level.getSkyDarken();
            boolean currentShadows = minecraft.options.entityShadows().get();
            if (modelGeneration != generation || currentEnvironment != environment || currentSky != skyDarken
                    || currentShadows != shadows || currentMode != externalMode || !position.equals(cameraPosition)) {
                externalMode = currentMode;
                generation = modelGeneration;
                environment = currentEnvironment;
                skyDarken = currentSky;
                shadows = currentShadows;
                cameraPosition = position;
                epoch++;
            }
        }
    }

    public static void clearVisibleItems() {
        visibleBatch = null;
        lastCompletedView = -1;
    }

    public static void end() {
        active = false;
        retainVisibility = false;
        frameFrustum = null;
    }

    public static boolean replayVisibleItems(Frustum frustum, Entity cameraEntity, boolean detached, DeltaTracker tracker,
                                             List<EntityRenderState> output, Predicate<BlockPos> sectionVisible) {
        VisibleBatch batch = visibleBatch;
        // A miss leaves the destination empty and lets the original extraction refresh the batch.
        visibleBatch = null;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (!retainVisibility || batch == null || batch.level != level || batch.epoch != epoch
                || !validCamera(minecraft, cameraEntity, detached) || !output.isEmpty()
                || level.getEntityCount() != batch.entityCount || !level.tickRateManager().runsNormally()) return false;

        double viewScale = Mth.clamp(minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5)
                * minecraft.options.entityDistanceScaling().get();
        if (viewScale != Entity.getViewScale()
                || !visibilityView(frustum, cameraPosition.x, cameraPosition.y, cameraPosition.z)
                || visibilityEpoch != batch.view) return false;
        for (BlockPos section : batch.sections) {
            if (!level.isOutsideBuildHeight(section.getY()) && !sectionVisible.test(section)) return false;
        }
        if (!replayBatchItems(batch, level.entitiesForRendering(), cameraEntity,
                tracker.getGameTimeDeltaPartialTick(true))) {
            reused = 0;
            return false;
        }

        output.addAll(batch.states);
        earlyReuses = batch.items.length;
        visibleBatch = batch;
        return true;
    }

    private static boolean replayBatchItems(VisibleBatch batch, Iterable<Entity> entities, Entity cameraEntity, float partial) {
        int index = 0;
        for (Entity entity : entities) {
            if (entity == cameraEntity) continue;
            if (index == batch.items.length || entity != batch.items[index]) return false;
            ItemEntity item = batch.items[index];
            Entry entry = batch.entries[index++];
            var access = (DroppedItemBatchRenderer.EntityCacheAccess) item;
            if (access.imo$cache().staticState != entry || entry.visiblePosition != item.position()
                    || entry.visibleBounds != item.getBoundingBox() || !stationary(item)) return false;
            ItemStack stack = item.getItem();
            int version = ((ItemBatchRenderer.ComponentVersion) (Object) stack).imo$version();
            if (!entry.matches(stack, version, stack.getCount(), item, epoch) || !access.imo$eligible(stack, version)) return false;
            ItemEntityRenderState state = reuse(entry, item, partial);
            if (state == null || state.appearsGlowing()) return false;
        }
        return index == batch.items.length;
    }

    public static void rememberVisibleItems(Entity cameraEntity, boolean detached, List<EntityRenderState> output) {
        if (visibleBatch != null) return;
        boolean steadyView = lastCompletedView == visibilityEpoch;
        lastCompletedView = visibilityEpoch;
        int count = output.size();
        Minecraft minecraft = Minecraft.getInstance();
        if (!retainVisibility || !steadyView || count < 64 || earlyReuses != count
                || !validCamera(minecraft, cameraEntity, detached)) return;
        ClientLevel level = minecraft.level;
        int entityCount = level.getEntityCount();
        if (entityCount != count && entityCount != count + 1) return;

        var items = new ItemEntity[count];
        var entries = new Entry[count];
        var sections = new ArrayList<BlockPos>();
        var sectionKeys = new LongOpenHashSet();
        for (int index = 0; index < count; index++) {
            EntityRenderState state = output.get(index);
            if (!(state instanceof ItemEntityRenderState) || state.appearsGlowing()) return;
            ItemEntity item = ((ItemStateAccess) state).carbon$entity();
            if (item == null || item.getClass() != ItemEntity.class) return;
            Entry entry = ((DroppedItemBatchRenderer.EntityCacheAccess) item).imo$cache().staticState;
            if (entry == null || entry.state != state || entry.epoch != epoch || entry.visibleEpoch != visibilityEpoch
                    || entry.visiblePosition != item.position() || entry.visibleBounds != item.getBoundingBox()) return;
            items[index] = item;
            entries[index] = entry;
            BlockPos position = item.blockPosition();
            if (sectionKeys.add(SectionPos.asLong(position))) sections.add(position.immutable());
        }
        visibleBatch = new VisibleBatch(level, epoch, visibilityEpoch, entityCount, items, entries, List.copyOf(output), sections);
    }

    private static boolean validCamera(Minecraft minecraft, Entity entity, boolean detached) {
        return minecraft.player != null && entity == minecraft.player && !detached && !minecraft.player.isSleeping()
                && !minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
    }

    private record VisibleBatch(ClientLevel level, int epoch, int view, int entityCount, ItemEntity[] items, Entry[] entries,
                                List<EntityRenderState> states, List<BlockPos> sections) {}

    public static boolean canReuseVisibility() {
        return retainVisibility;
    }

    public static byte sectionVisibility(BlockPos position) {
        long section = SectionPos.asLong(position);
        if (lastSectionVisibility == 0 || section != lastSection) {
            lastSection = section;
            lastSectionVisibility = sections.get(section);
        }
        return lastSectionVisibility;
    }

    public static boolean rememberSectionVisibility(BlockPos position, boolean visible) {
        lastSection = SectionPos.asLong(position);
        lastSectionVisibility = (byte) (visible ? 1 : -1);
        sections.put(lastSection, lastSectionVisibility);
        return visible;
    }

    public static boolean shouldRender(EntityRenderer<?, ?> renderer, Entity entity, Frustum frustum,
                                       double x, double y, double z, float partial, Operation<Boolean> original) {
        if (retainVisibility && entity.getClass() == ItemEntity.class && renderer.getClass() == ItemEntityRenderer.class) {
            ItemEntity item = (ItemEntity) entity;
            Entry entry = ((DroppedItemBatchRenderer.EntityCacheAccess) item).imo$cache().staticState;
            if (entry != null && entry.epoch == epoch && stationaryPosition(item) && visibilityView(frustum, x, y, z)) {
                entry.rendererSeen = extractionPass;
                AABB bounds = item.getBoundingBox();
                if (entry.visibleEpoch == visibilityEpoch && entry.visibleBounds == bounds
                        && entry.visiblePosition == item.position()) return true;
                if (nativeVisibility(renderer, entity, frustum, x, y, z, partial, original)) {
                    entry.visibleEpoch = visibilityEpoch;
                    entry.visibleBounds = bounds;
                    entry.visiblePosition = item.position();
                    return true;
                }
                return false;
            }
        }
        return nativeVisibility(renderer, entity, frustum, x, y, z, partial, original);
    }

    private static boolean visibilityView(Frustum frustum, double x, double y, double z) {
        if (frameFrustum == null) {
            if (cameraPosition.x != x || cameraPosition.y != y || cameraPosition.z != z) return false;
            double scale = Entity.getViewScale();
            var matrix = ((ItemFrustumAccess) frustum).carbon$matrix();
            if (!cameraPosition.equals(visibilityCamera) || visibilityScale != scale || !frustumMatrix.equals(matrix)
                    || frustumX != frustum.getCamX() || frustumY != frustum.getCamY() || frustumZ != frustum.getCamZ()) {
                visibilityCamera = cameraPosition;
                visibilityScale = scale;
                frustumMatrix.set(matrix);
                frustumX = frustum.getCamX();
                frustumY = frustum.getCamY();
                frustumZ = frustum.getCamZ();
                visibilityEpoch++;
            }
            frameFrustum = frustum;
        }
        return frameFrustum == frustum;
    }

    private static boolean nativeVisibility(EntityRenderer<?, ?> renderer, Entity entity, Frustum frustum,
                                            double x, double y, double z, float partial, Operation<Boolean> original) {
        return original.call(renderer, entity, frustum, x, y, z);
    }

    static int reused() { return reused; }

    static int earlyReuses() { return earlyReuses; }

    public static int clientTick() { return clientTick; }

    public static void advanceClientTick() { clientTick++; }

    public static void environmentChanged() { environmentChanges.incrementAndGet(); }

    static boolean requiresNativeSubmission() { return nativeSubmission; }

    private static boolean detectNativeSubmission() {
        FabricLoader loader = FabricLoader.getInstance();
        // Tweakeroo's shouldRender filter has already run at both retained-state hooks.
        return entityCulling
                || loader.isModLoaded("itemphysic") || loader.isModLoaded("itemphysiclite");
    }

    public static EntityRenderState reuseBeforeDispatcher(Entity entity, float partial) {
        if (!active || entityCulling || entity.getClass() != ItemEntity.class) return null;
        ItemEntity item = (ItemEntity) entity;
        var access = (DroppedItemBatchRenderer.EntityCacheAccess) item;
        var cache = access.imo$cache();
        Entry entry = cache.staticState;
        if (entry == null || (entry.rendererSeen != extractionPass
                && Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(item).getClass() != ItemEntityRenderer.class)) return null;
        if (stationary(item)) {
            ItemStack stack = item.getItem();
            int version = ((ItemBatchRenderer.ComponentVersion) (Object) stack).imo$version();
            if (entry.matches(stack, version, stack.getCount(), item, epoch) && access.imo$eligible(stack, version)) {
                ItemEntityRenderState reusedState = reuse(entry, item, partial);
                if (reusedState != null) {
                    earlyReuses++;
                    return reusedState;
                }
            }
        }
        cache.staticState = null;
        return null;
    }

    public static EntityRenderState extract(EntityRenderer<?, ?> renderer, Entity entity, float partial,
                                            Operation<EntityRenderState> original) {
        if (!active || entity.getClass() != ItemEntity.class || renderer.getClass() != ItemEntityRenderer.class) {
            return original.call(renderer, entity, partial);
        }
        ItemEntity item = (ItemEntity) entity;
        var access = (DroppedItemBatchRenderer.EntityCacheAccess) item;
        var cache = access.imo$cache();
        if (!stationary(item)) {
            cache.staticState = null;
            return original.call(renderer, entity, partial);
        }

        ItemStack stack = item.getItem();
        var components = (ItemBatchRenderer.ComponentVersion) (Object) stack;
        int version = components.imo$version();
        int count = stack.getCount();
        if (!access.imo$eligible(stack, version)) {
            cache.staticState = null;
            return original.call(renderer, entity, partial);
        }
        Entry entry = cache.staticState;
        if (entry != null && entry.matches(stack, version, count, item, epoch)) {
            ItemEntityRenderState reusedState = reuse(entry, item, partial);
            if (reusedState != null) return reusedState;
        }

        cache.staticState = null;
        EntityRenderState rendered = original.call(renderer, entity, partial);
        if (rendered instanceof ItemEntityRenderState state && !stack.isEmpty() && !stack.hasFoil()
                && state.nameTag == null && !state.displayFireAnimation && !state.isInvisible
                && plainModel(stack, state.item)) {
            components.imo$track();
            cache.staticState = new Entry(stack, version, count, epoch, state);
        }
        return rendered;
    }

    private static boolean stationary(ItemEntity item) {
        return item.tickCount > 0 && item.onGround() && !item.isInvisible() && !item.isPassenger() && !item.isVehicle()
                && stationaryPosition(item);
    }

    private static boolean stationaryPosition(ItemEntity item) {
        return item.xOld == item.getX() && item.yOld == item.getY() && item.zOld == item.getZ();
    }

    private static ItemEntityRenderState reuse(Entry entry, ItemEntity item, float partial) {
        ItemEntityRenderState state = entry.state;
        state.ageInTicks = item.tickCount + partial;
        state.bobOffset = item.bobOffs;
        state.outlineColor = Minecraft.getInstance().shouldEntityAppearGlowing(item) ? ARGB.opaque(item.getTeamColor()) : 0;
        ((ItemStateAccess) state).carbon$bind(item, partial);
        if (!ExternalDropRenderState.refresh(state, item)) return null;
        reused++;
        return state;
    }

    private static boolean plainModel(ItemStack stack, ItemStackRenderState state) {
        if (state.activeLayerCount != 1 || state.isAnimated()) return false;
        var layer = state.layers[0];
        if (layer.foilType != ItemStackRenderState.FoilType.NONE || layer.specialRenderer != null) return false;
        var id = stack.get(DataComponents.ITEM_MODEL);
        if (id == null) return false;
        var model = Minecraft.getInstance().getModelManager().getItemModel(id);
        return model != null && model.getClass() == BlockModelWrapper.class
                && ((BlockModelWrapper) model).tints.isEmpty() && ((BlockModelWrapper) model).extents == layer.extents;
    }

    static final class Entry {
        private final ItemStack stack;
        private final int version, count, epoch;
        private final ItemEntityRenderState state;
        private long rendererSeen;
        private int visibleEpoch;
        private AABB visibleBounds;
        private Vec3 visiblePosition;

        Entry(ItemStack stack, int version, int count, int epoch, ItemEntityRenderState state) {
            this.stack = stack;
            this.version = version;
            this.count = count;
            this.epoch = epoch;
            this.state = state;
        }

        ItemEntityRenderState state() { return state; }
        int count() { return count; }

        boolean matches(ItemStack stack, int version, int count, ItemEntity item, int epoch) {
            return this.stack == stack && this.version == version && this.count == count && this.epoch == epoch
                    && state.x == item.getX() && state.y == item.getY() && state.z == item.getZ();
        }
    }
}
