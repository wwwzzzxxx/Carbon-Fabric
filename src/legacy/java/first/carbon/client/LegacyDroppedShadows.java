package first.carbon.client;

import first.carbon.compat.RenderCompat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.GL42.glDrawArraysInstancedBaseInstance;
import static org.lwjgl.opengl.GL43.glMultiDrawArraysIndirect;

final class LegacyDroppedShadows {
    static final int MAX_ITEMS = 16384;
    private final int stride = 48;
    private final float radius = 0.15F;
    private final int program;
    private final int vao;
    private final int buffer;
    private final int indirectBuffer;
    private final int clipUniform;
    private final boolean clip;
    private final LegacyState state;
    private final List<ItemEntity> selected = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Receiver> receivers = new Long2ObjectOpenHashMap<>();
    private final Receiver empty = new Receiver(null, 0);
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final IdentityHashMap<ItemEntity, Packet> packets = new IdentityHashMap<>();
    private IntArrayList ranges = new IntArrayList(), nextRanges = new IntArrayList();
    private IntBuffer commands;
    private ByteBuffer data;
    private Vec3 cachedOrigin;
    private int count, stored, cachedRecords, uploadedCapacity;
    private boolean moving, selectionDirty = true, rangesDirty;

    LegacyDroppedShadows(int program, LegacyState state) {
        this.program = program;
        this.state = state;
        clip = defaultShadowTexture();
        clipUniform = glGetUniformLocation(program, "ClipShadow");
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        vao = glGenVertexArrays();
        buffer = glGenBuffers();
        indirectBuffer = GL.getCapabilities().OpenGL43 ? glGenBuffers() : 0;
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        for (int i = 0; i < 3; i++) {
            glVertexAttribPointer(i, 4, GL_FLOAT, false, stride, i * 16L);
            glEnableVertexAttribArray(i);
            glVertexAttribDivisor(i, 1);
        }
        glBindVertexArray(oldVao);
        glBindBuffer(GL_ARRAY_BUFFER, oldBuffer);
    }

