package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;

/**
 * Walks a packaged jar and everything nested inside it, looking for forbidden packages.
 *
 * <p>A Polarion extension ships as a single jar which carries its runtime dependencies under
 * {@code webapp/&lt;context&gt;/WEB-INF/lib}. Those nested jars are where a forbidden package
 * realistically appears, since the extension sources are compiled against the current API.
 * The scan therefore descends into every nested jar rather than stopping at the outer one.</p>
 *
 * <p>This mirrors Polarion's own gate,
 * {@code com.polarion.alm.install.extensions.validator.ExtensionsScanner}, which walks
 * {@code <PolarionHome>/extensions/ * /eclipse/plugins} and checks every jar, its manifest and
 * every nested jar the same way. Polarion extracts each nested jar to a temporary file; this
 * scanner streams it, which is faster and otherwise equivalent.</p>
 */
public final class BundleScanner {

    private final PackageRules rules;
    private final GlobMatcher excludedJars;
    private final int maxDepth;
    private final boolean checkClasses;
    private final boolean checkManifest;
    private final boolean checkDescriptors;
    private final Consumer<String> debug;

    private BundleScanner(@NotNull Builder builder) {
        this.rules = builder.rules;
        this.excludedJars = builder.excludedJars;
        this.maxDepth = builder.maxDepth;
        this.checkClasses = builder.checkClasses;
        this.checkManifest = builder.checkManifest;
        this.checkDescriptors = builder.checkDescriptors;
        this.debug = builder.debug;
    }

    /**
     * Creates a scanner builder for the given rules.
     *
     * @param rules the packages the scan rejects
     * @return a builder which produces a scanner over those rules
     */
    public static @NotNull Builder builder(@NotNull PackageRules rules) {
        return new Builder(rules);
    }

    /**
     * Scans a jar file and returns everything found.
     *
     * @param jarFile the jar to scan
     * @return the findings, together with the counters the scan produced
     * @throws IOException when the jar cannot be read
     */
    public @NotNull ScanResult scan(@NotNull Path jarFile) throws IOException {
        ScanResult result = new ScanResult();
        try (InputStream in = Files.newInputStream(jarFile)) {
            scanArchive(in, jarFile.getFileName().toString(), 0, result);
        }
        return result;
    }

