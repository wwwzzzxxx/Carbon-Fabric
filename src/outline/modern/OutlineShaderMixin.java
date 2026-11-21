package first.carbon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import first.carbon.client.OutlineShader;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
abstract class OutlineShaderMixin {
    @ModifyExpressionValue(method = "loadShader", at = @At(value = "INVOKE", remap = false,
            target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/Reader;)Ljava/lang/String;"))
    private static String carbon$outline(String source, @Local(argsOnly = true) ResourceLocation location,
                                         @Local(argsOnly = true) Resource resource) {
        if (location.getNamespace().equals("minecraft") && location.getPath().equals("shaders/post/entity_sobel.fsh")
                && OutlineShader.isBuiltin(resource.sourcePackId()) && OutlineShader.enabled()) {
            return OutlineShader.optimize(source);
        }
        return source;
    }
}
