package first.carbon.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import first.carbon.client.ItemBatchRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.blaze3d.vertex.VertexMultiConsumer$Double")
public interface VertexMultiConsumerMixin extends ItemBatchRenderer.VertexPair {
    @Override
    @Accessor("first")
    VertexConsumer imo$first();

    @Override
    @Accessor("second")
    VertexConsumer imo$second();
}
