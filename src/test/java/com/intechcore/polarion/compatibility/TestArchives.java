package com.intechcore.polarion.compatibility;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the class files and jars the scanner tests read.
 *
 * <p>Real class files, written with ASM, because the scanner reads them with ASM. A hand-rolled
 * byte prefix would not survive {@code ClassReader}.</p>
 */
final class TestArchives {

    private TestArchives() {
    }

    /**
     * Starts building a class.
     */
    static ClassBuilder cls(String internalName) {
        return new ClassBuilder(internalName);
    }

    /**
     * Packs the given entries into a jar.
     */
    static byte[] jar(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Builds an ordered entry map.
     */
    static Map<String, byte[]> entries() {
        return new LinkedHashMap<>();
    }

    /**
     * Renders a manifest with the given lines.
     */
    static byte[] manifest(List<String> lines) {
        StringBuilder text = new StringBuilder("Manifest-Version: 1.0\r\n");
        lines.forEach(line -> text.append(line).append("\r\n"));
        text.append("\r\n");
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Emits one class holding whatever the test needs the scanner to see.
     */
    static final class ClassBuilder {

        private final String internalName;
        private final List<String> interfaces = new ArrayList<>();
        private final List<String[]> fields = new ArrayList<>();
        private final List<String> annotations = new ArrayList<>();
        private final List<String> typeInstructions = new ArrayList<>();
        private final List<String> stringConstants = new ArrayList<>();
        private final List<String[]> methodInstructions = new ArrayList<>();
        private String superName = "java/lang/Object";
        private String methodDescriptor = "()V";
        private String catchType;

        private ClassBuilder(String internalName) {
            this.internalName = internalName;
        }

        ClassBuilder withSuper(String name) {
            this.superName = name;
            return this;
        }

        ClassBuilder withInterface(String name) {
            interfaces.add(name);
            return this;
        }

        ClassBuilder withField(String name, String descriptor) {
            fields.add(new String[]{name, descriptor});
            return this;
        }

        ClassBuilder withAnnotation(String descriptor) {
            annotations.add(descriptor);
            return this;
        }

        ClassBuilder withMethodDescriptor(String descriptor) {
            this.methodDescriptor = descriptor;
            return this;
        }

        ClassBuilder withTypeInstruction(String type) {
            typeInstructions.add(type);
            return this;
        }

        ClassBuilder withStringConstant(String value) {
            stringConstants.add(value);
            return this;
        }

        ClassBuilder withMethodInstruction(String owner, String name, String descriptor) {
            methodInstructions.add(new String[]{owner, name, descriptor});
            return this;
        }

        ClassBuilder withCatchType(String type) {
            this.catchType = type;
            return this;
        }

        byte[] build() {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, superName,
                    interfaces.isEmpty() ? null : interfaces.toArray(new String[0]));
            annotations.forEach(descriptor -> writer.visitAnnotation(descriptor, true).visitEnd());
            fields.forEach(field -> writer.visitField(Opcodes.ACC_PRIVATE, field[0], field[1], null, null).visitEnd());
            writeMethod(writer);
            writer.visitEnd();
            return writer.toByteArray();
        }

        private void writeMethod(ClassWriter writer) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", methodDescriptor, null, null);
            method.visitCode();
            Label start = new Label();
            Label end = new Label();
            Label handler = new Label();
            if (catchType != null) {
                method.visitTryCatchBlock(start, end, handler, catchType);
            }
            method.visitLabel(start);
            for (String type : typeInstructions) {
                method.visitTypeInsn(Opcodes.NEW, type);
                method.visitInsn(Opcodes.POP);
            }
            for (String value : stringConstants) {
                method.visitLdcInsn(value);
                method.visitInsn(Opcodes.POP);
            }
            for (String[] instruction : methodInstructions) {
                method.visitMethodInsn(Opcodes.INVOKESTATIC, instruction[0], instruction[1], instruction[2], false);
            }
            method.visitLabel(end);
            method.visitInsn(Opcodes.RETURN);
            if (catchType != null) {
                method.visitLabel(handler);
                method.visitInsn(Opcodes.POP);
                method.visitInsn(Opcodes.RETURN);
            }
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
    }
}
