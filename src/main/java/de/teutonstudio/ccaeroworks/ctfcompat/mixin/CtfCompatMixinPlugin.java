package de.teutonstudio.ccaeroworks.ctfcompat.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Keeps every direct Create: Train Functionality reference out of class loading when it is absent. */
public final class CtfCompatMixinPlugin implements IMixinConfigPlugin {
    private boolean ctfPresent;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader loader = CtfCompatMixinPlugin.class.getClassLoader();
        ctfPresent = loader.getResource("createtrainfunctionality/DashboardControllerBlockEntity.class") != null;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return ctfPresent; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
