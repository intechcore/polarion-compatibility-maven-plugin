package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViolationReportTest {

    private static Violation classReference(String subject, String container, String source, String detail) {
        return new Violation(Violation.Kind.CLASS_REFERENCE, subject, "jakarta.servlet", container, source, detail);
    }

    @Test
    void lines_shouldGroupByKindThenPackageThenJar() {
        List<Violation> violations = List.of(
                classReference("javax.servlet", "bundle.jar!/webapp/x/WEB-INF/lib/dep-1.0.jar", "org.dep.A", "javax.servlet.Filter"),
                classReference("javax.servlet", "bundle.jar!/webapp/x/WEB-INF/lib/dep-1.0.jar", "org.dep.B", "javax.servlet.Filter"),
                new Violation(Violation.Kind.MANIFEST_HEADER, "javax.servlet", "jakarta.servlet",
                        "bundle.jar", "META-INF/MANIFEST.MF", "Require-Bundle: javax.servlet-api"));

        assertThat(new ViolationReport(violations, 5).lines()).containsExactly(
                "Class references:",
                "  javax.servlet  ->  jakarta.servlet",
                "    in dep-1.0.jar",
                "      org.dep.A  (javax.servlet.Filter)",
                "      org.dep.B  (javax.servlet.Filter)",
                "OSGi manifest headers:",
                "  javax.servlet  ->  jakarta.servlet",
                "    in bundle.jar",
                "      META-INF/MANIFEST.MF  (Require-Bundle: javax.servlet-api)");
    }

    @Test
    void lines_shouldTruncateSourceListAndSayHowManyRemain() {
        List<Violation> violations = List.of(
                classReference("javax.servlet", "bundle.jar", "org.dep.A", "javax.servlet.Filter"),
                classReference("javax.servlet", "bundle.jar", "org.dep.B", "javax.servlet.Filter"),
                classReference("javax.servlet", "bundle.jar", "org.dep.C", "javax.servlet.Filter"));

        assertThat(new ViolationReport(violations, 1).lines())
                .contains("      org.dep.A  (javax.servlet.Filter)", "      ... and 2 more")
                .doesNotContain("      org.dep.B  (javax.servlet.Filter)");
    }

    @Test
    void lines_shouldOmitArrowWhenNoReplacementIsKnown() {
        List<Violation> violations = List.of(
                new Violation(Violation.Kind.CLASS_REFERENCE, "com.legacy", null, "bundle.jar", "org.dep.A", "com.legacy.Thing"));

        assertThat(new ViolationReport(violations, 5).lines()).contains("  com.legacy");
    }

    @Test
    void summary_shouldCountReferencesAndDistinctSubjects() {
        List<Violation> violations = List.of(
                classReference("javax.servlet", "bundle.jar", "org.dep.A", "javax.servlet.Filter"),
                classReference("javax.ws.rs", "bundle.jar", "org.dep.A", "javax.ws.rs.GET"));

        assertThat(new ViolationReport(violations, 5).summary())
                .isEqualTo("2 reference(s) to 2 forbidden package(s) or namespace(s)");
    }

    @Test
    void shortContainer_shouldKeepOnlyTheInnermostJarName() {
        assertThat(ViolationReport.shortContainer("bundle.jar")).isEqualTo("bundle.jar");
        assertThat(ViolationReport.shortContainer("bundle.jar!/webapp/x/WEB-INF/lib/dep-1.0.jar")).isEqualTo("dep-1.0.jar");
        assertThat(ViolationReport.shortContainer("a.jar!/b.jar!/c.jar")).isEqualTo("c.jar");
    }
}
