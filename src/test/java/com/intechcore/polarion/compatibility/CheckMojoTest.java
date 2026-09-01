package com.intechcore.polarion.compatibility;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckMojoTest {

    @TempDir
    private Path workDirectory;

    private CheckMojo mojo;

    /**
     * Maven applies {@code @Parameter(defaultValue = ...)} through its configurator, which a
     * plain {@code new CheckMojo()} never runs. Every default the test relies on is set here.
     */
    @BeforeEach
    void setUp() {
        this.mojo = new CheckMojo();
        set("packaging", "jar");
        set("rulesets", List.of("jakarta"));
        set("checkClasses", true);
        set("checkManifest", true);
        set("checkDescriptors", true);
        set("failOnViolation", true);
        set("maxNestingDepth", 5);
        set("maxSourcesPerPackage", 5);
    }

    private void set(String name, Object value) {
        try {
            Field field = CheckMojo.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this.mojo, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set " + name, e);
        }
    }

    private void givenJar(Map<String, byte[]> entries) throws IOException {
        Path jar = this.workDirectory.resolve("bundle.jar");
        Files.write(jar, TestArchives.jar(entries));
        set("jarFile", jar.toFile());
    }

    private static Map<String, byte[]> clean() {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("com/example/Clean.class", TestArchives.cls("com/example/Clean")
                .withSuper("jakarta/servlet/http/HttpServlet").build());
        return entries;
    }

    private static Map<String, byte[]> withServletReference() {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("com/example/Legacy.class", TestArchives.cls("com/example/Legacy")
                .withSuper("javax/servlet/http/HttpServlet").build());
        return entries;
    }

    @Test
    void execute_shouldPassOnACleanBundle() throws IOException {
        givenJar(clean());

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldFailOnAForbiddenReference() throws IOException {
        givenJar(withServletReference());

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("javax.servlet");
    }

    @Test
    void execute_shouldFailOnAReferenceInANestedJar() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("webapp/demo/WEB-INF/lib/legacy-1.0.jar", TestArchives.jar(withServletReference()));
        givenJar(entries);

        assertThatThrownBy(this.mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void execute_shouldWarnWhenFailOnViolationIsFalse() throws IOException {
        givenJar(withServletReference());
        set("failOnViolation", false);

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldSkipWhenSkipIsTrue() throws IOException {
        givenJar(withServletReference());
        set("skip", true);

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldSkipForPomPackaging() throws IOException {
        givenJar(withServletReference());
        set("packaging", "pom");

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldSkipExcludedJar() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("webapp/demo/WEB-INF/lib/legacy-1.0.jar", TestArchives.jar(withServletReference()));
        givenJar(entries);
        set("excludedJars", List.of("legacy-*.jar"));

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldSkipWhenTheJarIsMissing() {
        set("jarFile", this.workDirectory.resolve("absent.jar").toFile());

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldFailOnAMissingJarWhenConfiguredTo() {
        set("jarFile", this.workDirectory.resolve("absent.jar").toFile());
        set("failOnMissingJar", true);

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("No jar to scan");
    }

    @Test
    void execute_shouldRejectAnUnknownRuleset() throws IOException {
        givenJar(clean());
        set("rulesets", List.of("nope"));

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Cannot load rules");
    }

    @Test
    void execute_shouldApplyInlineRules() throws IOException {
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("com/example/Legacy.class", TestArchives.cls("com/example/Legacy")
                .withSuper("com/legacy/Api").build());
        givenJar(entries);
        set("rules", List.of("com.legacy -> com.modern"));

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("com.legacy");
    }

    @Test
    void execute_shouldReadARulesetFile() throws IOException {
        Path ruleset = this.workDirectory.resolve("extra-rules.txt");
        Files.writeString(ruleset, "com.legacy -> com.modern\n", StandardCharsets.UTF_8);
        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("com/example/Legacy.class", TestArchives.cls("com/example/Legacy")
                .withSuper("com/legacy/Api").build());
        givenJar(entries);
        set("rulesetFiles", List.of(ruleset.toFile()));

        assertThatThrownBy(this.mojo::execute).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void execute_shouldDoNothingWhenTheRulesetListIsEmpty() throws IOException {
        givenJar(withServletReference());
        set("rulesets", List.of());

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldFallBackToTheJakartaRulesetWhenNoneIsConfigured() throws IOException {
        givenJar(withServletReference());
        set("rulesets", null);

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("javax.servlet");
    }


    @Test
    void execute_shouldSkipWhenTheJarIsNotConfigured() {
        set("jarFile", null);

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldReportSkippedAndUnreadableEntries() throws IOException {
        Map<String, byte[]> innermost = TestArchives.entries();
        innermost.put("org/legacy/Dep.class", TestArchives.cls("org/legacy/Dep")
                .withTypeInstruction("javax/servlet/Filter").build());

        Map<String, byte[]> middle = TestArchives.entries();
        middle.put("inner.jar", TestArchives.jar(innermost));

        Map<String, byte[]> entries = TestArchives.entries();
        entries.put("outer.jar", TestArchives.jar(middle));
        entries.put("com/example/Broken.class", "not a class".getBytes(StandardCharsets.UTF_8));
        givenJar(entries);
        set("maxNestingDepth", 1);

        assertThatCode(this.mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void execute_shouldWrapAScanFailure() throws IOException {
        // The local header survives, the deflated entry data stops mid stream, so the scanner
        // fails while reading the entry rather than while opening the jar.
        Path jar = this.workDirectory.resolve("truncated.jar");
        byte[] bytes = TestArchives.jar(withServletReference());
        Files.write(jar, Arrays.copyOf(bytes, bytes.length / 2));
        set("jarFile", jar.toFile());

        assertThatThrownBy(this.mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Cannot scan");
    }
}
