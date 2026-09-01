package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One finding.
 *
 * @param kind        what kind of check produced the finding
 * @param subject     the forbidden package or legacy namespace which was found
 * @param replacement the suggested replacement, or null when none is known
 * @param container   the jar holding the offending entry, as a path such as
 *                    {@code bundle.jar!/webapp/x/WEB-INF/lib/dep.jar}
 * @param source      the class or file which holds the reference
 * @param detail      the reference as it appears in the bundle
 */
public record Violation(@NotNull Kind kind,
                        @NotNull String subject,
                        @Nullable String replacement,
                        @NotNull String container,
                        @NotNull String source,
                        @NotNull String detail) {

    /**
     * Key used to collapse repeated findings of the same package in the same source.
     *
     * <p>The detail is part of the key so that a manifest naming the same package in two
     * headers is reported twice, once per header. A class reports a package once, because
     * the scanner already collapses the references it holds.</p>
     *
     * @return the key
     */
    public @NotNull String dedupKey() {
        return kind + "|" + subject + "|" + container + "|" + source + "|" + detail;
    }

    /**
     * The check which produced a finding.
     */
    public enum Kind {

        /**
         * A compiled class refers to a forbidden package.
         */
        CLASS_REFERENCE("Class references"),

        /**
         * An OSGi manifest header names a forbidden package or bundle.
         */
        MANIFEST_HEADER("OSGi manifest headers"),

        /**
         * A deployment descriptor uses a legacy Java EE schema.
         */
        WEB_XML_NAMESPACE("Deployment descriptors"),

        /**
         * A JSP page mentions a forbidden package.
         */
        JSP_PAGE("JSP pages");

        private final String title;

        Kind(@NotNull String title) {
            this.title = title;
        }

        /**
         * Heading used in the report.
         *
         * @return the heading
         */
        public @NotNull String title() {
            return title;
        }
    }
}
