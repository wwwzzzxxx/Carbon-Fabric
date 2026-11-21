package first.carbon.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import first.carbon.client.BufferedItemOutlines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BufferBuilder.class)
public interface BufferedOutlineBuilderMixin extends BufferedItemOutlines.OutlineBuffer {
    @Override
    @Accessor("vertices")
    int carbon$vertexCount();
}
