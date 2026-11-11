package first.carbon.mixin;
import first.carbon.Carbon;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.math.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow private int visibleEntityCount;
    @Redirect(method="collectVisibleEntities",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
    private Iterable<Entity> carbon$entities(ClientLevel level) {
        var renderer=Carbon.Client.DROPPED_RENDERER;renderer.collecting=Carbon.CONFIG.optimizeDroppedItems.get();
        try { return level.entitiesForRendering(); } finally { renderer.collecting=false; }
    }
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void carbon$begin(GraphicsResourceAllocator allocator,DeltaTracker tracker,boolean outline,Camera camera,GameRenderer renderer,LightTexture light,Matrix4f view,Matrix4f projection,CallbackInfo ci) {
        Carbon.Client.RENDERER.beginWorld(camera,first.carbon.compat.RenderCompat.matrix(view),first.carbon.compat.RenderCompat.matrix(projection));
        Carbon.Client.DROPPED_RENDERER.beginFrame(camera,first.carbon.compat.RenderCompat.matrix(view),first.carbon.compat.RenderCompat.matrix(projection),tracker.getGameTimeDeltaPartialTick(false));
    }
    @Inject(method="renderLevel",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/LevelRenderer;visibleEntityCount:I",opcode=181,shift=At.Shift.AFTER))
    private void carbon$count(CallbackInfo ci) { visibleEntityCount+=Carbon.Client.DROPPED_RENDERER.visibleCollected; }
    @Inject(method="renderEntities",at=@At("RETURN"))
    private void carbon$flush(CallbackInfo ci) { Carbon.Client.DROPPED_RENDERER.flush();Carbon.Client.RENDERER.flushShadows(); }
    @Inject(method="renderLevel",at=@At("RETURN"))
    private void carbon$end(CallbackInfo ci) { Carbon.Client.DROPPED_RENDERER.flush();Carbon.Client.DROPPED_RENDERER.endFrame();Carbon.Client.RENDERER.endWorld(); }
}
