package first.carbon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.compat.BufferedDropProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Retains the instance written by an external renderer using Carbon's buffered backend. */
public final class BufferedDropPoses {
    private static final int STRIDE = 128;

    private final Map<ItemEntity, Entry> entries = new WeakHashMap<>();
    private final Replay replay;
    private final Function<MultiBufferSource, MultiBufferSource> owner;
    private Entry collecting;
    private MultiBufferSource collectSource;
    private boolean collectingDraw;
    private boolean active;
    private int reuses;

    BufferedDropPoses(Replay replay) {
        this(replay, source -> source.getClass() == MultiBufferSource.BufferSource.class ? source : null);
    }

    BufferedDropPoses(Replay replay, Function<MultiBufferSource, MultiBufferSource> owner) {
        this.replay = replay;
        this.owner = owner;
    }

    public void render(ItemEntity item, PoseStack pose, MultiBufferSource buffers, int light, Object model,
                       Object shape, Object surroundings, int style, float pitch, float yaw, Runnable original) {
        MultiBufferSource captureOwner = owner.apply(buffers);
        if (collecting != null || captureOwner == null) {
            // If another model render occurs inside the capture, keep its original dispatch.
            if (collecting != null) {
                collectingDraw = true;
                collecting.batch = null;
            }
            original.run();
            return;
        }

        ItemStack stack = item.getItem();
        Entry entry = entries.get(item);
        PoseStack.Pose base = pose.last();
        if (entry != null && entry.matches(item, stack, base, light, model, shape, surroundings, style, pitch, yaw)
                && replay.append(entry, buffers)) {
            reuses++;
            return;
        }

        if (entry == null) entry = new Entry();
        entry.prepare(item, stack, base, light, model, shape, surroundings, style, pitch, yaw);
        collecting = entry;
        collectSource = captureOwner;
        collectingDraw = false;
        try {
            original.run();
            // Some releases settle the pitch inside render(). Let that mutation run to completion.
            if (entry.batch != null && pitch(item) == entry.entityPitch && yaw(item) == entry.entityYaw) {
                entries.put(item, entry);
            } else {
                entries.remove(item);
            }
        } finally {
            collecting = null;
            collectSource = null;
        }
    }

    public static boolean stationary(ItemEntity item) {
        return item.getClass() == ItemEntity.class && item.tickCount > 0 && item.getAge() > 0 && item.isOnGround()
                && item.xOld == item.getX() && item.yOld == item.getY() && item.zOld == item.getZ()
                && !item.hasCustomName() && !item.shouldShowName() && !item.isInvisible()
                && !item.getItem().isEmpty() && item.getItem().getCount() == 1 && !item.getItem().hasFoil()
                && !Minecraft.getInstance().shouldEntityAppearGlowing(item);
    }

    public static float pitch(ItemEntity item) { return item.getXRot(); }
    public static float yaw(ItemEntity item) { return item.getYRot(); }

    void capture(GpuItemBatch batch, Object model, RenderType layer, MultiBufferSource owner,
                 ByteBuffer data, int count, boolean single) {
        if (collecting == null) return;
        if (collectingDraw || !single || owner != collectSource || !Objects.equals(collecting.model, model)) {
            collecting.batch = null;
        } else {
            collecting.layer = layer;
            collecting.batch = batch;
            ByteBuffer copy = data.duplicate();
            copy.position((count - 1) * STRIDE);
            copy.get(collecting.data);
        }
        collectingDraw = true;
    }

    void beginFrame() {
        reuses = 0;
        active = BufferedDropProvider.physicalStyle();
    }

    boolean active() { return active; }
    int reuses() { return reuses; }
    void clear() { entries.clear(); }

    interface Replay {
        boolean append(Entry entry, MultiBufferSource buffers);
    }

    static final class Entry {
        final byte[] data = new byte[STRIDE];
        GpuItemBatch batch;
        RenderType layer;
        private ItemStack stack;
        private Object pose, normal, model, shape, surroundings;
        private int tick, light, style;
        private float pitch, yaw, entityPitch, entityYaw;

        boolean matches(ItemEntity item, ItemStack stack, PoseStack.Pose base, int light, Object model,
                        Object shape, Object surroundings, int style, float pitch, float yaw) {
            return batch != null && !batch.closed && this.tick == item.tickCount && this.light == light
                    && this.style == style && this.pitch == pitch && this.yaw == yaw && this.model == model
                    && entityPitch == pitch(item) && entityYaw == yaw(item)
                    && Objects.equals(this.shape, shape) && Objects.equals(this.surroundings, surroundings)
                    && ItemStack.matches(this.stack, stack) && sameBase(base);
        }

        void prepare(ItemEntity item, ItemStack stack, PoseStack.Pose base, int light, Object model,
                     Object shape, Object surroundings, int style, float pitch, float yaw) {
            batch = null;
            tick = item.tickCount;
            this.light = light;
            this.style = style;
            this.pitch = pitch;
            this.yaw = yaw;
            entityPitch = pitch(item);
            entityYaw = yaw(item);
            this.model = model;
            this.shape = shape;
            this.surroundings = surroundings;
            if (this.stack == null || !ItemStack.matches(this.stack, stack)) this.stack = stack.copy();
            if (!sameBase(base)) {
                pose = base.pose().copy();
                normal = base.normal().copy();
            }
        }

        private boolean sameBase(PoseStack.Pose base) {
            return base.pose().equals(pose) && base.normal().equals(normal);
        }
    }
}
