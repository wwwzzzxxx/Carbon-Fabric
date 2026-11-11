package first.carbon.mixin;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BakedQuad.class)
public interface QuadAccessor { @Accessor("sprite") TextureAtlasSprite carbon$sprite(); }
