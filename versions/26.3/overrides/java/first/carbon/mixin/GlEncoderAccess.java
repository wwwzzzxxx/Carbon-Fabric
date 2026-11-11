package first.carbon.mixin;
import com.mojang.renderpearl.backend.opengl.*;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="com.mojang.renderpearl.backend.opengl.GlCommandEncoder") public interface GlEncoderAccess {
 @Accessor("lastPipeline") GlRenderPipeline carbon$lastPipeline();
 @Accessor("lastPipeline") void carbon$lastPipeline(GlRenderPipeline value);
 @Accessor("lastProgram") void carbon$lastProgram(GlProgram value);
}
