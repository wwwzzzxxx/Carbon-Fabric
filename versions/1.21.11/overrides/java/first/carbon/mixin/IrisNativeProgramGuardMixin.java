package first.carbon.mixin;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlProgram;
import com.llamalad7.mixinextras.sugar.Local;
import first.carbon.compat.IrisProgramBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GlCommandEncoder.class, priority = 500)
public abstract class IrisNativeProgramGuardMixin {
    @Inject(method = "trySetup", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlProgram;getUniforms()Ljava/util/Map;"))
    private void carbon$bindBeforeUniforms(CallbackInfoReturnable<Boolean> ci, @Local GlProgram program) {
        IrisProgramBinding.restoreIfMissing(program);
    }
}
