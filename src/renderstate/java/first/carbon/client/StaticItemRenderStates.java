package first.carbon.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import first.carbon.compat.ItemStateAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

// Vanilla visibility and submission still run. Only unchanged, ordinary item states are retained.
public final class StaticItemRenderStates {
    private static final AtomicInteger environmentChanges = new AtomicInteger();
    private static final boolean nativeSubmission = detectNativeSubmission();
    private static Vec3 cameraPosition;
    private static int environment, skyDarken, generation, epoch, reused, earlyReuses;
    private static boolean shadows, active;

    public static void begin(Vec3 position, int modelGeneration, boolean enabled) {
        reused = 0;
        earlyReuses = 0;
        active = enabled;
        if (enabled) {
            Minecraft minecraft = Minecraft.getInstance();
            int currentEnvironment = environmentChanges.get();
            int currentSky = minecraft.level.getSkyDarken();
            boolean currentShadows = minecraft.options.entityShadows().get();
            if (modelGeneration != generation || currentEnvironment != environment || currentSky != skyDarken
                    || currentShadows != shadows || !position.equals(cameraPosition)) {
                generation = modelGeneration;
                environment = currentEnvironment;
                skyDarken = currentSky;
                shadows = currentShadows;
                cameraPosition = position;
                epoch++;
            }
        }
    }

    public static void end() { active = false; }

    static int reused() { return reused; }

    static int earlyReuses() { return earlyReuses; }

    public static void environmentChanged() { environmentChanges.incrementAndGet(); }

    static boolean requiresNativeSubmission() { return nativeSubmission; }

    private static boolean detectNativeSubmission() {
        FabricLoader loader = FabricLoader.getInstance();
        // Tweakeroo's shouldRender filter has already run at both retained-state hooks.
        return loader.isModLoaded("entityculling")
                || loader.isModLoaded("itemphysic") || loader.isModLoaded("itemphysiclite");
    }

    public static EntityRenderState reuseBeforeDispatcher(Entity entity, float partial) {
        if (!active || nativeSubmission || entity.getClass() != ItemEntity.class) return null;
        ItemEntity item = (ItemEntity) entity;
        var access = (DroppedItemBatchRenderer.EntityCacheAccess) item;
        var cache = access.imo$cache();
        Entry entry = cache.staticState;
        if (entry == null || Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(item).getClass() != ItemEntityRenderer.class) return null;
        if (stationary(item)) {
            ItemStack stack = item.getItem();
            int version = ((ItemBatchRenderer.ComponentVersion) (Object) stack).imo$version();
            if (entry.matches(stack, version, stack.getCount(), item, epoch) && access.imo$eligible(stack, version)) {
                earlyReuses++;
                return reuse(entry, item, partial);
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
            return reuse(entry, item, partial);
        }

        cache.staticState = null;
        EntityRenderState rendered = original.call(renderer, entity, partial);
        if (rendered instanceof ItemEntityRenderState state && !stack.isEmpty() && !stack.hasFoil()
                && state.nameTag == null && !state.displayFireAnimation && !state.isInvisible
                && !state.appearsGlowing() && plainModel(stack, state.item)) {
            components.imo$track();
            cache.staticState = new Entry(stack, version, count, epoch, state);
        }
        return rendered;
    }

    private static boolean stationary(ItemEntity item) {
        return item.tickCount > 0 && item.onGround() && !item.isInvisible() && !item.isPassenger() && !item.isVehicle()
                && item.xOld == item.getX() && item.yOld == item.getY() && item.zOld == item.getZ();
    }

    private static ItemEntityRenderState reuse(Entry entry, ItemEntity item, float partial) {
        ItemEntityRenderState state = entry.state;
        state.ageInTicks = item.tickCount + partial;
        state.bobOffset = item.bobOffs;
        ((ItemStateAccess) state).carbon$bind(item, partial);
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

    record Entry(ItemStack stack, int version, int count, int epoch, ItemEntityRenderState state) {
        boolean matches(ItemStack stack, int version, int count, ItemEntity item, int epoch) {
            return this.stack == stack && this.version == version && this.count == count && this.epoch == epoch
                    && state.x == item.getX() && state.y == item.getY() && state.z == item.getZ();
        }
    }
}
