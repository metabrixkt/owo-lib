package io.wispforest.owo;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Checks Minecraft member contracts without initializing or launching the game.
///
/// @since 0.13.1+metabrix+26.3
/// @author metabrix
@NullMarked
class MixinTargetTest {
    private final Map<String, ClassNode> classes = new HashMap<>();

    @ParameterizedTest
    @ValueSource(strings = {"owo.mixins.json", "uwu.mixins.json"})
    void configuredMixinsReferenceExistingMembers(final String configName) throws IOException {
        final var problems = new ArrayList<String>();
        final var loader = getClass().getClassLoader();
        try (final var stream = loader.getResourceAsStream(configName)) {
            if (stream == null) throw new IOException("Missing " + configName);

            final var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            final var base = config.get("package").getAsString().replace('.', '/') + "/";
            for (var section : List.of("mixins", "client", "server")) {
                if (!config.has(section)) continue;

                for (final var entry : config.getAsJsonArray(section)) {
                    final var mixin = read(base + entry.getAsString().replace('.', '/'));
                    for (final var annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) {
                        if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;

                        final var targets = new ArrayList<String>();
                        for (final var type : list(value(annotation, "value"))) targets.add(((Type) type).getInternalName());
                        for (final var target : list(value(annotation, "targets"))) targets.add(target.toString().replace('.', '/'));

                        for (final var target : targets) {
                            try {
                                checkMembers(mixin, target, problems);
                            } catch (final IOException cause) {
                                problems.add(mixin.name + ": " + cause.getMessage());
                            }
                        }
                    }
                }
            }
        }

        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
    }

