package first.carbon.mixin;

import first.carbon.client.OutlineShader;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ShaderManager.class)
abstract class OutlineShaderMixin {
    @ModifyVariable(method = "loadShader", at = @At(value = "STORE", ordinal = 0), ordinal = 0, allow = 1)
    private static String carbon$outline(String source, Identifier location, Resource resource) {
        if (location.getNamespace().equals("minecraft") && location.getPath().equals("shaders/post/entity_sobel.fsh")
                && OutlineShader.isBuiltin(resource.sourcePackId()) && OutlineShader.enabled()) {
            return OutlineShader.optimize(source);
        }
        return source;
    }
}
