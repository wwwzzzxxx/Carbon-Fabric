package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(EntityRenderDispatcher.class)
abstract class ExternalItemDispatcherMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;"
            + "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"))
    private void carbon$captureDropPose(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack pose,
                                        SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        var batches = Carbon.Client.DROPPED_RENDERER;
        if (batches.acceptsExternalPoses() && state instanceof ItemEntityRenderState item && !item.appearsGlowing()
                && renderer.getClass() == ItemEntityRenderer.class && collector instanceof SubmitNodeStorage
                && (pose.last().pose().properties() & Matrix4fc.PROPERTY_TRANSLATION) != 0) {
            var previous = batches.captureExternalItem(item);
            try {
                original.call(renderer, state, pose, collector, camera);
            } finally {
                batches.captureExternalItem(previous);
            }
        } else {
            original.call(renderer, state, pose, collector, camera);
        }
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/"
            + "SubmitNodeCollector;submitShadow(Lcom/mojang/blaze3d/vertex/PoseStack;FLjava/util/List;)V"))
    private void carbon$batchDropShadow(SubmitNodeCollector collector, PoseStack pose, float radius,
                                        List<EntityRenderState.ShadowPiece> pieces, Operation<Void> original,
                                        @Local(argsOnly = true) EntityRenderState state) {
        if (!(collector instanceof SubmitNodeStorage) || !(state instanceof ItemEntityRenderState item)
                || !Carbon.Client.DROPPED_RENDERER.submitExternalShadow(item, pose)) {
            original.call(collector, pose, radius, pieces);
        }
    }
}
