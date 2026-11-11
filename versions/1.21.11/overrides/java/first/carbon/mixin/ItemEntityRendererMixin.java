package first.carbon.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import first.carbon.Carbon;
import first.carbon.compat.ItemStateAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemEntityRenderer.class) public abstract class ItemEntityRendererMixin extends EntityRenderer<ItemEntity,ItemEntityRenderState> {
    protected ItemEntityRendererMixin(EntityRendererProvider.Context context){super(context);}
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",at=@At("RETURN"))
    private void carbon$extract(ItemEntity entity,ItemEntityRenderState state,float partial,CallbackInfo ci){((ItemStateAccess)state).carbon$bind(entity,partial);}
    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",at=@At("HEAD"),cancellable=true)
    private void carbon$submit(ItemEntityRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo ci) {
        var access=(ItemStateAccess)state;var item=access.carbon$entity();
        if(item!=null && Carbon.Client.DROPPED_RENDERER.submitEntity(item,state.item,access.carbon$partial(),state.lightCoords,pose,Minecraft.getInstance().renderBuffers().bufferSource())) {super.submit(state,pose,collector,camera);ci.cancel();}
    }
}
