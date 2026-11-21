package first.carbon.mixin;

import com.mojang.blaze3d.shaders.Program;
import first.carbon.client.OutlineShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.IOException;
import java.io.InputStream;

@Mixin(Program.class)
abstract class OutlineShaderMixin {
    @ModifyVariable(method = "compileShader", at = @At("HEAD"), argsOnly = true)
    private static InputStream carbon$outline(InputStream input, Program.Type type, String name,
                                             InputStream original, String pack) throws IOException {
        if (type == Program.Type.FRAGMENT && name.equals("entity_sobel") && OutlineShader.isBuiltin(pack)
                && OutlineShader.enabled()) {
            return OutlineShader.optimize(input);
        }
        return input;
    }
}
