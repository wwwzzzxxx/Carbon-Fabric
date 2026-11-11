package first.carbon.mixin;

import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.class)
public interface RenderTypeStateAccessor {
    @Accessor("sortOnUpload") boolean carbon$sortOnUpload();
}
