package first.carbon.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements ItemBatchRenderer.Target {
    @Unique
    private MultiBufferSource.BufferSource imo$source;
    @Unique
    private RenderType imo$type;

    @Override
    public MultiBufferSource.BufferSource imo$source() {
        return imo$source;
    }

    @Override
    public RenderType imo$type() {
        return imo$type;
    }

    @Override
    public void imo$target(MultiBufferSource.BufferSource source, RenderType type) {
        imo$source = source;
        imo$type = type;
    }
}
