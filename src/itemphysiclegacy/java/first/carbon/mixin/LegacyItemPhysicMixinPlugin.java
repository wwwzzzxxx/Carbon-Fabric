package first.carbon.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class LegacyItemPhysicMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> SUPPORTED_VERSIONS = Set.of("1.6.9", "1.6.12", "1.7.1");

    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> targets, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return FabricLoader.getInstance().getModContainer("itemphysic")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .filter(SUPPORTED_VERSIONS::contains).isPresent();
    }
}
