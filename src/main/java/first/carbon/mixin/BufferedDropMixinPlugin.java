package first.carbon.mixin;

import first.carbon.compat.BufferedDropProvider;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class BufferedDropMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> targets, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return !BufferedDropProvider.provider().isEmpty();
    }
}
