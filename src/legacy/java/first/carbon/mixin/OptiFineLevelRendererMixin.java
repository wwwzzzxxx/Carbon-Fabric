package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import first.carbon.Carbon;
import java.util.Collection;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class OptiFineLevelRendererMixin {
    @Shadow @Final private RenderBuffers renderBuffers;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;prepare(Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Camera;Lnet/minecraft/world/entity/Entity;)V", shift = At.Shift.AFTER), require = 0)
    private void carbon$beginOptiFineSelection(CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.beginOptiFineLimit();
    }

    // OptiFine groups the entities after walking its visible chunk sections and calling shouldRender.
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"), require = 0)
    private Collection<?> carbon$collectSelected(Map<?, ?> groups, PoseStack pose, float partialTick, long finishTime,
                                                  boolean blockOutline, Camera camera, GameRenderer renderer, LightTexture light, Matrix4f projection) {
        Carbon.Client.DROPPED_RENDERER.collectOptiFine(groups, renderBuffers.bufferSource(), pose, partialTick);
        return groups.values();
    }
}
