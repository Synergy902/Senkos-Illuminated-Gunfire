package offline;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/** Apply packaged SBW mixins to the released SBW/GeckoLib bytecode; never starts a game. */
public final class SbwMixinCheck {
    private static int assertions;
    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
    private record Hook(String mixin, String handler) {}

    public static void main(String[] args) throws Exception {
        boolean server = Boolean.getBoolean("offline.server");
        boolean absent = Boolean.getBoolean("offline.absent");
        ClassLoader loader = SbwMixinCheck.class.getClassLoader();
        JsonObject config;
        try (Reader in = new InputStreamReader(Objects.requireNonNull(
                loader.getResourceAsStream("offline-sbw.mixins.json")), StandardCharsets.UTF_8)) {
            config = JsonParser.parseReader(in).getAsJsonObject();
        }
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(server
                ? MixinEnvironment.Side.SERVER : MixinEnvironment.Side.CLIENT)
                .setObfuscationContext("searge");
        var service = new OfflineMixinService();
        Map<String,List<Hook>> targets = new LinkedHashMap<>();
        for (JsonElement entry : config.getAsJsonArray("client")) {
            String mixinName = config.get("package").getAsString() + "." + entry.getAsString();
            ClassNode mixin = service.getClassNode(mixinName);
            List<String> names = targetNames(mixin);
            check(!names.isEmpty(), "Mixin has no targets: " + mixinName);
            for (String target : names) {
                List<Hook> hooks = targets.computeIfAbsent(target, key -> new ArrayList<>());
                for (MethodNode method : mixin.methods) {
                    if (injection(method)) hooks.add(new Hook(mixinName, method.name));
                }
            }
        }
        check(!targets.isEmpty(), "No SBW mixins discovered in packaged configuration");
        if (absent) {
            check(loader.getResource("com/atsuishio/superbwarfare/Mod.class") == null,
                    "Absent-mod case accidentally contains Superb Warfare");
            check(config.has("plugin"), "Optional SBW mixins need a gating plugin");
            IMixinConfigPlugin plugin = (IMixinConfigPlugin) Class.forName(config.get("plugin").getAsString())
                    .getDeclaredConstructor().newInstance();
            plugin.onLoad(config.get("package").getAsString());
            for (var entry : targets.entrySet()) {
                for (Hook hook : entry.getValue()) {
                    check(!plugin.shouldApplyMixin(entry.getKey(), hook.mixin),
                            "Missing SBW must disable " + hook.mixin);
                }
            }
        }
        Mixins.addConfiguration("offline-sbw.mixins.json");
        var ctor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer")
                .getDeclaredConstructor();
        ctor.setAccessible(true);
        IMixinTransformer transformer = (IMixinTransformer) ctor.newInstance();
        int methods = 0;
        if (absent) {
            String name = "com.tacz.guns.client.model.BedrockGunModel";
            byte[] input = bytes(service.getClassNode(name));
            byte[] output = transformer.transformClassBytes(name, name, input);
            check(Arrays.equals(input, output), "Absent SBW integration must leave TaCZ unchanged");
        } else for (var entry : targets.entrySet()) {
            String target = entry.getKey();
            byte[] input = bytes(service.getClassNode(target));
            byte[] output = transformer.transformClassBytes(target, target, input);
            if (server) {
                check(Arrays.equals(input, output), "Client SBW mixin applied on server: " + target);
                continue;
            }
            check(!Arrays.equals(input, output), "No transformation: " + target);
            ClassNode transformed = new ClassNode();
            new ClassReader(output).accept(transformed, 0);
            for (Hook hook : entry.getValue()) {
                List<MethodNode> handlers = transformed.methods.stream()
                        .filter(m -> m.name.contains(hook.handler)).toList();
                check(!handlers.isEmpty(), "Missing injected handler " + hook.mixin + ":" + hook.handler);
                for (MethodNode handler : handlers) {
                    long calls = transformed.methods.stream().filter(m -> !m.name.equals(handler.name))
                            .flatMap(m -> Arrays.stream(m.instructions.toArray()))
                            .filter(n -> n instanceof MethodInsnNode call && call.owner.equals(transformed.name)
                                    && call.name.equals(handler.name) && call.desc.equals(handler.desc)).count();
                    check(calls > 0, "Unreachable injected handler " + handler.name);
                }
            }
            for (MethodNode method : transformed.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                    new Analyzer<>(new BasicVerifier()).analyze(transformed.name, method);
                    methods++;
                }
            }
            Path out = Path.of(System.getProperty("offline.output"), transformed.name + ".class");
            Files.createDirectories(out.getParent()); Files.write(out, output);
            System.out.println("APPLIED + ASM VERIFIED " + target);
        }
        System.out.println("PASS " + assertions + " assertions, " + methods + " verified methods; "
                + (absent ? "ABSENT SBW" : server ? "SERVER" : "CLIENT") + "; no game started");
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> both = new ArrayList<>();
        if (a != null) both.addAll(a); if (b != null) both.addAll(b); return both;
    }
    private static boolean injection(MethodNode method) {
        return annotations(method.visibleAnnotations, method.invisibleAnnotations).stream()
                .anyMatch(a -> a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                        || a.desc.startsWith("Lcom/llamalad7/mixinextras/injector/"));
    }
    private static List<String> targetNames(ClassNode node) {
        List<String> result = new ArrayList<>();
        for (AnnotationNode annotation : annotations(node.visibleAnnotations, node.invisibleAnnotations)) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String key = (String) annotation.values.get(i);
                if (!key.equals("value") && !key.equals("targets")) continue;
                for (Object target : (List<?>) annotation.values.get(i + 1))
                    result.add(target instanceof Type type ? type.getClassName() : target.toString());
            }
        }
        return result;
    }
}
