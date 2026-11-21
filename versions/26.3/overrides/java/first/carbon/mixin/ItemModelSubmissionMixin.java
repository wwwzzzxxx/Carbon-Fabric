package first.carbon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import first.carbon.Carbon;
import first.carbon.client.ItemOutlineBatchRenderer;
import first.carbon.compat.ItemGeometry;
import first.carbon.compat.RenderCompat;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = ItemFeatureRenderer.class, priority = 500)
public abstract class ItemModelSubmissionMixin extends RenderTypeFeatureRenderer<ItemFeatureRenderer.Submit> {
    @Unique private final PoseStack carbon$pose = new PoseStack();
    @Unique private final List<Object> carbon$owners = new ArrayList<>();
    @Unique private final Map<List<BakedQuad>, ItemGeometry> carbon$frameModels = new IdentityHashMap<>();
    @Unique private final ItemOutlineBatchRenderer carbon$outlines = new ItemOutlineBatchRenderer();
    @Unique private int carbon$group;
    @Unique private int carbon$sharedOutlineGroup;

    @Override
    public void beginPrepare(FeatureFrameContext context) {
        carbon$group = 0;
        carbon$sharedOutlineGroup = -1;
        carbon$frameModels.clear();
        carbon$outlines.beginFrame();
    }

    @Override
    public void finishPrepare(FeatureFrameContext context) {
        carbon$outlines.upload();
    }

    @Override
    public void finishExecute(FeatureFrameContext context) {
        super.finishExecute(context);
        carbon$outlines.finishFrame();
    }

    @Override
    public void close() {
        carbon$frameModels.clear();
        carbon$outlines.close();
    }

    @ModifyVariable(method = "buildGroup", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<ItemFeatureRenderer.Submit> carbon$filter(List<ItemFeatureRenderer.Submit> submits) {
        int index = carbon$group++;
        while (carbon$owners.size() <= index) carbon$owners.add(new Object());
        if (!Carbon.CONFIG.enabled.get() || !RenderCompat.world || submits.isEmpty()) return submits;
        if (submits.getFirst().outlineColor() != 0) {
            for (int i = 0; i < submits.size(); i++) {
                if (!Carbon.Client.DROPPED_RENDERER.isOutlineMarker(submits.get(i))) continue;
                carbon$sharedOutlineGroup = index;
                if (submits.size() == 1) return List.of();
                var nativeSubmits = new ArrayList<>(submits);
                nativeSubmits.remove(i);
                submits = nativeSubmits;
                break;
            }
            return carbon$outlines.prepare(index, submits) ? List.of() : submits;
        }

        Object owner = carbon$owners.get(index);
        List<ItemFeatureRenderer.Submit> remaining = null;
        for (var submit : submits) {
            if (submit.outlineColor() == 0 && submit.foilType() == ItemStackRenderState.FoilType.NONE) {
                ItemGeometry model = carbon$model(submit.quads());
                if (model != null) {
                    var type = model.quads.getFirst().materialInfo().itemRenderType();
                    carbon$pose.last().set(submit.pose());
                    if (Carbon.Client.RENDERER.submitDirect(model, submit.tintLayers(), submit.lightCoords(),
                            submit.overlayCoords(), carbon$pose, owner, type, null)) continue;
                }
            }
            if (remaining == null) remaining = new ArrayList<>();
            remaining.add(submit);
        }
        return remaining == null ? List.of() : remaining;
    }

    @Unique
    private ItemGeometry carbon$model(List<BakedQuad> quads) {
        // Submit nodes for the same baked model share its quad list.
        ItemGeometry model = carbon$frameModels.get(quads);
        if (model == null && !carbon$frameModels.containsKey(quads)) {
            model = carbon$createModel(quads);
            carbon$frameModels.put(quads, model);
        }
        return model;
    }

    @Unique
    private ItemGeometry carbon$createModel(List<BakedQuad> quads) {
        if (quads.isEmpty()) return null;
        var type = quads.getFirst().materialInfo().itemRenderType();
        if (type.hasBlending()) return null;
        for (BakedQuad quad : quads) {
            if (quad.materialInfo().itemRenderType() != type) return null;
        }
        return ItemGeometry.plain(quads);
    }

    @Override
    public void executeGroup(FeatureFrameContext context, OitStage stage, RenderPass pass, int index,
                             List<ItemFeatureRenderer.Submit> submits, boolean ordered) {
        if (index == carbon$sharedOutlineGroup) Carbon.Client.DROPPED_RENDERER.drawOutlines(pass);
        carbon$outlines.draw(index, pass);
        Carbon.Client.RENDERER.flushOwner(carbon$owners.get(index));
        super.executeGroup(context, stage, pass, index, submits, ordered);
    }
}
