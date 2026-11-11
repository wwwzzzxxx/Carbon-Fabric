package first.carbon.mixin;
import first.carbon.Carbon;import first.carbon.compat.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.feature.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import java.util.*;
@Mixin(value=ItemFeatureRenderer.class,priority=500) public abstract class ItemModelSubmissionMixin extends RenderTypeFeatureRenderer<ItemFeatureRenderer.Submit> {
 @Unique private final PoseStack carbon$pose=new PoseStack();
 @Unique private final List<Object> carbon$owners=new ArrayList<>();
 @Unique private int carbon$group;
 @Override public void beginPrepare(FeatureFrameContext context){carbon$group=0;}
 @ModifyVariable(method="buildGroup",at=@At("HEAD"),argsOnly=true,ordinal=0)
 private List<ItemFeatureRenderer.Submit> carbon$filter(List<ItemFeatureRenderer.Submit> submits){
  int index=carbon$group++;while(carbon$owners.size()<=index)carbon$owners.add(new Object());Object owner=carbon$owners.get(index);
  if(!Carbon.CONFIG.enabled.get()||!RenderCompat.world)return submits;
  List<ItemFeatureRenderer.Submit> remaining=new ArrayList<>();
  for(var submit:submits){boolean accepted=false;
   if(submit.outlineColor()==0&&submit.foilType()!=ItemStackRenderState.FoilType.SPECIAL&&!submit.quads().isEmpty()&&!submit.hasTranslucency()){
    var type=submit.quads().get(0).materialInfo().itemRenderType();
    if(submit.quads().stream().allMatch(quad->quad.materialInfo().itemRenderType()==type)){
     carbon$pose.last().set(submit.pose());accepted=Carbon.Client.RENDERER.submitDirect(ItemGeometry.plain(submit.quads()),submit.tintLayers(),submit.lightCoords(),submit.overlayCoords(),carbon$pose,owner,type,submit.foilType()==ItemStackRenderState.FoilType.NONE?null:RenderCompat.foilType(type));
    }
   }
   if(!accepted)remaining.add(submit);
  }
  return remaining;
 }
 @Override public void executeGroup(FeatureFrameContext context,int index,List<ItemFeatureRenderer.Submit> submits,boolean ordered){super.executeGroup(context,index,submits,ordered);Carbon.Client.RENDERER.flushOwner(carbon$owners.get(index));}
}