    private void checkMembers(
        final ClassNode mixin,
        final String target,
        final List<String> problems
    ) throws IOException {
        final var targetClass = read(target);
        if ((mixin.access & Opcodes.ACC_INTERFACE) != 0 && (targetClass.access & Opcodes.ACC_INTERFACE) == 0) {
            for (final var method : mixin.methods) {
                if (method.name.startsWith("<") || (method.access & Opcodes.ACC_SYNTHETIC) != 0) continue;

                final boolean accessor = annotations(method.visibleAnnotations, method.invisibleAnnotations).stream()
                    .anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;")
                        || annotation.desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;"));
                if (!accessor) {
                    problems.add(mixin.name + ": interface mixin targets class " + target
                        + " but declares non-accessor method " + method.name + method.desc);
                }
            }
        }
        for (final var field : mixin.fields) {
            for (final var annotation : annotations(field.visibleAnnotations, field.invisibleAnnotations)) {
                if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")
                    && !hasField(target, field.name, field.desc)) {
                    problems.add(mixin.name + ": missing shadow " + target + "." + field.name + ":" + field.desc);
                }
            }
        }
        for (final var method : mixin.methods) {
            final var methodAnnotations = annotations(method.visibleAnnotations, method.invisibleAnnotations);
            final boolean alternative = methodAnnotations.stream().anyMatch(annotation ->
                annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Group;"));
            for (final var annotation : methodAnnotations) {
                final var explicit = value(annotation, "value");
                if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;") && explicit instanceof final String name) {
                    final var type = Type.getMethodType(method.desc);
                    final var fieldType = type.getReturnType().getSort() == Type.VOID ? type.getArgumentTypes()[0] : type.getReturnType();
                    if (!hasField(target, name, fieldType.getDescriptor())) {
                        problems.add(mixin.name + ": missing accessor " + target + "." + name + ":" + fieldType.getDescriptor());
                    }
                }
                if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;") && explicit instanceof final String name) {
                    var descriptor = method.desc;
                    if (name.equals("<init>")) descriptor = descriptor.substring(0, descriptor.indexOf(')') + 1) + "V";
                    if (!hasMethod(target, name, descriptor)) {
                        problems.add(mixin.name + ": missing invoker " + target + "." + name + descriptor);
                    }
                }
                if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")
                    && !hasMethod(target, method.name.replaceFirst("^shadow\\$", ""), method.desc)) {
                    problems.add(mixin.name + ": missing shadow " + target + "." + method.name + method.desc);
                }
                // Injection annotations all identify target methods using the `method` element.
                for (final var selector : list(value(annotation, "method"))) {
                    final var text = selector.toString();
                    if (text.contains("*") || text.startsWith("@")) continue;

                    final var index = text.indexOf('(');
                    final var name = index < 0 ? text : text.substring(0, index);
                    final var descriptor = index < 0 ? null : text.substring(index);
                    if (!hasMethod(target, name, descriptor)) {
                        problems.add(mixin.name + ": missing injection method " + target + "." + text);
                    }
                }
                checkReferences(annotation, mixin.name, alternative, problems);
            }
        }
    }

    private void checkReferences(
        final AnnotationNode annotation,
        final String mixin,
        final boolean alternative,
        final List<String> problems
    ) throws IOException {
        if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/At;")) {
            final var target = value(annotation, "target");
            if (target instanceof final String reference && reference.startsWith("L") && reference.contains(";")) {
                final int separator = reference.indexOf(';');
                final var owner = reference.substring(1, separator);
                // Grouped Fabric/Quilt hooks are alternatives; only the installed loader is present.
                if (alternative && getClass().getClassLoader().getResource(owner + ".class") == null) return;

                final var member = reference.substring(separator + 1);
                final int method = member.indexOf('(');
                final int field = member.indexOf(':');
                if (method >= 0 && !hasMethod(owner, member.substring(0, method), member.substring(method))) {
                    problems.add(mixin + ": missing invocation target " + reference);
                } else if (field >= 0 && !hasField(owner, member.substring(0, field), member.substring(field + 1))) {
                    problems.add(mixin + ": missing field target " + reference);
                }
            }
        }

        if (annotation.values != null) {
            for (int i = 1; i < annotation.values.size(); i += 2) {
                for (final var value : list(annotation.values.get(i))) {
                    if (value instanceof final AnnotationNode nested) {checkReferences(nested, mixin, alternative, problems);}
                }
            }
        }
    }

    @Contract("null, _, _ -> false")
    private boolean hasField(final @Nullable String owner, final String name, final String descriptor) throws IOException {
        if (owner == null) return false;

        final var node = read(owner);
        if (node.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor))) return true;
        return hasField(node.superName, name, descriptor);
    }

    @Contract("null, _, _ -> false")
    private boolean hasMethod(final @Nullable String owner, final String name, final @Nullable String descriptor) throws IOException {
        if (owner == null) return false;

        final var node = read(owner);
        if (node.methods.stream().anyMatch(method -> method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor)))) {
            return true;
        }

        // This method is supplied by Fabric's creative-tab mixin, not Minecraft's raw class.
        if (owner.equals("net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen") && name.equals("updateSelection")) {
            return hasMethod("net/fabricmc/fabric/mixin/creativetab/client/CreativeModeInventoryScreenMixin", name, descriptor);
        }

        if (name.equals("<init>")) return false;

        for (final var parent : node.interfaces) if (hasMethod(parent, name, descriptor)) return true;

        return hasMethod(node.superName, name, descriptor);
    }

    private ClassNode read(final String name) throws IOException {
        final var cached = classes.get(name);
        if (cached != null) return cached;

        try (final var stream = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new IOException("Missing class " + name);

            final var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes.put(name, node);
            return node;
        }
    }

    private static List<AnnotationNode> annotations(
        final @Nullable List<AnnotationNode> visible,
        final @Nullable List<AnnotationNode> invisible
    ) {
        var result = new ArrayList<AnnotationNode>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static @Nullable Object value(final AnnotationNode annotation, final String name) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        return null;
    }

    @Contract("null -> !null")
    private static List<?> list(final @Nullable Object value) {
        return value == null ? List.of() : value instanceof List<?> values ? values : List.of(value);
    }
}
