package first.carbon.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import first.carbon.Carbon;
import net.irisshaders.batchedentityrendering.impl.BlendingStateHolder;
import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import first.carbon.compat.RenderCompat.CaptureSource;
import net.minecraft.client.renderer.rendertype.RenderType;

// Loaded reflectively only when Iris is present; keeps optional Iris types out of the main bridge verifier.
public final class IrisLayer extends RenderType implements BlendingStateHolder, IrisCompatibility.LayerAccess {
    private final Object owner;
    private final RenderType wrapped;
    private boolean flushing;

    public IrisLayer(Object owner, RenderType wrapped) {
        super("carbon:instanced_" + wrapped, wrapped.bufferSize(), wrapped.affectsCrumbling(),wrapped.sortOnUpload(),wrapped::setupRenderState,wrapped::clearRenderState);
        this.owner = owner;
        this.wrapped = wrapped;
    }

    @Override public RenderType carbon$wrapped() { return wrapped; }
    @Override public void setTransparencyType(TransparencyType type) { ((BlendingStateHolder) (Object) wrapped).setTransparencyType(type); }
    @Override public TransparencyType getTransparencyType() { return ((BlendingStateHolder) (Object) wrapped).getTransparencyType(); }
    @Override public void clearRenderState() {
        if (!flushing) {
            flushing = true;
            try { Carbon.Client.RENDERER.flush(owner, this); }
            finally { flushing = false; }
        }
        wrapped.clearRenderState();
    }
    @Override public void draw(com.mojang.blaze3d.vertex.MeshData mesh) {
        Carbon.Client.RENDERER.flush(owner, this);
        wrapped.draw(mesh);
    }
    @Override public com.mojang.blaze3d.pipeline.RenderTarget getRenderTarget() {return wrapped.getRenderTarget();}
    @Override public com.mojang.blaze3d.pipeline.RenderPipeline getRenderPipeline() {return wrapped.getRenderPipeline();}
    @Override public com.mojang.blaze3d.vertex.VertexFormat format() {return wrapped.format();}
    @Override public com.mojang.blaze3d.vertex.com.mojang.blaze3d.PrimitiveTopology mode() {return wrapped.mode();}
}
