package first.carbon.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import first.carbon.Carbon;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow private int visibleEntityCount;

    @Inject(method="renderLevel",at=@At("HEAD"))
    private void carbon$begin(GraphicsResourceAllocator allocator,DeltaTracker tracker,boolean outline,Camera camera,GameRenderer renderer,Matrix4f view,Matrix4f projection,CallbackInfo ci) {
        Carbon.Client.RENDERER.beginWorld(camera,first.carbon.compat.RenderCompat.matrix(view),first.carbon.compat.RenderCompat.matrix(projection));
        Carbon.Client.DROPPED_RENDERER.beginFrame(camera,first.carbon.compat.RenderCompat.matrix(view),first.carbon.compat.RenderCompat.matrix(projection),tracker.getGameTimeDeltaPartialTick(false));
    }
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;collectVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z"))
    private boolean carbon$chooseItemLimit(LevelRenderer renderer, Camera camera, Frustum frustum, List<Entity> visible,
                                          Operation<Boolean> original) {
        return Carbon.Client.DROPPED_RENDERER.selectWithItemLimit(() -> original.call(renderer, camera, frustum, visible));
    }

    @WrapOperation(method = "collectVisibleEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
    private Iterable<Entity> carbon$selectStaticItems(ClientLevel level, Operation<Iterable<Entity>> original,
                                                     @Local(argsOnly = true) Camera camera,
                                                     @Local(argsOnly = true) Frustum frustum) {
        return Carbon.Client.DROPPED_RENDERER.selectVisibleItems(original.call(level), camera, frustum);
    }

    @ModifyArg(method = "collectVisibleEntities", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"), index = 0)
    private Object carbon$countVisibleItem(Object entity) {
        return Carbon.Client.DROPPED_RENDERER.countVisibleItem(entity);
    }

    @Inject(method = "collectVisibleEntities", at = @At("RETURN"))
    private void carbon$completeSelection(Camera camera, Frustum frustum, List<Entity> visible, CallbackInfoReturnable<Boolean> ci) {
        Carbon.Client.DROPPED_RENDERER.completeVisibleItems(visible);
    }

    @Inject(method = "renderLevel", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleEntityCount:I", opcode = 181, shift = At.Shift.AFTER))
    private void carbon$countRestored(CallbackInfo ci) {
        visibleEntityCount += Carbon.Client.DROPPED_RENDERER.visibleCollected;
    }

    @ModifyVariable(method = "renderEntities", at = @At("HEAD"), argsOnly = true)
    private List<Entity> carbon$reuseStaticItems(List<Entity> visible) {
        return Carbon.Client.DROPPED_RENDERER.reuseVisibleItems(visible);
    }

    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void carbon$flush(CallbackInfo ci) {
        var renderer = Carbon.Client.DROPPED_RENDERER;
        renderer.flush();
        renderer.finishVisibleItems();
        Carbon.Client.RENDERER.flushShadows();
    }
    @Inject(method="renderLevel",at=@At("RETURN"))
    private void carbon$end(CallbackInfo ci) { Carbon.Client.DROPPED_RENDERER.flush();Carbon.Client.DROPPED_RENDERER.endFrame();Carbon.Client.RENDERER.endWorld(); }
}
