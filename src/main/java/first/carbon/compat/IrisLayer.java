package first.carbon.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import first.carbon.Carbon;
import net.coderbot.batchedentityrendering.impl.BlendingStateHolder;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

// Loaded reflectively only when Iris is present; keeps optional Iris types out of the main bridge verifier.
public final class IrisLayer extends RenderType implements BlendingStateHolder, IrisCompatibility.LayerAccess {
    private final MultiBufferSource.BufferSource owner;
    private final RenderType wrapped;
    private boolean flushing;

    public IrisLayer(MultiBufferSource.BufferSource owner, RenderType wrapped) {
        super("carbon:instanced_" + wrapped, wrapped.format(), wrapped.mode(), wrapped.bufferSize(), wrapped.affectsCrumbling(),
                ((first.carbon.mixin.RenderTypeStateAccessor) (Object) wrapped).carbon$sortOnUpload(),
                wrapped::setupRenderState, wrapped::clearRenderState);
        this.owner = owner;
        this.wrapped = wrapped;
    }

    @Override public RenderType carbon$wrapped() { return wrapped; }
    @Override public TransparencyType getTransparencyType() { return ((BlendingStateHolder) (Object) wrapped).getTransparencyType(); }
    @Override public void clearRenderState() {
        if (!flushing) {
            flushing = true;
            try { Carbon.Client.RENDERER.flush(owner, this); }
            finally { flushing = false; }
        }
        wrapped.clearRenderState();
    }
    @Override public void end(BufferBuilder builder, int x, int y, int z) {
        Carbon.Client.RENDERER.flush(owner, this);
        wrapped.end(builder, x, y, z);
    }
}
