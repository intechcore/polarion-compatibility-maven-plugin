package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Decides whether a value refers to a forbidden package.
 *
 * <p>The matching mirrors Polarion's runtime gate
 * ({@code com.polarion.alm.install.extensions.validator.helper.JakartaCompatibilityChecker}):
 * slashes are normalized to dots, then the value is tested with a plain substring search
 * against each forbidden package. There is no word boundary, so {@code javax.transaction}
 * also matches {@code javax.transaction.xa.XAResource}. That is what Polarion does, and a
 * build check which is more lenient than the gate is worthless.</p>
 *
 * <p>An allow entry suppresses a match when the allow string both contains the forbidden
 * package and appears in the value. It exists as an escape hatch. Using it makes the build
 * more lenient than the runtime gate, so a bundle can pass here and still be rejected by
 * Polarion.</p>
 */
public final class PackageRules {

    private final Map<String, Rule> forbidden;
    private final TreeSet<String> allowed;

    private PackageRules(@NotNull Map<String, Rule> forbidden, @NotNull TreeSet<String> allowed) {
        this.forbidden = Collections.unmodifiableMap(new TreeMap<>(forbidden));
        this.allowed = allowed;
    }

    /**
     * Creates rules from a builder.
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Returns true when no package is forbidden.
     */
    public boolean isEmpty() {
        return forbidden.isEmpty();
    }

    /**
     * Returns the first forbidden package the value refers to, or null when it refers to none.
     *
     * @param value a class name, descriptor, signature, string constant or manifest header value,
     *              in either internal or dotted form
     */
    public @Nullable Rule match(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String normalized = value.replace('/', '.');
        for (Map.Entry<String, Rule> entry : forbidden.entrySet()) {
            if (normalized.contains(entry.getKey()) && !isSuppressed(normalized, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isSuppressed(@NotNull String normalized, @NotNull String packageName) {
        for (String allow : allowed) {
            if (allow.contains(packageName) && normalized.contains(allow)) {
                return true;
            }
        }
        return false;
    }

    /**
     * All forbidden packages, keyed by package name, sorted for stable reporting.
     */
    public @NotNull Map<String, Rule> all() {
        return new TreeMap<>(forbidden);
    }

    /**
     * A forbidden package.
     *
     * @param packageName the package which must not be referenced
     * @param replacement the suggested replacement package, or null when none is known
     */
    public record Rule(@NotNull String packageName, @Nullable String replacement) {
    }

    /**
     * Collects rules from rulesets and from plugin configuration.
     */
    public static final class Builder {

        private final Map<String, Rule> forbidden = new LinkedHashMap<>();
        private final TreeSet<String> allowed = new TreeSet<>();

        private Builder() {
        }

        /**
         * Adds a forbidden package with an optional replacement suggestion.
         */
        public void forbid(@NotNull String packageName, @Nullable String replacement) {
            allowed.remove(packageName);
            forbidden.put(packageName, new Rule(packageName, replacement));
        }

        /**
         * Adds an allow entry which suppresses a forbidden match.
         */
        public void allow(@NotNull String packageName) {
            forbidden.remove(packageName);
            allowed.add(packageName);
        }

        /**
         * Builds the immutable rule set.
         */
        public @NotNull PackageRules build() {
            return new PackageRules(forbidden, new TreeSet<>(allowed));
        }
    }
}
