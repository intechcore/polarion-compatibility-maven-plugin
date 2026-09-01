package com.intechcore.polarion.compatibility;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckMojoTest {

    @TempDir
    private Path workDirectory;

    private CheckMojo mojo;
    private Path classes;
    private Path libraries;

    @BeforeEach
    void setUp() throws IOException {
        this.classes = Files.createDirectories(this.workDirectory.resolve("classes"));
        this.libraries = Files.createDirectories(this.workDirectory.resolve("libs"));
        this.mojo = new CheckMojo();
        set("classesDirectory", this.classes.toFile());
        set("librariesDirectory", this.libraries.toFile());
        set("failOnViolation", true);
    }

    @Test
    void execute_shouldPassOnCleanBuildOutput() throws IOException {
        ClassFiles.writeClass(this.classes, "com.example.Clean", "Ljakarta/servlet/http/HttpServlet;");

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldFailOnReferenceInClasses() throws IOException {
        ClassFiles.writeClass(this.classes, "com.example.Servlet", "Ljavax/servlet/Filter;");

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("1 javax reference(s)");
    }

    @Test
    void execute_shouldFailOnReferenceInBundledJar() throws IOException {
        ClassFiles.writeJar(this.libraries, "legacy.jar", "com.example.Mail", "Ljavax/mail/Session;");

        assertThatThrownBy(this.mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void execute_shouldWarnWhenFailOnViolationIsFalse() throws IOException {
        ClassFiles.writeClass(this.classes, "com.example.Servlet", "Ljavax/servlet/Filter;");
        set("failOnViolation", false);

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldSkipExcludedJar() throws IOException {
        ClassFiles.writeJar(this.libraries, "legacy.jar", "com.example.Mail", "Ljavax/mail/Session;");
        set("excludedJarNames", List.of("legacy*.jar"));

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldSkipWhenSkipIsTrue() throws IOException {
        ClassFiles.writeClass(this.classes, "com.example.Servlet", "Ljavax/servlet/Filter;");
        set("skip", true);

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldHonorCustomPackageLists() throws IOException {
        ClassFiles.writeClass(this.classes, "com.example.Legacy", "Lcom/legacy/Api;", "Ljavax/servlet/Filter;");
        set("forbiddenPackages", List.of("com.legacy"));
        set("allowedPackages", List.of());

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("1 javax reference(s)");
    }

    @Test
    void execute_shouldTolerateMissingDirectories() throws IOException {
        set("classesDirectory", this.workDirectory.resolve("absent").toFile());
        set("librariesDirectory", this.workDirectory.resolve("absent").toFile());

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldWrapScanFailure() throws IOException {
        Path broken = this.libraries.resolve("broken.jar");
        Files.writeString(broken, "not a jar");

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Failed to scan");
    }

    @Test
    void execute_shouldRejectUnreadableClassFile() throws IOException {
        Path file = this.classes.resolve("Broken.class");
        Files.write(file, new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 61, 0, 2, 99});

        assertThatThrownBy(this.mojo::execute).isInstanceOf(MojoExecutionException.class);
    }

    @Test
    void defaults_shouldCoverJakartaMigratedPackages() {
        assertThat(CheckMojo.DEFAULT_FORBIDDEN_PACKAGES).contains("javax.servlet", "javax.ws.rs", "javax.xml.bind");
        assertThat(CheckMojo.DEFAULT_ALLOWED_PACKAGES).containsExactly("javax.annotation.processing");
    }

    @Test
    void execute_shouldFallBackToDefaultsForEmptyPackageList() throws IOException {
        ClassFiles.writeClass(this.classes, "com.example.Servlet", "Ljavax/servlet/Filter;");
        set("forbiddenPackages", List.of());

        assertThatThrownBy(this.mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void execute_shouldTolerateUnsetDirectories() {
        set("classesDirectory", null);
        set("librariesDirectory", null);

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    private void set(String name, Object value) {
        try {
            var field = CheckMojo.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this.mojo, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
