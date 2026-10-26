package offline;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Check the shared library upgrade against actual TaCZ bytecode, without class initialization. */
public final class SbwDependencyCheck {
    private static final String PREFIX = "com/github/mcmodderanchor/simplebedrockmodel/";
    private static final Map<String,ClassNode> cache = new HashMap<>();
    private static ClassNode node(String name) throws IOException {
        if (cache.containsKey(name)) return cache.get(name);
        try (InputStream in = SbwDependencyCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing referenced library class: " + name);
            ClassNode result = new ClassNode(); new ClassReader(in).accept(result, ClassReader.SKIP_CODE);
            cache.put(name, result); return result;
        }
    }
    private static boolean method(String owner, String name, String desc, Set<String> seen) throws IOException {
        if (!seen.add(owner)) return false;
        ClassNode n = node(owner);
        if (n.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc))) return true;
        if (name.equals("<init>")) return false;
        if (n.superName != null && method(n.superName, name, desc, seen)) return true;
        for (String iface : n.interfaces) if (method(iface, name, desc, seen)) return true;
        return false;
    }
    private static boolean field(String owner, String name, String desc, Set<String> seen) throws IOException {
        if (!seen.add(owner)) return false;
        ClassNode n = node(owner);
        if (n.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc))) return true;
        if (n.superName != null && field(n.superName, name, desc, seen)) return true;
        for (String iface : n.interfaces) if (field(iface, name, desc, seen)) return true;
        return false;
    }
    public static void main(String[] args) throws Exception {
        int calls = 0, fields = 0, types = 0;
        try (ZipFile jar = new ZipFile(args[0])) {
            for (ZipEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode n = new ClassNode();
                try (InputStream in = jar.getInputStream(entry)) { new ClassReader(in).accept(n, 0); }
                if (n.superName != null && n.superName.startsWith(PREFIX)) { node(n.superName); types++; }
                for (String iface : n.interfaces) if (iface.startsWith(PREFIX)) { node(iface); types++; }
                for (MethodNode m : n.methods) for (AbstractInsnNode insn : m.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.startsWith(PREFIX)) {
                        if (!method(call.owner, call.name, call.desc, new HashSet<>()))
                            throw new AssertionError("Unresolved method from " + n.name + ": " + call.owner + "." + call.name + call.desc);
                        calls++;
                    } else if (insn instanceof FieldInsnNode f && f.owner.startsWith(PREFIX)) {
                        if (!field(f.owner, f.name, f.desc, new HashSet<>()))
                            throw new AssertionError("Unresolved field from " + n.name + ": " + f.owner + "." + f.name + f.desc);
                        fields++;
                    } else if (insn instanceof TypeInsnNode t && t.desc.startsWith(PREFIX)) { node(t.desc); types++; }
                }
            }
        }
        if (calls + fields + types == 0) throw new AssertionError("No TaCZ library references were checked");
        System.out.println("PASS TaCZ against SBW's shared SimpleBedrockModel: " + calls + " method calls, "
                + fields + " fields, " + types + " type references; no game started");
    }
}
