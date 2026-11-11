package first.carbon.mixin;
import com.mojang.blaze3d.vertex.BufferBuilder;
import first.carbon.client.ItemBatchRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements ItemBatchRenderer.Target {
    @Unique private MultiBufferSource.BufferSource carbon$source;
    @Unique private RenderType carbon$type;
    public MultiBufferSource.BufferSource imo$source() { return carbon$source; }
    public RenderType imo$type() { return carbon$type; }
    public void imo$target(MultiBufferSource.BufferSource source, RenderType type) { carbon$source = source; carbon$type = type; }
}
