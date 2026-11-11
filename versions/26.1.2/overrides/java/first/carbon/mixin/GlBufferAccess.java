package first.carbon.mixin;
import com.mojang.blaze3d.opengl.GlBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GlBuffer.class) public interface GlBufferAccess {@Accessor("handle") int carbon$handle();}
