package first.carbon.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

// Batch-rendered visible items must keep EntityCulling's client tick eligibility.
public final class EntityCullingCompatibility {
    private Class<?> cullable;
    private MethodHandle setOutOfCamera;

    public EntityCullingCompatibility() {
        try {
            cullable = Class.forName("dev.tr7zw.entityculling.access.Cullable");
            setOutOfCamera = MethodHandles.publicLookup().findVirtual(cullable, "setOutOfCamera",
                    MethodType.methodType(void.class, boolean.class))
                    .asType(MethodType.methodType(void.class, Object.class, boolean.class));
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException exception) {
            LoggerFactory.getLogger("Carbon").warn("EntityCulling visibility bridge unavailable", exception);
        }
    }

    public int markRendered(Iterable<ItemEntity> items, Vec3 camera) {
        int visible = 0;
        var minecraft = Minecraft.getInstance();
        var frustum = RenderCompat.frustum;
        if (frustum != null) {
            for (ItemEntity item : items) {
                if (!item.isRemoved() && item.level() == minecraft.level
                        && minecraft.getEntityRenderDispatcher().shouldRender(item, frustum, camera.x, camera.y, camera.z)
                        ) {
                    visible++;
                    if (setOutOfCamera != null && cullable.isInstance(item)) {
                        try {
                            setOutOfCamera.invokeExact((Object) item, false);
                        } catch (Throwable exception) {
                            setOutOfCamera = null;
                            LoggerFactory.getLogger("Carbon").warn("EntityCulling visibility update unavailable", exception);
                        }
                    }
                }
            }
        }
        return visible;
    }
}