    long draw(List<ItemEntity> items, ClientLevel level, float[] view, float[] normal, Vec3 origin,
              Vec3 camera, float partialTick) {
        if (!origin.equals(cachedOrigin)) {
            invalidate();
            cachedOrigin = origin;
        }
        boolean same = selected.equals(items);
        boolean rebuild = selectionDirty || moving || !same;
        int firstUpload = stored;
        if (rebuild) {
            if (!same) {
                selected.clear();
                selected.addAll(items);
            }
            if (indirectBuffer != 0) {
                firstUpload = prepareRanges(items, level, origin, partialTick);
            } else {
                stored = 0;
                moving = false;
                for (ItemEntity item : items) add(item, level, origin, partialTick);
                count = stored;
                firstUpload = 0;
            }
            selectionDirty = false;
        }
        if (count == 0) return 0;

        long uploaded = 0;
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        RenderType type = EntityRenderDispatcher.SHADOW_RENDER_TYPE;
        type.setupRenderState();
        try {
            state.apply(program, false);
            glUniform1i(clipUniform, clip ? 1 : 0);
            glUniformMatrix4fv(glGetUniformLocation(program, "ViewPose"), false, view);
            glUniformMatrix3fv(glGetUniformLocation(program, "ViewNormal"), false, normal);
            glUniform3f(glGetUniformLocation(program, "CameraOffset"),
                    (float) (origin.x - camera.x), (float) (origin.y - camera.y), (float) (origin.z - camera.z));
            glBindVertexArray(vao);
            if (indirectBuffer != 0) {
                if (rebuild && stored > firstUpload) {
                    glBindBuffer(GL_ARRAY_BUFFER, buffer);
                    if (uploadedCapacity < data.capacity()) {
                        uploadedCapacity = data.capacity();
                        glBufferData(GL_ARRAY_BUFFER, uploadedCapacity, GL_DYNAMIC_DRAW);
                        firstUpload = 0;
                    }
                    int bytes = (stored - firstUpload) * stride;
                    glBufferSubData(GL_ARRAY_BUFFER, (long) firstUpload * stride,
                            RenderCompat.slice(data, firstUpload * stride, bytes));
                    uploaded += bytes;
                }
                uploaded += drawRanges();
            } else {
                if (rebuild) {
                    glBindBuffer(GL_ARRAY_BUFFER, buffer);
                    glBufferData(GL_ARRAY_BUFFER, RenderCompat.slice(data, 0, count * stride), GL_STREAM_DRAW);
                    uploaded += (long) count * stride;
                }
                glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, count);
            }
        } finally {
            glUseProgram(oldProgram);
            type.clearRenderState();
            glBindVertexArray(oldVao);
            glBindBuffer(GL_ARRAY_BUFFER, oldBuffer);
        }
        return uploaded;
    }

    private int prepareRanges(List<ItemEntity> items, ClientLevel level, Vec3 origin, float partialTick) {
        // An item can touch at most eight receivers. Reclaim old slots before the cache exceeds its budget.
        if (cachedRecords + items.size() * 8 > MAX_ITEMS * 16 || packets.size() > MAX_ITEMS) {
            packets.clear();
            cachedRecords = 0;
        }
        int firstUpload = cachedRecords;
        stored = cachedRecords;
        moving = false;
        for (ItemEntity item : items) {
            if (stationary(item) && !item.isInvisible()) {
                Packet packet = packets.get(item);
                if (packet == null || !packet.position.equals(item.position())) {
                    int first = stored;
                    add(item, level, origin, partialTick);
                    packets.put(item, new Packet(item.position(), first, stored - first));
                }
            }
        }
        cachedRecords = stored;

        count = 0;
        nextRanges.clear();
        for (ItemEntity item : items) {
            if (item.isInvisible()) continue;
            int first, length;
            if (stationary(item)) {
                Packet packet = packets.get(item);
                first = packet.first;
                length = packet.count;
            } else {
                first = stored;
                add(item, level, origin, partialTick);
                length = stored - first;
            }
            if (length > 0) {
                int end = nextRanges.size();
                if (end >= 2 && nextRanges.getInt(end - 2) + nextRanges.getInt(end - 1) == first) {
                    nextRanges.set(end - 1, nextRanges.getInt(end - 1) + length);
                } else {
                    nextRanges.add(first);
                    nextRanges.add(length);
                }
                count += length;
            }
        }
        if (!ranges.equals(nextRanges)) {
            IntArrayList previous = ranges;
            ranges = nextRanges;
            nextRanges = previous;
            rangesDirty = true;
        }
        return firstUpload;
    }

    private long drawRanges() {
        if (ranges.size() == 2) {
            glDrawArraysInstancedBaseInstance(GL_TRIANGLE_STRIP, 0, 4, ranges.getInt(1), ranges.getInt(0));
            return 0;
        }
        long uploaded = 0;
        int previous = glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer);
        try {
            if (rangesDirty) {
                int ints = ranges.size() * 2;
                if (commands == null || commands.capacity() < ints) {
                    commands = commands == null ? MemoryUtil.memAllocInt(ints) : MemoryUtil.memRealloc(commands, ints);
                }
                commands.clear();
                for (int i = 0; i < ranges.size(); i += 2) {
                    commands.put(4).put(ranges.getInt(i + 1)).put(0).put(ranges.getInt(i));
                }
                commands.flip();
                glBufferData(GL_DRAW_INDIRECT_BUFFER, commands, GL_STREAM_DRAW);
                uploaded = (long) ints * Integer.BYTES;
                rangesDirty = false;
            }
            glMultiDrawArraysIndirect(GL_TRIANGLE_STRIP, 0L, ranges.size() / 2, 0);
        } finally {
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, previous);
        }
        return uploaded;
    }

    private static boolean stationary(ItemEntity item) {
        return item.tickCount == 0 || item.xOld == item.getX() && item.yOld == item.getY() && item.zOld == item.getZ();
    }

    private static boolean defaultShadowTexture() {
        try (Resource resource = Minecraft.getInstance().getResourceManager()
                .getResource(new ResourceLocation("textures/misc/shadow.png"))) {
            TextureMetadataSection metadata = resource.getMetadata(TextureMetadataSection.SERIALIZER);
            if (metadata == null || metadata.isBlur() || !metadata.isClamp()) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream input = resource.getInputStream();
            byte[] bytes = new byte[4096];
            for (int read; (read = input.read(bytes)) != -1;) digest.update(bytes, 0, read);
            return Base64.getEncoder().encodeToString(digest.digest())
                    .equals("EjT5v+2j/2o8WtOOxvEYxG170ZvzlGxj/z5LH4WkVP8=");
        } catch (IOException | NoSuchAlgorithmException | RuntimeException ignored) {
            return false;
        }
    }

    private void add(ItemEntity item, ClientLevel level, Vec3 origin, float partialTick) {
        if (item.isInvisible()) return;
        double currentX = item.getX(), currentY = item.getY(), currentZ = item.getZ();
        double x = item.tickCount == 0 ? currentX : Mth.lerp(partialTick, item.xOld, currentX);
        double y = item.tickCount == 0 ? currentY : Mth.lerp(partialTick, item.yOld, currentY);
        double z = item.tickCount == 0 ? currentZ : Mth.lerp(partialTick, item.zOld, currentZ);
        moving |= !stationary(item);
        for (int bz = Mth.floor(z - radius), maxZ = Mth.floor(z + radius); bz <= maxZ; bz++) {
            for (int by = Mth.floor(y - radius), maxY = Mth.floor(y); by <= maxY; by++) {
                for (int bx = Mth.floor(x - radius), maxX = Mth.floor(x + radius); bx <= maxX; bx++) {
                    Receiver receiver = receiver(level, bx, by, bz);
                    if (receiver.bounds == null) continue;
                    int offset = stored++ * stride;
                    if (data == null || offset >= data.capacity()) {
                        data = data == null ? MemoryUtil.memAlloc(64 * stride) : MemoryUtil.memRealloc(data, data.capacity() * 2);
                    }
                    AABB bounds = receiver.bounds;
                    data.putFloat(offset, (float) (bounds.minX - origin.x));
                    data.putFloat(offset + 4, (float) (bounds.minY - origin.y));
                    data.putFloat(offset + 8, (float) (bounds.minZ - origin.z));
                    data.putFloat(offset + 12, (float) (bounds.maxX - origin.x));
                    data.putFloat(offset + 16, (float) (bounds.maxZ - origin.z));
                    data.putFloat(offset + 20, (float) (x - origin.x));
                    data.putFloat(offset + 24, (float) (z - origin.z));
                    data.putFloat(offset + 28, receiver.brightness);
                    data.putFloat(offset + 32, (float) (y - by));
                    data.putFloat(offset + 36, (float) (currentX - origin.x));
                    data.putFloat(offset + 40, (float) (currentY - origin.y));
                    data.putFloat(offset + 44, (float) (currentZ - origin.z));
                }
            }
        }
    }

    private Receiver receiver(ClientLevel level, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Receiver receiver = receivers.get(key);
        if (receiver != null) return receiver;
        if (receivers.size() >= MAX_ITEMS) receivers.clear();
        receiver = empty;
        pos.set(x, y - 1, z);
        BlockState block = level.getBlockState(pos);
        if (block.getRenderShape() != RenderShape.INVISIBLE && block.isCollisionShapeFullBlock(level, pos)) {
            VoxelShape shape = block.getShape(level, pos);
            pos.setY(y);
            if (!shape.isEmpty() && level.getMaxLocalRawBrightness(pos) > 3) {
                receiver = new Receiver(shape.bounds().move(x, y, z), level.getBrightness(pos));
            }
        }
        receivers.put(key, receiver);
        return receiver;
    }

    int count() { return count; }

    void selectionChanged() { selectionDirty = true; }

    void remove(ItemEntity item) {
        packets.remove(item);
        selected.clear();
        selectionDirty = true;
    }

    void invalidate() {
        selected.clear();
        receivers.clear();
        packets.clear();
        ranges.clear();
        count = stored = cachedRecords = 0;
        selectionDirty = true;
    }

    void close() {
        invalidate();
        glDeleteProgram(program);
        glDeleteBuffers(buffer);
        if (indirectBuffer != 0) glDeleteBuffers(indirectBuffer);
        glDeleteVertexArrays(vao);
        if (data != null) MemoryUtil.memFree(data);
        if (commands != null) MemoryUtil.memFree(commands);
    }

    private static final class Packet {
        final Vec3 position;
        final int first, count;

        Packet(Vec3 position, int first, int count) {
            this.position = position;
            this.first = first;
            this.count = count;
        }
    }

    private static final class Receiver {
        final AABB bounds;
        final float brightness;

        Receiver(AABB bounds, float brightness) {
            this.bounds = bounds;
            this.brightness = brightness;
        }
    }
}
