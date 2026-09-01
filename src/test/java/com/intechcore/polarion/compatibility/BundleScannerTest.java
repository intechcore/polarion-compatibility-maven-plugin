package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class BundleScannerTest {

    @TempDir
    Path tempDir;

    private PackageRules rules;

    @BeforeEach
    void setUp() throws IOException {
        PackageRules.Builder builder = PackageRules.builder();
        RulesetLoader.loadBuiltin("jakarta", builder);
        rules = builder.build();
    }

    private Path writeJar(Map<String, byte[]> entries) throws IOException {
        Path jar = tempDir.resolve("bundle.jar");
        Files.write(jar, TestArchives.jar(entries));
        return jar;
    }

    private BundleScanner.ScanResult scan(Map<String, byte[]> entries) throws IOException {
        return BundleScanner.builder(rules).build().scan(writeJar(entries));
    }

    @Test
    void scan_shouldFindForbiddenTypeInAMethodDescriptor() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("com/example/Own.class", TestArchives.cls("com/example/Own")
                .withMethodDescriptor("(Ljavax/servlet/ServletRequest;)V")
                .build());

        assertThat(scan(entries).violations()).singleElement()
                .extracting(Violation::kind, Violation::subject, Violation::source)
                .containsExactly(Violation.Kind.CLASS_REFERENCE, "javax.servlet", "com.example.Own");
    }

    @Test
    void scan_shouldFindForbiddenSuperclassInterfaceFieldAndAnnotation() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("a/A.class", TestArchives.cls("a/A").withSuper("javax/servlet/http/HttpServlet").build());
        entries.put("b/B.class", TestArchives.cls("b/B").withInterface("javax/servlet/Filter").build());
        entries.put("c/C.class", TestArchives.cls("c/C").withField("ctx", "Ljavax/servlet/ServletContext;").build());
        entries.put("d/D.class", TestArchives.cls("d/D").withAnnotation("Ljavax/annotation/Resource;").build());

        assertThat(scan(entries).violations())
                .extracting(Violation::source, Violation::subject)
                .containsExactlyInAnyOrder(
                        tuple("a.A", "javax.servlet"),
                        tuple("b.B", "javax.servlet"),
                        tuple("c.C", "javax.servlet"),
                        tuple("d.D", "javax.annotation"));
    }

    @Test
    void scan_shouldFindForbiddenTypeInInstructionsAndCatchBlocks() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("a/A.class", TestArchives.cls("a/A").withTypeInstruction("javax/mail/Session").build());
        entries.put("b/B.class", TestArchives.cls("b/B")
                .withMethodInstruction("javax/ws/rs/core/Response", "ok", "()V").build());
        entries.put("c/C.class", TestArchives.cls("c/C").withCatchType("javax/mail/MessagingException").build());

        assertThat(scan(entries).violations())
                .extracting(Violation::subject)
                .containsExactlyInAnyOrder("javax.mail", "javax.ws.rs", "javax.mail");
    }

    @Test
    void scan_shouldFindForbiddenPackageInAStringConstant() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("a/A.class", TestArchives.cls("a/A").withStringConstant("javax.servlet.Filter").build());

        assertThat(scan(entries).violations()).singleElement()
                .extracting(Violation::subject, Violation::detail)
                .containsExactly("javax.servlet", "javax.servlet.Filter");
    }

    @Test
    void scan_shouldFollowPolarionAndFlagALowercaseStringConstant() throws IOException {
        // Polarion checks ldc constants by substring, so a property name matches too.
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("a/A.class", TestArchives.cls("a/A").withStringConstant("javax.activation.debug").build());

        assertThat(scan(entries).violations()).singleElement()
                .extracting(Violation::subject).isEqualTo("javax.activation");
    }

    @Test
    void scan_shouldIgnoreAStaticFinalStringWhichIsNeverLoaded() throws IOException {
        // A ConstantValue attribute is not visited by ASM, so Polarion misses it and so do we.
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("a/A.class", TestArchives.cls("a/A").withField("prop", "Ljava/lang/String;").build());

        assertThat(scan(entries).violations()).isEmpty();
    }

    @Test
    void scan_shouldFindForbiddenReferenceInNestedJar() throws IOException {
        Map<String, byte[]> nested = TestArchives.entries();
        nested.put("org/legacy/Dep.class", TestArchives.cls("org/legacy/Dep")
                .withTypeInstruction("javax/ws/rs/core/Response").build());

        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("com/example/Own.class", TestArchives.cls("com/example/Own").build());
        entries.put("webapp/demo/WEB-INF/lib/legacy-1.0.jar", TestArchives.jar(nested));

        BundleScanner.ScanResult result = scan(entries);

        assertThat(result.jarsScanned()).isEqualTo(1);
        assertThat(result.violations()).singleElement()
                .extracting(Violation::subject, Violation::container, Violation::source)
                .containsExactly("javax.ws.rs", "bundle.jar!/webapp/demo/WEB-INF/lib/legacy-1.0.jar", "org.legacy.Dep");
    }

    @Test
    void scan_shouldIgnorePackagesWhichStayedInTheJdk() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("com/example/Xml.class", TestArchives.cls("com/example/Xml")
                .withMethodDescriptor("(Ljavax/xml/transform/stream/StreamResult;)V")
                .withTypeInstruction("javax/xml/parsers/DocumentBuilderFactory")
                .withField("subject", "Ljavax/security/auth/Subject;")
                .withMethodInstruction("javax/xml/crypto/dsig/XMLSignature", "sign", "()V")
                .build());

        assertThat(scan(entries).violations()).isEmpty();
    }

    @Test
    void scan_shouldFollowPolarionAndFlagJdkSubpackagesOfAJakartaParent() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("a/A.class", TestArchives.cls("a/A").withTypeInstruction("javax/transaction/xa/XAResource").build());

        assertThat(scan(entries).violations()).singleElement()
                .extracting(Violation::subject).isEqualTo("javax.transaction");
    }

    @Test
    void scan_shouldSkipExcludedJar() throws IOException {
        Map<String, byte[]> nested = TestArchives.entries();
        nested.put("org/legacy/Dep.class", TestArchives.cls("org/legacy/Dep")
                .withTypeInstruction("javax/servlet/Filter").build());

        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("webapp/demo/WEB-INF/lib/fop-core-2.11.jar", TestArchives.jar(nested));

        BundleScanner.ScanResult result = BundleScanner.builder(rules)
                .excludedJars(new GlobMatcher(List.of("fop-core-*.jar")))
                .build()
                .scan(writeJar(entries));

        assertThat(result.violations()).isEmpty();
        assertThat(result.excludedJars()).containsExactly("bundle.jar!/webapp/demo/WEB-INF/lib/fop-core-2.11.jar");
    }

    @Test
    void scan_shouldReportEachForbiddenPackageOncePerClass() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("a/A.class", TestArchives.cls("a/A")
                .withTypeInstruction("javax/servlet/Filter")
                .withTypeInstruction("javax/servlet/http/HttpServletRequest")
                .withMethodDescriptor("(Ljavax/servlet/ServletContext;)V")
                .build());

        assertThat(scan(entries).violations()).hasSize(1);
    }

    @Test
    void scan_shouldCheckEveryJarManifestIncludingNestedOnes() throws IOException {
        Map<String, byte[]> nested = TestArchives.entries();
        nested.put("META-INF/MANIFEST.MF", TestArchives.manifest(List.of("Bundle-SymbolicName: javax.mail")));

        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("META-INF/MANIFEST.MF", TestArchives.manifest(List.of(
                "Import-Package: org.osgi.framework,javax.servlet;version=\"3.1\"")));
        entries.put("webapp/demo/WEB-INF/lib/javax.mail-1.6.2.jar", TestArchives.jar(nested));

        assertThat(scan(entries).violations())
                .allMatch(v -> v.kind() == Violation.Kind.MANIFEST_HEADER)
                .extracting(Violation::subject)
                .containsExactlyInAnyOrder("javax.servlet", "javax.mail");
    }

    @Test
    void scan_shouldFollowPolarionAndFlagAForbiddenPackageInBundleClassPath() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("META-INF/MANIFEST.MF", TestArchives.manifest(List.of(
                "Bundle-ClassPath: .,webapp/demo/WEB-INF/lib/javax.mail-1.6.2.jar")));

        assertThat(scan(entries).violations()).singleElement()
                .extracting(Violation::kind, Violation::subject)
                .containsExactly(Violation.Kind.MANIFEST_HEADER, "javax.mail");
    }

    @Test
    void scan_shouldAcceptJakartaManifestHeaders() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("META-INF/MANIFEST.MF", TestArchives.manifest(List.of(
                "Import-Package: org.osgi.framework,jakarta.annotation;bundle-symbolic-name=\"jakarta.annotation-api\"",
                "Require-Bundle: jakarta.servlet-api,jakarta.ws.rs-api")));

        assertThat(scan(entries).violations()).isEmpty();
    }

    @Test
    void scan_shouldFlagLegacyWebXmlNamespace() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("webapp/demo/WEB-INF/web.xml",
                "<web-app xmlns=\"http://xmlns.jcp.org/xml/ns/javaee\" version=\"4.0\"/>".getBytes(StandardCharsets.UTF_8));

        assertThat(scan(entries).violations()).singleElement()
                .extracting(Violation::kind, Violation::subject, Violation::replacement)
                .containsExactly(Violation.Kind.WEB_XML_NAMESPACE, "http://xmlns.jcp.org/xml/ns/javaee",
                        "https://jakarta.ee/xml/ns/jakartaee");
    }

    @Test
    void scan_shouldAcceptJakartaWebXmlNamespace() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("webapp/demo/WEB-INF/web.xml",
                "<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"6.1\"/>".getBytes(StandardCharsets.UTF_8));

        assertThat(scan(entries).violations()).isEmpty();
    }

    @Test
    void scan_shouldFlagJspMentioningAForbiddenPackage() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("webapp/demo/pages/index.jsp",
                "<%@ page import=\"javax.servlet.http.HttpSession\" %>".getBytes(StandardCharsets.UTF_8));

        assertThat(scan(entries).violations()).singleElement()
                .extracting(Violation::kind, Violation::subject)
                .containsExactly(Violation.Kind.JSP_PAGE, "javax.servlet");
    }

    @Test
    void scan_shouldHonourDisabledChecks() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("a/A.class", TestArchives.cls("a/A").withTypeInstruction("javax/servlet/Filter").build());
        entries.put("META-INF/MANIFEST.MF", TestArchives.manifest(List.of("Require-Bundle: javax.servlet-api")));

        BundleScanner.ScanResult result = BundleScanner.builder(rules)
                .checkClasses(false)
                .checkManifest(false)
                .build()
                .scan(writeJar(entries));

        assertThat(result.violations()).isEmpty();
    }

    @Test
    void scan_shouldRecordUnreadableClass() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("com/example/Broken.class", "not a class".getBytes(StandardCharsets.UTF_8));

        BundleScanner.ScanResult result = scan(entries);

        assertThat(result.unreadableClasses()).containsExactly("bundle.jar!/com/example/Broken.class");
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void scan_shouldStopAtNestingLimit() throws IOException {
        Map<String, byte[]> innermost = TestArchives.entries();
        innermost.put("org/legacy/Dep.class", TestArchives.cls("org/legacy/Dep")
                .withTypeInstruction("javax/servlet/Filter").build());

        Map<String, byte[]> middle = TestArchives.entries();
        middle.put("inner.jar", TestArchives.jar(innermost));

        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("outer.jar", TestArchives.jar(middle));

        BundleScanner.ScanResult result = BundleScanner.builder(rules).maxDepth(1).build().scan(writeJar(entries));

        assertThat(result.violations()).isEmpty();
        assertThat(result.skippedJars()).containsExactly("bundle.jar!/outer.jar!/inner.jar");
    }
}
