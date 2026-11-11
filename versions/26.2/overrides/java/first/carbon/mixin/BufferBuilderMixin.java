package first.carbon.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import first.carbon.client.ItemBatchRenderer;
import first.carbon.compat.RenderCompat.CaptureSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements ItemBatchRenderer.Target {
    @Unique
    private Object imo$source;
    @Unique
    private RenderType imo$type;

    @Override
    public Object imo$source() {
        return imo$source;
    }

    @Override
    public RenderType imo$type() {
        return imo$type;
    }

    @Override
    public void imo$target(Object source, RenderType type) {
        imo$source = source;
        imo$type = type;
    }
}
