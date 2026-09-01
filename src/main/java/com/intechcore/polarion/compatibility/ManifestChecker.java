package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Checks a jar manifest for forbidden packages.
 *
 * <p>Every main attribute value is checked, not only the OSGi headers, because that is what
 * Polarion does ({@code JakartaCompatibilityChecker.isManifestCompatible} iterates
 * {@code getMainAttributes().values()}). The matching is the same substring search, so a value
 * fails as soon as it contains a forbidden package anywhere.</p>
 *
 * <p>That catches more than imports. {@code Bundle-ClassPath} lists the nested jars by path, so
 * bundling a dependency whose file name starts with a forbidden package, such as
 * {@code javax.mail-1.6.2.jar}, fails the manifest check even when no class refers to the
 * package. Polarion rejects such a bundle, so this scanner does too.</p>
 */
public final class ManifestChecker {

    private ManifestChecker() {
    }

    /**
     * Returns a finding for every main attribute naming a forbidden package.
     *
     * @param content   the raw manifest
     * @param rules     the packages the check rejects
     * @param container the jar holding the manifest
     * @return the findings, empty when no attribute names a forbidden package
     * @throws IOException when the manifest cannot be parsed
     */
    public static @NotNull List<Violation> check(byte[] content, @NotNull PackageRules rules,
                                                 @NotNull String container) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(content));
        Attributes attributes = manifest.getMainAttributes();
        List<Violation> violations = new ArrayList<>();
        for (Map.Entry<Object, Object> attribute : attributes.entrySet()) {
            // Manifest parses every main attribute into a String, so the value is never null.
            String value = attribute.getValue().toString();
            if (value.isBlank()) {
                continue;
            }
            PackageRules.Rule rule = rules.match(value);
            if (rule != null) {
                violations.add(new Violation(Violation.Kind.MANIFEST_HEADER, rule.packageName(), rule.replacement(),
                        container, "META-INF/MANIFEST.MF", attribute.getKey() + ": " + abbreviate(value)));
            }
        }
        return violations;
    }

    /**
     * Shortens a long header value so the report stays readable. Bundle-ClassPath in particular
     * can run to several thousand characters.
     */
    static @NotNull String abbreviate(@NotNull String value) {
        return value.length() <= 120 ? value : value.substring(0, 117) + "...";
    }
}
