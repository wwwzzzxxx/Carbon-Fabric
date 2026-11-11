package first.carbon.mixin;
import first.carbon.Carbon;
import first.carbon.compat.ItemGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ItemFeatureRenderer.class,priority=500) public abstract class ItemModelSubmissionMixin {
 @Unique private final PoseStack carbon$pose=new PoseStack();
 @Inject(method="renderItem",at=@At("HEAD"),cancellable=true)
 private void carbon$submit(MultiBufferSource.BufferSource buffers,OutlineBufferSource outline,SubmitNodeStorage.ItemSubmit submit,CallbackInfo ci) {
  if(!Carbon.CONFIG.enabled.get() || submit.outlineColor()!=0 || submit.foilType()==ItemStackRenderState.FoilType.SPECIAL || submit.quads().isEmpty())return;
  var type=submit.quads().get(0).materialInfo().itemRenderType();
  if(submit.quads().stream().anyMatch(quad->quad.materialInfo().itemRenderType()!=type))return;
  carbon$pose.last().set(submit.pose());
  var vertices=ItemFeatureRenderer.getFoilBuffer(buffers,type,true,submit.foilType()!=ItemStackRenderState.FoilType.NONE);
  if(Carbon.Client.RENDERER.submit(ItemGeometry.plain(submit.quads()),submit.tintLayers(),submit.lightCoords(),submit.overlayCoords(),carbon$pose,vertices))ci.cancel();
 }
}
