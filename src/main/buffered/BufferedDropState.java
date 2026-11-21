package first.carbon.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.client.BufferedDropPoses;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Reads the render state that the already loaded physics mod populated for this frame. */
public final class BufferedDropState {
    private static final Inputs INPUTS = load();

    private BufferedDropState() {}

    public static boolean render(BufferedDropPoses poses, ItemEntityRenderState state, ItemEntity item,
                                 PoseStack pose, MultiBufferSource buffers, int light, Object model,
                                 Object layer, Object transform, Runnable original) {
        if (INPUTS == null) return false;
        int style;
        float pitch;
        float yaw;
        try {
            if (INPUTS.skip != null && (boolean) INPUTS.skip.invokeExact(state)) return false;
            style = (boolean) INPUTS.offset.invokeExact(state) ? 2 : 1;
            if (INPUTS.block != null && (boolean) INPUTS.block.invokeExact(state)) style += 2;
            pitch = (float) INPUTS.pitch.invokeExact(state);
            yaw = (float) INPUTS.yaw.invokeExact(state);
        } catch (Throwable unavailable) {
            return false;
        }
        poses.render(item, pose, buffers, light, model, layer, transform, style, pitch, yaw, original);
        return true;
    }

    private static Inputs load() {
        boolean full = "itemphysic".equals(BufferedDropProvider.provider());
        String name = full ? "team.creative.itemphysic.client.ItemEntityRenderStateExtender"
                : "team.creative.itemphysiclite.ItemEntityRenderStateExtender";
        try {
            Class<?> type = Class.forName(name, false, BufferedDropState.class.getClassLoader());
            return new Inputs(full ? getter(type, "skipRendering", boolean.class) : null,
                    getter(type, "hasAdditionalOffset", boolean.class),
                    optionalBlock(type), getter(type, "getXRot", float.class), getter(type, "getYRot", float.class));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    private static MethodHandle optionalBlock(Class<?> type) throws IllegalAccessException {
        try {
            return getter(type, "isBlock", boolean.class);
        } catch (NoSuchMethodException unavailable) {
            return null;
        }
    }

    private static MethodHandle getter(Class<?> type, String method, Class<?> result)
            throws NoSuchMethodException, IllegalAccessException {
        return MethodHandles.publicLookup().findVirtual(type, method, MethodType.methodType(result))
                .asType(MethodType.methodType(result, ItemEntityRenderState.class));
    }

    private record Inputs(MethodHandle skip, MethodHandle offset, MethodHandle block, MethodHandle pitch, MethodHandle yaw) {}
}
