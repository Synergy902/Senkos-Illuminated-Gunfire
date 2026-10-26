package com.senko.gunfirelight.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Resource checks never initialize SBW or Forge registries during Mixin discovery. */
public final class SbwCompatibilityMixinPlugin implements IMixinConfigPlugin {
    private static final String MARKER = "com/atsuishio/superbwarfare/Mod.class";
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1).startsWith("Sbw")
            || SbwCompatibilityMixinPlugin.class.getClassLoader().getResource(MARKER) != null;
    }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
