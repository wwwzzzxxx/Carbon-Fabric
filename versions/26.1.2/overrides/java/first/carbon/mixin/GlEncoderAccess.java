package first.carbon.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(targets="com.mojang.blaze3d.opengl.GlCommandEncoder")
public interface GlEncoderAccess {
    @Invoker("applyPipelineState") void carbon$pipeline(RenderPipeline pipeline);
}
