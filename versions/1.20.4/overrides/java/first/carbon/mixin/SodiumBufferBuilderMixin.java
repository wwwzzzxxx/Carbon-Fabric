package first.carbon.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import first.carbon.client.BufferedItemOutlines;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder",remap=false)
public abstract class SodiumBufferBuilderMixin implements ItemBatchRenderer.Target, BufferedItemOutlines.OutlineBuffer {
    @Shadow public abstract BufferBuilder getOriginalBufferBuilder();

    @Unique
    private MultiBufferSource.BufferSource imo$source;
    @Unique
    private RenderType imo$type;

    @Override
    public MultiBufferSource.BufferSource imo$source() {
        return imo$source != null ? imo$source
                : ((ItemBatchRenderer.Target) getOriginalBufferBuilder()).imo$source();
    }

    @Override
    public RenderType imo$type() {
        return imo$type != null ? imo$type
                : ((ItemBatchRenderer.Target) getOriginalBufferBuilder()).imo$type();
    }

    @Override
    public void imo$target(MultiBufferSource.BufferSource source, RenderType type) {
        imo$source = source;
        imo$type = type;
    }

    @Override
    public int carbon$vertexCount() {
        return ((BufferedItemOutlines.OutlineBuffer) getOriginalBufferBuilder()).carbon$vertexCount();
    }
}
