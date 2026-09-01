package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityScannerTest {

    @TempDir
    private Path workDirectory;

    private CompatibilityScanner scanner;

    @BeforeEach
    void setUp() {
        this.scanner = new CompatibilityScanner(CheckMojo.DEFAULT_FORBIDDEN_PACKAGES, CheckMojo.DEFAULT_ALLOWED_PACKAGES);
    }

    @Test
    void scanDirectory_shouldReportInternalAndDottedReferences() throws IOException {
        Path classes = this.workDirectory.resolve("classes");
        ClassFiles.writeClass(classes, "com.example.Servlet", "Ljavax/servlet/http/HttpServlet;", "javax.xml.bind.JAXBContext");

        List<ForbiddenReference> found = this.scanner.scanDirectory(classes);

        assertThat(found).extracting(ForbiddenReference::reference)
                .containsExactlyInAnyOrder("javax.servlet.http.HttpServlet", "javax.xml.bind.JAXBContext");
        assertThat(found).extracting(ForbiddenReference::className).containsOnly("com.example.Servlet");
        assertThat(found).extracting(ForbiddenReference::source).containsOnly("classes");
    }

    @Test
    void scanDirectory_shouldIgnoreCleanClasses() throws IOException {
        Path classes = this.workDirectory.resolve("classes");
        ClassFiles.writeClass(classes, "com.example.Clean", "Ljakarta/servlet/http/HttpServlet;", "java/lang/String");

        assertThat(this.scanner.scanDirectory(classes)).isEmpty();
    }

    @Test
    void scanDirectory_shouldIgnoreAllowedSubPackage() throws IOException {
        Path classes = this.workDirectory.resolve("classes");
        ClassFiles.writeClass(classes, "com.example.Processor", "Ljavax/annotation/processing/Processor;", "javax.annotation.processing");

        assertThat(this.scanner.scanDirectory(classes)).isEmpty();
    }

    @Test
    void scanDirectory_shouldReturnEmptyForMissingDirectory() throws IOException {
        assertThat(this.scanner.scanDirectory(this.workDirectory.resolve("absent"))).isEmpty();
    }

    @Test
    void scanJar_shouldReportReferencesAndSkipNonClassEntries() throws IOException {
        Path jar = ClassFiles.writeJar(this.workDirectory, "legacy.jar", "com.example.Mail", "Ljavax/mail/Session;");

        List<ForbiddenReference> found = this.scanner.scanJar(jar);

        assertThat(found).singleElement().satisfies(reference -> {
            assertThat(reference.source()).isEqualTo("legacy.jar");
            assertThat(reference.className()).isEqualTo("com.example.Mail");
            assertThat(reference.reference()).isEqualTo("javax.mail.Session");
        });
        assertThat(found.get(0)).hasToString("legacy.jar -> com.example.Mail references javax.mail.Session");
    }

    @Test
    void scanJar_shouldHonorCustomPackageLists() throws IOException {
        Path jar = ClassFiles.writeJar(this.workDirectory, "custom.jar", "com.example.Legacy", "Lcom/legacy/Api;");
        CompatibilityScanner custom = new CompatibilityScanner(List.of("com.legacy"), List.of());

        assertThat(custom.scanJar(jar)).extracting(ForbiddenReference::reference).containsExactly("com.legacy.Api");
    }

    @Test
    void scanDirectory_shouldKeepUnderscoreAndInnerClassNames() throws IOException {
        Path classes = this.workDirectory.resolve("classes");
        ClassFiles.writeClass(classes, "com.example.Inner", "Ljavax/servlet/Foo_Bar$Inner;");

        assertThat(this.scanner.scanDirectory(classes)).extracting(ForbiddenReference::reference)
                .containsExactly("javax.servlet.Foo_Bar$Inner");
    }
}
