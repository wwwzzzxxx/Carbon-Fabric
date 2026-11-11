package first.carbon.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

// Reuse native visibility for stationary items when their distance and frustum tests still agree.
final class StaticItemVisibility {
    private List<Entity> selected = List.of();
    private final List<Entity> remaining = new ArrayList<>();
    private final List<Entity> scene = new ArrayList<>();
    private final List<Cell> sceneCells = new ArrayList<>();
    private final List<Entity> partialSelection = new ArrayList<>();
    private final List<Entity> partialRemaining = new ArrayList<>();
    private final List<Entity> merged = new ArrayList<>();
    private final Long2ObjectOpenHashMap<BlockPos> sections = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
    private final Matrix4f matrix = new Matrix4f();
    private AABB bounds;
    private Entity cameraEntity;
    private Vec3 cameraPosition;
    private double viewScale, frustumX, frustumY, frustumZ;
    private int entityCount, frameEntityCount;
    private Iterable<Entity> source;
    private Camera camera;
    private Frustum frustum;
    private List<Entity> replayed = List.of();
    private boolean fixedView;

    Iterable<Entity> select(Iterable<Entity> entities, Camera camera, Frustum frustum,
                            LevelRenderer renderer, int entityCount) {
        source = entities;
        this.camera = camera;
        this.frustum = frustum;
        frameEntityCount = entityCount;
        if (bounds == null || this.entityCount != entityCount || cameraEntity != camera.getEntity()
                || viewScale != Entity.getViewScale() || !camera.getPosition().equals(cameraPosition)) {
            return entities;
        }
        boolean sameFrustum = frustum.getCamX() == frustumX && frustum.getCamY() == frustumY
                && frustum.getCamZ() == frustumZ && frustum.matrix.equals(matrix);
        int inside = cells.size();
        if (!sameFrustum) {
            if (inside > 128 || !frustum.isVisible(bounds)) return entities;
            inside = 0;
            for (Cell cell : cells.values()) {
                cell.visible = cell.isInside(frustum);
                if (cell.visible) inside++;
            }
            if (inside == 0) return entities;
        }
        for (BlockPos position : sections.values()) {
            if (!renderer.isSectionCompiled(position)) return entities;
        }
        if (inside == cells.size()) {
            fixedView = sameFrustum;
            replayed = selected;
            return remaining;
        }
        for (int index = 0; index < scene.size(); index++) {
            Cell cell = sceneCells.get(index);
            (cell != null && cell.visible ? partialSelection : partialRemaining).add(scene.get(index));
        }
        replayed = partialSelection;
        return partialRemaining;
    }

    boolean complete(List<Entity> visible, int itemLimit) {
        if (replayed.isEmpty()) return false;
        if (visible.isEmpty()) {
            visible.addAll(itemLimit < 0 ? replayed : replayed.subList(0, Math.min(itemLimit, replayed.size())));
        } else {
            // Merge the native edge selection and the cached selection in traversal order.
            int item = 0;
            int other = 0;
            for (Entity entity : scene) {
                if (item < replayed.size() && entity == replayed.get(item)) {
                    merged.add(entity);
                    item++;
                } else if (other < visible.size() && entity == visible.get(other)) {
                    merged.add(entity);
                    other++;
                }
            }
            while (other < visible.size()) merged.add(visible.get(other++));
            visible.clear();
            for (Entity entity : merged) visible.add(entity);
            merged.clear();
        }
        replayed = List.of();
        partialSelection.clear();
        partialRemaining.clear();
        return true;
    }

    int fixedSelectionCount(List<Entity> packet) {
        return fixedView && replayed == packet ? packet.size() : 0;
    }

    int replayedCount() {
        return replayed.size();
    }

    List<Entity> selection() {
        return selected;
    }

    List<Entity> remember(List<Entity> visible) {
        if (source == null || visible.size() < 256) return null;
        boolean same = entityCount == frameEntityCount && selected.size() == visible.size();
        for (int index = 0; same && index < visible.size(); index++) {
            same = selected.get(index) == visible.get(index);
        }
        if (same) {
            rememberView();
            return selected;
        }
        discard();
        Cell combined = new Cell();
        int index = 0;
        for (Entity entity : source) {
            Cell cell = null;
            if (index < visible.size() && entity == visible.get(index)) {
                AABB box = entity.getBoundingBox();
                if (entity == camera.getEntity() || !Double.isFinite(box.getSize())) {
                    discard();
                    return null;
                }
                combined.include(box.minX, box.minY, box.minZ);
                combined.include(box.maxX, box.maxY, box.maxZ);
                BlockPos position = entity.blockPosition();
                long cellKey = BlockPos.asLong(position.getX() >> 2, position.getY() >> 2, position.getZ() >> 2);
                cell = cells.computeIfAbsent(cellKey, ignored -> new Cell());
                // A cell containing all item centers proves that each inflated item box is visible.
                cell.include((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
                if (!entity.level().isOutsideBuildHeight(position.getY())) {
                    sections.putIfAbsent(SectionPos.asLong(position), position);
                }
                index++;
            } else {
                remaining.add(entity);
            }
            scene.add(entity);
            sceneCells.add(cell);
        }
        if (index != visible.size()) {
            discard();
            return null;
        }
        selected = List.copyOf(visible);
        bounds = new AABB(combined.minX, combined.minY, combined.minZ,
                combined.maxX, combined.maxY, combined.maxZ).inflate(0.5);
        entityCount = frameEntityCount;
        rememberView();
        return selected;
    }

    private void rememberView() {
        cameraPosition = camera.getPosition();
        cameraEntity = camera.getEntity();
        viewScale = Entity.getViewScale();
        matrix.set(frustum.matrix);
        frustumX = frustum.getCamX();
        frustumY = frustum.getCamY();
        frustumZ = frustum.getCamZ();
    }

    void discard() {
        bounds = null;
        cameraEntity = null;
        selected = List.of();
        remaining.clear();
        scene.clear();
        sceneCells.clear();
        cells.clear();
        sections.clear();
    }

    void endFrame() {
        source = null;
        camera = null;
        frustum = null;
        fixedView = false;
        replayed = List.of();
        partialSelection.clear();
        partialRemaining.clear();
    }

    private static final class Cell {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        boolean visible;

        void include(double x, double y, double z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        boolean isInside(Frustum frustum) {
            return frustum.cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ) == FrustumIntersection.INSIDE;
        }
    }
}
