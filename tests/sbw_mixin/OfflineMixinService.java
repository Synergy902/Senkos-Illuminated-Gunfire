package offline;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.launch.platform.container.*;

/** Bytecode-only Mixin service. It never invokes a Minecraft entry point. */
public final class OfflineMixinService extends MixinServiceAbstract
        implements IClassProvider, IClassBytecodeProvider, IClassTracker, ITransformerProvider {
    public String getName() { return "offline-sbw-bytecode-regression"; }
    public boolean isValid() { return true; }
    public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
    public IClassProvider getClassProvider() { return this; }
    public IClassBytecodeProvider getBytecodeProvider() { return this; }
    public ITransformerProvider getTransformerProvider() { return this; }
    public IClassTracker getClassTracker() { return this; }
    public IMixinAuditTrail getAuditTrail() { return null; }
    public Collection<String> getPlatformAgents() { return Collections.emptyList(); }
    public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("bytecode-only"); }
    public InputStream getResourceAsStream(String name) { return getClass().getClassLoader().getResourceAsStream(name); }
    public URL[] getClassPath() { return new URL[0]; }
    public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        if (initialize && (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.blaze3d.")))
            throw new AssertionError("Minecraft initialization is forbidden: " + name);
        return Class.forName(name, false, getClass().getClassLoader());
    }
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
    public ClassNode getClassNode(String name) throws IOException, ClassNotFoundException { return getClassNode(name, true); }
    public ClassNode getClassNode(String name, boolean runTransformers) throws IOException, ClassNotFoundException {
        String resource = name.replace('.', '/') + ".class";
        byte[] bytes = null;
        try (JarFile jar = new JarFile(System.getProperty("offline.targetJar"))) {
            JarEntry entry = jar.getJarEntry(resource);
            if (entry != null) try (InputStream in = jar.getInputStream(entry)) { bytes = in.readAllBytes(); }
        }
        if (bytes == null) try (InputStream in = getResourceAsStream(resource)) { if (in != null) bytes = in.readAllBytes(); }
        if (bytes == null) throw new ClassNotFoundException(name);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
    public void registerInvalidClass(String name) { }
    public boolean isClassLoaded(String name) { return false; }
    public String getClassRestrictions(String name) { return ""; }
    public Collection<ITransformer> getTransformers() { return Collections.emptyList(); }
    public Collection<ITransformer> getDelegatedTransformers() { return Collections.emptyList(); }
    public void addTransformerExclusion(String name) { }
}