    private void scanArchive(@NotNull InputStream in, @NotNull String container, int depth, @NotNull ScanResult result) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                // Polarion lowercases the entry name before testing the suffix
                // (JakartaCompatibilityChecker.isJarEntryCompatible), so it scans an entry named
                // Foo.JAR. Matching case sensitively here would skip it and pass a bundle the
                // gate rejects. The original name is kept for the report.
                String suffixName = name.toLowerCase(Locale.ROOT);
                if (suffixName.endsWith(".jar")) {
                    scanNestedJar(zip, container, name, depth, result);
                } else if (checkClasses && suffixName.endsWith(".class")) {
                    result.classesScanned++;
                    scanClass(readAll(zip), container, name, result);
                } else if (checkManifest && "META-INF/MANIFEST.MF".equalsIgnoreCase(name)) {
                    result.add(ManifestChecker.check(readAll(zip), rules, container));
                } else if (checkDescriptors && WebXmlChecker.isDescriptor(name)) {
                    result.add(WebXmlChecker.check(readAll(zip), container, name));
                } else if (checkDescriptors && WebXmlChecker.isJsp(name)) {
                    result.add(WebXmlChecker.checkJsp(readAll(zip), rules, container, name));
                }
            }
        }
    }

    private void scanNestedJar(@NotNull ZipInputStream zip, @NotNull String container, @NotNull String name,
                               int depth, @NotNull ScanResult result) throws IOException {
        String nested = container + "!/" + name;
        if (excludedJars.matches(nested)) {
            result.excludedJars.add(nested);
            return;
        }
        if (depth >= maxDepth) {
            debug.accept("Nesting limit " + maxDepth + " reached, not scanning " + nested);
            result.skippedJars.add(nested);
            return;
        }
        result.jarsScanned++;
        scanArchive(new ByteArrayInputStream(readAll(zip)), nested, depth + 1, result);
    }

    private void scanClass(byte[] bytes, @NotNull String container, @NotNull String entryName, @NotNull ScanResult result) {
        ForbiddenPackageVisitor visitor = new ForbiddenPackageVisitor(rules);
        try {
            new ClassReader(bytes).accept(visitor, ForbiddenPackageVisitor.PARSING_OPTIONS);
        } catch (RuntimeException e) {
            debug.accept("Cannot read " + container + "!/" + entryName + ": " + e);
            result.unreadableClasses.add(container + "!/" + entryName);
            return;
        }
        // A detection key is the packageName of a rule this scanner matched, so all() holds it.
        // ClassReader.accept always calls visit first, so a class which parsed has a name.
        // className() is declared nullable all the same, so the entry path stands in for it. That
        // keeps the report honest without a null branch no test can reach.
        String source = Objects.requireNonNullElse(visitor.className(), entryName);
        for (Map.Entry<String, String> detection : visitor.detections().entrySet()) {
            PackageRules.Rule rule = rules.all().get(detection.getKey());
            result.add(new Violation(Violation.Kind.CLASS_REFERENCE, detection.getKey(),
                    rule.replacement(), container, source, detection.getValue()));
        }
    }

    private static byte[] readAll(@NotNull InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Everything one scan produced.
     */
    public static final class ScanResult {

        private final Map<String, Violation> violations = new LinkedHashMap<>();
        private final List<String> excludedJars = new ArrayList<>();
        private final List<String> skippedJars = new ArrayList<>();
        private final List<String> unreadableClasses = new ArrayList<>();
        private int classesScanned;
        private int jarsScanned;

        /**
         * Creates an empty result. Only the scan fills it in.
         */
        private ScanResult() {
        }

        private void add(@NotNull Violation violation) {
            violations.putIfAbsent(violation.dedupKey(), violation);
        }

        private void add(@NotNull List<Violation> found) {
            found.forEach(this::add);
        }

        /**
         * The findings, one per forbidden package per source, in discovery order.
         *
         * @return an immutable copy of the findings
         */
        public @NotNull List<Violation> violations() {
            return List.copyOf(violations.values());
        }

        /**
         * Nested jars skipped because of an exclusion glob.
         *
         * @return the paths of those jars
         */
        public @NotNull List<String> excludedJars() {
            return List.copyOf(excludedJars);
        }

        /**
         * Nested jars skipped because the nesting limit was reached.
         *
         * @return the paths of those jars
         */
        public @NotNull List<String> skippedJars() {
            return List.copyOf(skippedJars);
        }

        /**
         * Class entries which could not be parsed.
         *
         * @return the names of those entries
         */
        public @NotNull List<String> unreadableClasses() {
            return List.copyOf(unreadableClasses);
        }

        /**
         * Number of class entries read.
         *
         * @return the count
         */
        public int classesScanned() {
            return classesScanned;
        }

        /**
         * Number of nested jars opened.
         *
         * @return the count
         */
        public int jarsScanned() {
            return jarsScanned;
        }
    }

    /**
     * Configures a scanner.
     */
    public static final class Builder {

        private final PackageRules rules;
        private GlobMatcher excludedJars = new GlobMatcher(List.of());
        private int maxDepth = 5;
        private boolean checkClasses = true;
        private boolean checkManifest = true;
        private boolean checkDescriptors = true;
        private Consumer<String> debug = message -> {
        };

        private Builder(@NotNull PackageRules rules) {
            this.rules = rules;
        }

        /**
         * Sets the globs of nested jars to skip.
         *
         * @param matcher the compiled globs, or null to skip nothing
         * @return this builder
         */
        public @NotNull Builder excludedJars(@Nullable GlobMatcher matcher) {
            this.excludedJars = matcher == null ? new GlobMatcher(List.of()) : matcher;
            return this;
        }

        /**
         * Sets how deep nested jars are followed.
         *
         * @param depth the nesting limit
         * @return this builder
         */
        public @NotNull Builder maxDepth(int depth) {
            this.maxDepth = depth;
            return this;
        }

        /**
         * Enables or disables the class reference check.
         *
         * @param enabled whether the check runs
         * @return this builder
         */
        public @NotNull Builder checkClasses(boolean enabled) {
            this.checkClasses = enabled;
            return this;
        }

        /**
         * Enables or disables the OSGi manifest check.
         *
         * @param enabled whether the check runs
         * @return this builder
         */
        public @NotNull Builder checkManifest(boolean enabled) {
            this.checkManifest = enabled;
            return this;
        }

        /**
         * Enables or disables the deployment descriptor check.
         *
         * @param enabled whether the check runs
         * @return this builder
         */
        public @NotNull Builder checkDescriptors(boolean enabled) {
            this.checkDescriptors = enabled;
            return this;
        }

        /**
         * Sets the sink for diagnostic messages.
         *
         * @param sink receives one message per skipped or unreadable entry
         * @return this builder
         */
        public @NotNull Builder debug(@NotNull Consumer<String> sink) {
            this.debug = sink;
            return this;
        }

        /**
         * Builds the scanner.
         *
         * @return a scanner configured by this builder
         */
        public @NotNull BundleScanner build() {
            return new BundleScanner(this);
        }
    }
}
