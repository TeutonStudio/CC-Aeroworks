package de.teutonstudio.ccaeroworks.radarcompat.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Prevents radar mixin classes from being applied when Create: Radars is absent. */
public final class RadarCompatMixinPlugin implements IMixinConfigPlugin {
    private boolean createRadarPresent;

    @Override
    public void onLoad(String mixinPackage) {
        ClassLoader loader = RadarCompatMixinPlugin.class.getClassLoader();
        createRadarPresent = loader.getResource("com/happysg/radar/block/behavior/networks/NetworkData.class") != null;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return createRadarPresent; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
