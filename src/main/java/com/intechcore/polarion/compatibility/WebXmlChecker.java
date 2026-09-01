package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Checks deployment descriptors and JSP pages.
 *
 * <p>The legacy namespace list is the one Polarion rejects,
 * {@code JakartaCompatibilityChecker.FORBIDDEN_WEBXML_MARKERS}. JSP pages are matched against
 * the forbidden packages over the whole file text, which is what Polarion does too.</p>
 *
 * <p>One deliberate difference: Polarion only looks at loose {@code web.xml} and {@code .jsp}
 * files under {@code <PolarionHome>/extensions/ * /eclipse/plugins}, never at the same files
 * packed inside a jar. A Polarion extension ships everything inside its jar, so Polarion in
 * practice never checks these. Tomcat still parses the descriptor, so this scanner looks
 * inside the jar as well.</p>
 */
public final class WebXmlChecker {

    private static final String JAKARTA_NAMESPACE = "https://jakarta.ee/xml/ns/jakartaee";

    private static final Map<String, String> LEGACY_NAMESPACES = Map.of(
            "http://xmlns.jcp.org/xml/ns/javaee", JAKARTA_NAMESPACE,
            "http://java.sun.com/xml/ns/javaee", JAKARTA_NAMESPACE,
            "http://java.sun.com/xml/ns/j2ee", JAKARTA_NAMESPACE);

    private WebXmlChecker() {
    }

    /**
     * Returns true when the entry is a deployment descriptor this check applies to.
     *
     * @param entryName the path of the entry inside the jar
     * @return whether the entry is a deployment descriptor
     */
    public static boolean isDescriptor(@NotNull String entryName) {
        String name = fileName(entryName);
        return "web.xml".equals(name) || "web-fragment.xml".equals(name);
    }

    /**
     * Returns true when the entry is a JSP page.
     *
     * @param entryName the path of the entry inside the jar
     * @return whether the entry is a JSP page
     */
    public static boolean isJsp(@NotNull String entryName) {
        return fileName(entryName).endsWith(".jsp");
    }

    /**
     * Returns a finding for every legacy namespace declared in the descriptor.
     *
     * @param content   the raw descriptor
     * @param container the jar holding the descriptor
     * @param entryName the path of the descriptor inside the jar
     * @return the findings, empty when the descriptor declares no legacy namespace
     */
    public static @NotNull List<Violation> check(byte[] content, @NotNull String container, @NotNull String entryName) {
        String text = new String(content, StandardCharsets.UTF_8);
        List<Violation> violations = new ArrayList<>();
        for (Map.Entry<String, String> legacy : LEGACY_NAMESPACES.entrySet()) {
            if (text.contains(legacy.getKey())) {
                violations.add(new Violation(Violation.Kind.WEB_XML_NAMESPACE, legacy.getKey(), legacy.getValue(),
                        container, entryName, "xmlns=\"" + legacy.getKey() + "\""));
            }
        }
        return violations;
    }

    /**
     * Returns a finding when the JSP page mentions a forbidden package.
     *
     * @param content   the raw page
     * @param rules     the packages the check rejects
     * @param container the jar holding the page
     * @param entryName the path of the page inside the jar
     * @return the finding, or an empty list when the page mentions none
     */
    public static @NotNull List<Violation> checkJsp(byte[] content, @NotNull PackageRules rules,
                                                    @NotNull String container, @NotNull String entryName) {
        String text = new String(content, StandardCharsets.UTF_8);
        PackageRules.Rule rule = rules.match(text);
        if (rule == null) {
            return List.of();
        }
        return List.of(new Violation(Violation.Kind.JSP_PAGE, rule.packageName(), rule.replacement(),
                container, entryName, rule.packageName()));
    }

    private static @NotNull String fileName(@NotNull String entryName) {
        return entryName.substring(entryName.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    }
}
