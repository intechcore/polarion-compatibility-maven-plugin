package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ASM visitor which reports the forbidden packages a class refers to.
 *
 * <p>It visits the same places as Polarion's runtime gate
 * ({@code com.polarion.alm.install.extensions.validator.helper.JavaxDetectionVisitor}): the
 * class name, superclass, interfaces and signature; annotation descriptors on the class,
 * fields, methods, parameters, instructions and local variables; field and method descriptors
 * and signatures; declared exceptions; the owner and descriptor of every field and method
 * instruction; type instructions; try-catch types; local variable descriptors; and string
 * constants loaded with {@code ldc}.</p>
 *
 * <p>One deliberate difference: Polarion stops at the first hit and returns a boolean, while
 * this visitor keeps going and records every distinct package with the value that matched.
 * The detection is identical, the report is usable.</p>
 */
public final class ForbiddenPackageVisitor extends ClassVisitor {

    /**
     * Parsing flags Polarion uses: {@code SKIP_DEBUG | SKIP_FRAMES}.
     */
    public static final int PARSING_OPTIONS = 6;

    /**
     * ASM API level. Named {@code ASM_API} rather than {@code API}, because {@link ClassVisitor}
     * already declares a protected {@code api} field and the two would differ only by case.
     */
    private static final int ASM_API = Opcodes.ASM9;

    private final PackageRules rules;
    private final Map<String, String> detections = new LinkedHashMap<>();
    private String className;

    /**
     * Creates a visitor which matches against the given rules.
     */
    public ForbiddenPackageVisitor(@NotNull PackageRules rules) {
        super(ASM_API);
        this.rules = rules;
    }

    /**
     * Forbidden package to the value which matched it, in discovery order.
     */
    public @NotNull Map<String, String> detections() {
        return detections;
    }

    /**
     * Name of the visited class in dotted form, or null when it was not visited.
     */
    public @Nullable String className() {
        return className;
    }

    private void check(@Nullable String value) {
        if (value == null) {
            return;
        }
        PackageRules.Rule rule = rules.match(value);
        if (rule != null) {
            detections.putIfAbsent(rule.packageName(), value.replace('/', '.'));
        }
    }

    private void checkAll(String[] values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            check(value);
        }
    }

    @Override
    public void visit(int version, int access, @Nullable String name, @Nullable String signature,
                      @Nullable String superName, String[] interfaces) {
        className = name == null ? null : name.replace('/', '.');
        check(name);
        check(signature);
        check(superName);
        checkAll(interfaces);
    }

    @Override
    public @Nullable AnnotationVisitor visitAnnotation(@Nullable String descriptor, boolean visible) {
        check(descriptor);
        return null;
    }

    @Override
    public @Nullable AnnotationVisitor visitTypeAnnotation(int typeRef, @Nullable TypePath typePath,
                                                           @Nullable String descriptor, boolean visible) {
        check(descriptor);
        return null;
    }

    @Override
    public @NotNull FieldVisitor visitField(int access, @Nullable String name, @Nullable String descriptor,
                                            @Nullable String signature, @Nullable Object value) {
        check(descriptor);
        check(signature);
        return new DetectionFieldVisitor();
    }

    @Override
    public @NotNull MethodVisitor visitMethod(int access, @Nullable String name, @Nullable String descriptor,
                                              @Nullable String signature, String[] exceptions) {
        check(descriptor);
        check(signature);
        checkAll(exceptions);
        return new DetectionMethodVisitor();
    }

    private final class DetectionFieldVisitor extends FieldVisitor {

        private DetectionFieldVisitor() {
            super(ASM_API);
        }

        @Override
        public @Nullable AnnotationVisitor visitAnnotation(@Nullable String descriptor, boolean visible) {
            check(descriptor);
            return null;
        }

        @Override
        public @Nullable AnnotationVisitor visitTypeAnnotation(int typeRef, @Nullable TypePath typePath,
                                                               @Nullable String descriptor, boolean visible) {
            check(descriptor);
            return null;
        }
    }

    private final class DetectionMethodVisitor extends MethodVisitor {

        private DetectionMethodVisitor() {
            super(ASM_API);
        }

        @Override
        public @Nullable AnnotationVisitor visitAnnotation(@Nullable String descriptor, boolean visible) {
            check(descriptor);
            return null;
        }

        @Override
        public @Nullable AnnotationVisitor visitTypeAnnotation(int typeRef, @Nullable TypePath typePath,
                                                               @Nullable String descriptor, boolean visible) {
            check(descriptor);
            return null;
        }

        @Override
        public @Nullable AnnotationVisitor visitParameterAnnotation(int parameter, @Nullable String descriptor, boolean visible) {
            check(descriptor);
            return null;
        }

        @Override
        public @Nullable AnnotationVisitor visitInsnAnnotation(int typeRef, @Nullable TypePath typePath,
                                                               @Nullable String descriptor, boolean visible) {
            check(descriptor);
            return null;
        }

        @Override
        public @Nullable AnnotationVisitor visitLocalVariableAnnotation(int typeRef, @Nullable TypePath typePath,
                                                                        Label[] start, Label[] end,
                                                                        int[] index, @Nullable String descriptor,
                                                                        boolean visible) {
            check(descriptor);
            return null;
        }

        @Override
        public void visitTypeInsn(int opcode, @Nullable String type) {
            check(type);
        }

        @Override
        public void visitFieldInsn(int opcode, @Nullable String owner, @Nullable String name, @Nullable String descriptor) {
            check(owner);
            check(descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, @Nullable String owner, @Nullable String name,
                                    @Nullable String descriptor, boolean isInterface) {
            check(owner);
            check(descriptor);
        }

        @Override
        public void visitLdcInsn(@Nullable Object value) {
            if (value instanceof String string) {
                check(string);
            }
        }

        /**
         * Never called with {@link #PARSING_OPTIONS}, which skips debug information. Kept
         * because Polarion carries the same hook, so the two stay comparable.
         */
        @Override
        public void visitLocalVariable(@Nullable String name, @Nullable String descriptor, @Nullable String signature,
                                       @Nullable Label start, @Nullable Label end, int index) {
            check(descriptor);
            check(signature);
        }

        @Override
        public void visitTryCatchBlock(@Nullable Label start, @Nullable Label end, @Nullable Label handler, @Nullable String type) {
            check(type);
        }
    }
}
