package com.intechcore.polarion.compatibility;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fails the build when the packaged jar, or any jar nested inside it, refers to a forbidden package.
 *
 * <p>Example usage in pom.xml:</p>
 * <pre>{@code
 * <plugin>
 *     <groupId>com.intechcore</groupId>
 *     <artifactId>polarion-compatibility-maven-plugin</artifactId>
 *     <executions>
 *         <execution>
 *             <goals>
 *                 <goal>check</goal>
 *             </goals>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
// Maven's configurator assigns every @Parameter field by reflection. No static analysis can
// see that, so each one otherwise reports as never assigned or as an empty collection.
@SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
public class CheckMojo extends AbstractMojo {

    /**
     * Rulesets used when {@code rulesets} is not configured at all.
     */
    private static final List<String> DEFAULT_RULESETS = List.of("jakarta");

    @Parameter(defaultValue = "${project.packaging}", readonly = true)
    private String packaging;

    /**
     * The jar to scan. Defaults to the artifact this project produces.
     */
    @Parameter(property = "polarion.compatibility.jarFile", defaultValue = "${project.build.directory}/${project.build.finalName}.jar")
    private File jarFile;

    /**
     * Bundled rulesets to load: {@code jakarta}, {@code jakarta-extended}. Omitting the element
     * loads {@code jakarta}. An empty element loads none, which is how a project runs on its own
     * {@code rulesetFiles} or {@code rules} alone.
     */
    @Parameter
    private List<String> rulesets;

    /**
     * Ruleset files in the project, read after the bundled rulesets.
     */
    @Parameter
    private List<File> rulesetFiles;

    /**
     * Extra rules in ruleset line format, applied last so they override everything else.
     * Use {@code javax.foo -> jakarta.foo} to forbid and {@code !javax.foo.bar} to allow.
     */
    @Parameter
    private List<String> rules;

    /**
     * Nested jars to skip, as globs matched against the jar name or the full nested path,
     * for example {@code fop-core-*.jar}.
     */
    @Parameter
    private List<String> excludedJars;

    /**
     * Whether to scan compiled classes.
     */
    @Parameter(property = "polarion.compatibility.checkClasses", defaultValue = "true")
    private boolean checkClasses;

    /**
     * Whether to check the OSGi headers of the bundle manifest.
     */
    @Parameter(property = "polarion.compatibility.checkManifest", defaultValue = "true")
    private boolean checkManifest;

    /**
     * Whether to check deployment descriptors for a legacy Java EE schema.
     */
    @Parameter(property = "polarion.compatibility.checkDescriptors", defaultValue = "true")
    private boolean checkDescriptors;

    /**
     * How deep nested jars are followed.
     */
    @Parameter(property = "polarion.compatibility.maxNestingDepth", defaultValue = "5")
    private int maxNestingDepth;

    /**
     * How many offending classes to list per forbidden package.
     */
    @Parameter(property = "polarion.compatibility.maxSourcesPerPackage", defaultValue = "5")
    private int maxSourcesPerPackage;

    /**
     * Whether a finding fails the build. Set 'false' to survey a project without breaking it.
     */
    @Parameter(property = "polarion.compatibility.failOnViolation", defaultValue = "true")
    private boolean failOnViolation;

    /**
     * Whether to fail when the jar to scan does not exist.
     */
    @Parameter(property = "polarion.compatibility.failOnMissingJar", defaultValue = "false")
    private boolean failOnMissingJar;

    /**
     * Skips the check entirely.
     */
    @Parameter(property = "polarion.compatibility.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Creates the goal. Maven instantiates it and injects the parameters above.
     */
    public CheckMojo() {
        // Declared only so javadoc has a constructor to document: an implicit one cannot carry
        // a comment, and maven-javadoc-plugin runs with failOnWarnings. Maven assigns every
        // @Parameter field by reflection after construction, so there is nothing to do here.
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Forbidden packages check skipped");
            return;
        }
        if ("pom".equals(packaging)) {
            getLog().debug("Forbidden packages check skipped for pom packaging");
            return;
        }
        if (jarFile == null || !jarFile.isFile()) {
            handleMissingJar();
            return;
        }
        PackageRules packageRules = loadRules();
        if (packageRules.isEmpty()) {
            getLog().warn("Forbidden packages check has no rules, nothing to do");
            return;
        }
        report(scan(packageRules));
    }

    private void handleMissingJar() throws MojoExecutionException {
        String message = "No jar to scan at " + jarFile;
        if (failOnMissingJar) {
            throw new MojoExecutionException(message);
        }
        getLog().info(message + ", forbidden packages check skipped");
    }

    private @NotNull BundleScanner.ScanResult scan(@NotNull PackageRules packageRules) throws MojoExecutionException {
        BundleScanner scanner = BundleScanner.builder(packageRules)
                .excludedJars(new GlobMatcher(excludedJars == null ? List.of() : excludedJars))
                .maxDepth(maxNestingDepth)
                .checkClasses(checkClasses)
                .checkManifest(checkManifest)
                .checkDescriptors(checkDescriptors)
                .debug(message -> getLog().debug(message))
                .build();
        Path path = jarFile.toPath();
        long started = System.nanoTime();
        try {
            BundleScanner.ScanResult result = scanner.scan(path);
            long millis = (System.nanoTime() - started) / 1_000_000L;
            getLog().info(String.format("Scanned %s: %d classes in %d nested jar(s), %d ms",
                    path.getFileName(), result.classesScanned(), result.jarsScanned(), millis));
            return result;
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot scan " + path, e);
        }
    }

    private void report(@NotNull BundleScanner.ScanResult result) throws MojoFailureException {
        result.excludedJars().forEach(jar -> getLog().info("Excluded from the scan: " + jar));
        result.skippedJars().forEach(jar -> getLog().warn("Not scanned, nesting limit reached: " + jar));
        result.unreadableClasses().forEach(entry -> getLog().warn("Not scanned, unreadable class: " + entry));

        List<Violation> violations = result.violations();
        if (violations.isEmpty()) {
            getLog().info("No forbidden packages found");
            return;
        }
        ViolationReport report = new ViolationReport(violations, maxSourcesPerPackage);
        List<String> lines = new ArrayList<>();
        lines.add("Forbidden packages found in " + jarFile.getName());
        lines.addAll(report.lines());
        lines.add(report.summary());
        if (failOnViolation) {
            lines.forEach(line -> getLog().error(line));
            getLog().error("Upgrade or replace the dependency, or exclude the jar with <excludedJars>");
            throw new MojoFailureException("Forbidden packages found in " + jarFile.getName() + ": "
                    + report.summary() + ": " + String.join(", ", report.subjects()));
        }
        lines.forEach(line -> getLog().warn(line));
    }

    private @NotNull PackageRules loadRules() throws MojoExecutionException {
        PackageRules.Builder builder = PackageRules.builder();
        try {
            for (String ruleset : rulesets == null ? DEFAULT_RULESETS : rulesets) {
                RulesetLoader.loadBuiltin(ruleset, builder);
            }
            for (File file : rulesetFiles == null ? List.<File>of() : rulesetFiles) {
                RulesetLoader.loadFile(file.toPath(), builder);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot load rules", e);
        }
        if (rules != null) {
            RulesetLoader.loadLines(rules, builder, "<rules> configuration");
        }
        return builder.build();
    }
}
