package first.carbon.mixin;

import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.Carbon;
import first.carbon.client.BufferedItemOutlines;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.OutlineBufferSource$EntityOutlineGenerator")
abstract class BufferedOutlineGeneratorMixin extends DefaultedVertexConsumer implements BufferedItemOutlines.OutlineVertex {
    @Shadow @Final @Mutable private VertexConsumer delegate;
    @Unique private boolean carbon$writing;

    @Override public VertexConsumer carbon$delegate() { return delegate; }
    @Override public void carbon$delegate(VertexConsumer vertices) { delegate = vertices; }
    @Override public int carbon$color() { return defaultA << 24 | defaultR << 16 | defaultG << 8 | defaultB; }

    @Inject(method = {"endVertex", "vertex(FFFFFFFFFIIFFF)V"}, at = @At("HEAD"))
    private void carbon$beforeNativeOutline(CallbackInfo ci) {
        if (!carbon$writing) {
            carbon$writing = true;
            Carbon.Client.RENDERER.beforeNativeOutline(delegate);
        }
    }
}
