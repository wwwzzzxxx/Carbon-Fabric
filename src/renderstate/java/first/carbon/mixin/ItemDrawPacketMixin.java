package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class ItemDrawPacketMixin {
    @Inject(method = "submitEntities", at = @At("HEAD"), cancellable = true)
    private void carbon$tryItemPacket(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        if (Carbon.Client.DROPPED_RENDERER.replayItemPacket(state.entityRenderStates, pose)) ci.cancel();
    }

    @Inject(method = "submitEntities", at = @At("RETURN"))
    private void carbon$recordItemPacket(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        Carbon.Client.DROPPED_RENDERER.rememberItemPacket(state.entityRenderStates);
    }
}
