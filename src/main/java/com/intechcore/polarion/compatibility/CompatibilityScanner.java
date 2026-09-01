package com.intechcore.polarion.compatibility;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds references to legacy {@code javax} packages in compiled classes.
 *
 * <p>Polarion 2606 runs on Jakarta EE 11. Its own compatibility checker refuses to load an
 * extension whose bundled classes still reference a {@code javax} package that moved to
 * {@code jakarta}. This scanner reports the same references at build time.</p>
 */
public class CompatibilityScanner {

    private static final String CLASS_SUFFIX = ".class";

    private final List<String> forbiddenPackages;
    private final List<String> allowedPackages;

    /**
     * Creates a scanner.
     *
     * @param forbiddenPackages package prefixes that must not be referenced, in dotted form
     * @param allowedPackages   package prefixes that stay allowed inside a forbidden prefix
     */
    public CompatibilityScanner(List<String> forbiddenPackages, List<String> allowedPackages) {
        this.forbiddenPackages = List.copyOf(forbiddenPackages);
        this.allowedPackages = List.copyOf(allowedPackages);
    }

    /**
     * Scans every class file below a directory.
     *
     * @param classesDirectory the root directory; a missing directory yields no findings
     * @return the forbidden references, in scan order
     * @throws IOException when the directory cannot be read
     */
    public List<ForbiddenReference> scanDirectory(Path classesDirectory) throws IOException {
        if (!Files.isDirectory(classesDirectory)) {
            return List.of();
        }
        List<ForbiddenReference> found = new ArrayList<>();
        String source = classesDirectory.getFileName().toString();
        try (Stream<Path> files = Files.walk(classesDirectory)) {
            files.filter(path -> path.toString().endsWith(CLASS_SUFFIX))
                    .forEach(path -> found.addAll(scanClassFile(path, classesDirectory, source)));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return found;
    }

    /**
     * Scans every class file inside a jar.
     *
     * @param jar the jar file
     * @return the forbidden references, in entry order
     * @throws IOException when the jar cannot be read
     */
    public List<ForbiddenReference> scanJar(Path jar) throws IOException {
        List<ForbiddenReference> found = new ArrayList<>();
        String source = jar.getFileName().toString();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(CLASS_SUFFIX)) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    found.addAll(inspect(source, toClassName(entry.getName()), input));
                }
            }
        }
        return found;
    }

    private List<ForbiddenReference> scanClassFile(Path file, Path root, String source) {
        try (InputStream input = Files.newInputStream(file)) {
            return inspect(source, toClassName(root.relativize(file).toString()), input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<ForbiddenReference> inspect(String source, String className, InputStream input) throws IOException {
        Set<String> references = new LinkedHashSet<>();
        for (String constant : ClassFileReader.readUtf8Constants(input)) {
            collectReferences(constant, references);
        }
        return references.stream()
                .map(reference -> new ForbiddenReference(source, className, reference))
                .toList();
    }

    private void collectReferences(String constant, Set<String> references) {
        for (String forbidden : this.forbiddenPackages) {
            addReference(constant, forbidden.replace('.', '/') + '/', references);
            addReference(constant, forbidden + '.', references);
        }
    }

    private void addReference(String constant, String needle, Set<String> references) {
        int index = constant.indexOf(needle);
        if (index < 0) {
            return;
        }
        String reference = extractReference(constant, index);
        if (!isAllowed(reference)) {
            references.add(reference);
        }
    }

    private static String extractReference(String constant, int start) {
        int end = start;
        while (end < constant.length() && isReferenceChar(constant.charAt(end))) {
            end++;
        }
        return constant.substring(start, end).replace('/', '.');
    }

    private static boolean isReferenceChar(char character) {
        return Character.isLetterOrDigit(character) || character == '.' || character == '/' || character == '_' || character == '$';
    }

    private boolean isAllowed(String reference) {
        return this.allowedPackages.stream().anyMatch(allowed -> reference.equals(allowed) || reference.startsWith(allowed + '.'));
    }

    private static String toClassName(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.substring(0, normalized.length() - CLASS_SUFFIX.length()).replace('/', '.');
    }
}
