package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives every visitor hook directly, the way ASM would.
 *
 * <p>{@link BundleScannerTest} covers the hooks through real class files, which is the honest
 * end to end path. It cannot reach all of them: {@code visitLocalVariable} never fires under
 * {@link ForbiddenPackageVisitor#PARSING_OPTIONS}, and several annotation hooks need bytecode
 * shapes no small fixture produces. Polarion's own visitor carries each of these hooks, so this
 * test states what each one must do.</p>
 */
class ForbiddenPackageVisitorTest {

    private static final String FORBIDDEN = "Ljavax/servlet/Filter;";

    private PackageRules rules;
    private ForbiddenPackageVisitor visitor;

    @BeforeEach
    void setUp() throws IOException {
        PackageRules.Builder builder = PackageRules.builder();
        RulesetLoader.loadBuiltin("jakarta", builder);
        rules = builder.build();
        visitor = new ForbiddenPackageVisitor(rules);
    }

    @Test
    void visit_shouldLeaveClassNameNullWhenAsmReportsNone() {
        visitor.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, null, null, null, null);

        assertThat(visitor.className()).isNull();
        assertThat(visitor.detections()).isEmpty();
    }

    @Test
    void visit_shouldReadNameSignatureSuperclassAndInterfaces() {
        visitor.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/Legacy",
                "Ljava/lang/Object;Ljavax/ws/rs/core/Feature;", "javax/servlet/http/HttpServlet",
                new String[]{"javax/ejb/SessionBean"});

        assertThat(visitor.className()).isEqualTo("com.example.Legacy");
        assertThat(visitor.detections().keySet())
                .containsExactlyInAnyOrder("javax.ws.rs", "javax.servlet", "javax.ejb");
    }

    @Test
    void visitTypeAnnotation_shouldReadTheClassAnnotationDescriptor() {
        visitor.visitTypeAnnotation(0, null, "Ljavax/validation/Valid;", true);

        assertThat(visitor.detections()).containsKey("javax.validation");
    }

    @Test
    void fieldVisitor_shouldReadAnnotationsOnAField() {
        FieldVisitor field = visitor.visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/String;", null, null);

        field.visitAnnotation("Ljavax/inject/Inject;", true);
        field.visitTypeAnnotation(0, null, "Ljavax/validation/NotNull;", true);

        assertThat(visitor.detections().keySet()).containsExactlyInAnyOrder("javax.inject", "javax.validation");
    }

    @Test
    void visitMethod_shouldReadDescriptorSignatureAndDeclaredExceptions() {
        visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()Ljavax/mail/Session;",
                "()Ljava/util/List<Ljavax/jms/Message;>;", new String[]{"javax/transaction/RollbackException"});

        assertThat(visitor.detections().keySet())
                .containsExactlyInAnyOrder("javax.mail", "javax.jms", "javax.transaction");
    }

    @Test
    void methodVisitor_shouldReadEveryAnnotationHook() {
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);

        method.visitAnnotation("Ljavax/annotation/PostConstruct;", true);
        method.visitTypeAnnotation(0, null, "Ljavax/persistence/Entity;", true);
        method.visitParameterAnnotation(0, "Ljavax/el/ELContext;", true);
        method.visitInsnAnnotation(0, null, "Ljavax/websocket/Session;", true);
        method.visitLocalVariableAnnotation(0, null, new Label[]{new Label()}, new Label[]{new Label()},
                new int[]{0}, "Ljavax/json/JsonObject;", true);

        assertThat(visitor.detections().keySet()).containsExactlyInAnyOrder(
                "javax.annotation", "javax.persistence", "javax.el", "javax.websocket", "javax.json");
    }

    @Test
    void methodVisitor_shouldReadFieldAndMethodInstructions() {
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);

        method.visitFieldInsn(Opcodes.GETSTATIC, "javax/faces/context/FacesContext", "instance", "Ljavax/jws/WebService;");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "javax/xml/bind/JAXBContext", "newInstance",
                "()Ljavax/xml/soap/SOAPMessage;", false);

        assertThat(visitor.detections().keySet()).containsExactlyInAnyOrder(
                "javax.faces", "javax.jws", "javax.xml.bind", "javax.xml.soap");
    }

    @Test
    void visitLdcInsn_shouldReadStringConstantsAndIgnoreEverythingElse() {
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);

        method.visitLdcInsn(42);
        method.visitLdcInsn(3.5d);
        assertThat(visitor.detections()).isEmpty();

        method.visitLdcInsn("javax.servlet.Filter");
        assertThat(visitor.detections()).containsKey("javax.servlet");
    }

    @Test
    void visitLocalVariable_shouldReadDescriptorAndSignature() {
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);

        method.visitLocalVariable("filter", FORBIDDEN, "Ljava/util/List<Ljavax/resource/cci/Record;>;",
                new Label(), new Label(), 1);

        assertThat(visitor.detections().keySet()).containsExactlyInAnyOrder("javax.servlet", "javax.resource");
    }

    @Test
    void visitTryCatchBlock_shouldReadTheCaughtType() {
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);

        method.visitTryCatchBlock(new Label(), new Label(), new Label(), "javax/ejb/EJBException");

        assertThat(visitor.detections()).containsKey("javax.ejb");
    }

    @Test
    void detections_shouldKeepTheFirstValueWhichMatchedAPackage() {
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);

        method.visitTypeInsn(Opcodes.NEW, "javax/servlet/ServletException");
        method.visitTypeInsn(Opcodes.NEW, "javax/servlet/http/Cookie");

        assertThat(visitor.detections()).containsEntry("javax.servlet", "javax.servlet.ServletException");
    }

    @Test
    void hooks_shouldIgnoreNullValues() {
        MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", null, null, null);

        method.visitTypeInsn(Opcodes.NEW, null);
        method.visitFieldInsn(Opcodes.GETSTATIC, null, null, null);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, null, null, null, false);
        method.visitLdcInsn(null);
        method.visitLocalVariable(null, null, null, new Label(), new Label(), 0);
        method.visitTryCatchBlock(new Label(), new Label(), new Label(), null);
        method.visitAnnotation(null, true);

        assertThat(visitor.detections()).isEmpty();
    }

    @Test
    void parsingOptions_shouldStayAtThePolarionValue() {
        assertThat(ForbiddenPackageVisitor.PARSING_OPTIONS).isEqualTo(6);
    }
}
