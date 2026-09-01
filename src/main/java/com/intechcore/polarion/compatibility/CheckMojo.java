package com.intechcore.polarion.compatibility;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fails the build when compiled classes reference a legacy {@code javax} package.
 *
 * <p>The goal scans the module's own classes and every jar below the build directory, which is
 * where a Polarion extension collects its bundled libraries.</p>
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckMojo extends AbstractMojo {

    /** Package prefixes rejected by Polarion 2606 unless the plugin is configured otherwise. */
    public static final List<String> DEFAULT_FORBIDDEN_PACKAGES = List.of(
            "javax.activation",
            "javax.annotation",
            "javax.batch",
            "javax.decorator",
            "javax.ejb",
            "javax.el",
            "javax.enterprise",
            "javax.faces",
            "javax.inject",
            "javax.interceptor",
            "javax.jms",
            "javax.json",
            "javax.jws",
            "javax.mail",
            "javax.persistence",
            "javax.resource",
            "javax.security.auth.message",
            "javax.security.enterprise",
            "javax.security.jacc",
            "javax.servlet",
            "javax.transaction",
            "javax.validation",
            "javax.websocket",
            "javax.ws.rs",
            "javax.xml.bind",
            "javax.xml.soap",
            "javax.xml.ws");

    /** Package prefixes that stay allowed although a forbidden prefix covers them. */
    public static final List<String> DEFAULT_ALLOWED_PACKAGES = List.of("javax.annotation.processing");

    /** Skips the goal entirely. */
    @Parameter(property = "polarion.compatibility.skip", defaultValue = "false")
    private boolean skip;

    /** Reports findings as warnings instead of failing the build. */
    @Parameter(property = "polarion.compatibility.failOnViolation", defaultValue = "true")
    private boolean failOnViolation;

    /** Directory holding the module's own compiled classes. */
    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File classesDirectory;

    /** Directory searched recursively for bundled jars. */
    @Parameter(defaultValue = "${project.build.directory}")
    private File librariesDirectory;

    /** Package prefixes to reject. Replaces {@link #DEFAULT_FORBIDDEN_PACKAGES} when set. */
    @Parameter
    private List<String> forbiddenPackages;

    /** Package prefixes to keep allowed. Replaces {@link #DEFAULT_ALLOWED_PACKAGES} when set. */
    @Parameter
    private List<String> allowedPackages;

    /** Glob patterns matched against a jar file name. A match excludes the jar from the scan. */
    @Parameter
    private List<String> excludedJarNames;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            getLog().info("Polarion compatibility check skipped");
            return;
        }

        CompatibilityScanner scanner = new CompatibilityScanner(
                this.forbiddenPackages == null || this.forbiddenPackages.isEmpty() ? DEFAULT_FORBIDDEN_PACKAGES : this.forbiddenPackages,
                this.allowedPackages == null ? DEFAULT_ALLOWED_PACKAGES : this.allowedPackages);

        List<ForbiddenReference> found;
        try {
            found = scan(scanner);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan for javax references", e);
        }

        report(found);
    }

    private List<ForbiddenReference> scan(CompatibilityScanner scanner) throws IOException {
        List<ForbiddenReference> found = new ArrayList<>();
        if (this.classesDirectory != null) {
            found.addAll(scanner.scanDirectory(this.classesDirectory.toPath()));
        }
        for (Path jar : collectJars()) {
            getLog().debug("Scanning " + jar);
            found.addAll(scanner.scanJar(jar));
        }
        return found;
    }

    private List<Path> collectJars() throws IOException {
        if (this.librariesDirectory == null || !this.librariesDirectory.isDirectory()) {
            return List.of();
        }
        List<PathMatcher> exclusions = buildExclusions();
        try (Stream<Path> files = Files.walk(this.librariesDirectory.toPath())) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> exclusions.stream().noneMatch(matcher -> matcher.matches(path.getFileName())))
                    .toList();
        }
    }

    private List<PathMatcher> buildExclusions() {
        if (this.excludedJarNames == null) {
            return List.of();
        }
        return this.excludedJarNames.stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .toList();
    }

    private void report(List<ForbiddenReference> found) throws MojoFailureException {
        if (found.isEmpty()) {
            getLog().info("Polarion compatibility check passed: no javax references found");
            return;
        }

        String summary = found.size() + " javax reference(s) rejected by Polarion";
        if (this.failOnViolation) {
            found.forEach(reference -> getLog().error(reference.toString()));
            throw new MojoFailureException(summary);
        }
        found.forEach(reference -> getLog().warn(reference.toString()));
        getLog().warn(summary);
    }
}
