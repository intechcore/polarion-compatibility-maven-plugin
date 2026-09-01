package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders findings as the lines the build log prints.
 */
public final class ViolationReport {

    private final List<Violation> violations;
    private final int maxSourcesPerSubject;

    /**
     * Creates a report over the given findings.
     *
     * @param violations           the findings to render
     * @param maxSourcesPerSubject how many offending classes to list per forbidden package
     */
    public ViolationReport(@NotNull List<Violation> violations, int maxSourcesPerSubject) {
        this.violations = violations;
        this.maxSourcesPerSubject = maxSourcesPerSubject;
    }

    /**
     * Returns the report as a list of log lines.
     *
     * @return the lines, in print order
     */
    public @NotNull List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Violation.Kind, List<Violation>> byKind : groupByKind().entrySet()) {
            lines.add(byKind.getKey().title() + ":");
            appendKind(lines, byKind.getValue());
        }
        return lines;
    }

    private void appendKind(@NotNull List<String> lines, @NotNull List<Violation> forKind) {
        for (Map.Entry<String, List<Violation>> bySubject : groupBySubject(forKind).entrySet()) {
            Violation first = bySubject.getValue().getFirst();
            String replacement = first.replacement() == null ? "" : "  ->  " + first.replacement();
            lines.add("  " + bySubject.getKey() + replacement);
            appendSubject(lines, bySubject.getValue());
        }
    }

    private void appendSubject(@NotNull List<String> lines, @NotNull List<Violation> forSubject) {
        for (Map.Entry<String, List<Violation>> byContainer : groupByContainer(forSubject).entrySet()) {
            lines.add("    in " + byContainer.getKey());
            List<Violation> sources = byContainer.getValue();
            int shown = Math.min(sources.size(), maxSourcesPerSubject);
            for (int i = 0; i < shown; i++) {
                Violation violation = sources.get(i);
                lines.add("      " + violation.source() + "  (" + violation.detail() + ")");
            }
            if (sources.size() > shown) {
                lines.add("      ... and " + (sources.size() - shown) + " more");
            }
        }
    }

    /**
     * One line summary of the findings.
     *
     * @return the summary line
     */
    public @NotNull String summary() {
        return violations.size() + " reference(s) to " + subjects().size() + " forbidden package(s) or namespace(s)";
    }

    /**
     * The distinct forbidden packages and namespaces found, sorted.
     *
     * <p>The build failure message names them, because a CI log often shows only that line.</p>
     *
     * @return the subjects, sorted
     */
    public @NotNull List<String> subjects() {
        return violations.stream().map(Violation::subject).distinct().sorted().toList();
    }

    private @NotNull Map<Violation.Kind, List<Violation>> groupByKind() {
        Map<Violation.Kind, List<Violation>> grouped = new LinkedHashMap<>();
        for (Violation.Kind kind : Violation.Kind.values()) {
            List<Violation> forKind = violations.stream().filter(v -> v.kind() == kind).toList();
            if (!forKind.isEmpty()) {
                grouped.put(kind, forKind);
            }
        }
        return grouped;
    }

    private static @NotNull Map<String, List<Violation>> groupBySubject(@NotNull List<Violation> input) {
        Map<String, List<Violation>> grouped = new TreeMap<>();
        for (Violation violation : input) {
            grouped.computeIfAbsent(violation.subject(), key -> new ArrayList<>()).add(violation);
        }
        return grouped;
    }

    private static @NotNull Map<String, List<Violation>> groupByContainer(@NotNull List<Violation> input) {
        Map<String, List<Violation>> grouped = new TreeMap<>();
        for (Violation violation : input) {
            grouped.computeIfAbsent(shortContainer(violation.container()), key -> new ArrayList<>()).add(violation);
        }
        return grouped;
    }

    /**
     * Shortens a nested container path to the jar name, which is what identifies the dependency.
     */
    static @NotNull String shortContainer(@NotNull String container) {
        int lastJar = container.lastIndexOf("!/");
        if (lastJar < 0) {
            return container;
        }
        String tail = container.substring(lastJar + 2);
        return tail.substring(tail.lastIndexOf('/') + 1);
    }
}
