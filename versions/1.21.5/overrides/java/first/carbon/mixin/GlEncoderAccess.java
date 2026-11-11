package first.carbon.mixin;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GlCommandEncoder.class)
public interface GlEncoderAccess {
    @Invoker("applyPipelineState") void carbon$pipeline(RenderPipeline pipeline);
}
