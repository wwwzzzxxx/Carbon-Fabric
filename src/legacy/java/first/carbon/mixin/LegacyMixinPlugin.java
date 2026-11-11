package first.carbon.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class LegacyMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("first.carbon.mixin.OptiFineLevelRendererMixin")) {
            return FabricLoader.getInstance().getModContainer("optifabric")
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                    .filter("1.13.16"::equals)
                    .isPresent();
        }
        if (!mixinClassName.equals("first.carbon.mixin.IrisBufferedSourceMixin")) return true;
        return FabricLoader.getInstance().getModContainer("iris")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .filter("1.4.5"::equals)
                .isPresent();
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
