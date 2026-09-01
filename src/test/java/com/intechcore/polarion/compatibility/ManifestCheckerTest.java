package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestCheckerTest {

    private PackageRules rules;

    @BeforeEach
    void setUp() throws IOException {
        PackageRules.Builder builder = PackageRules.builder();
        RulesetLoader.loadBuiltin("jakarta", builder);
        rules = builder.build();
    }

    @Test
    void check_shouldReportEveryMainAttributeNamingAForbiddenPackage() throws IOException {
        byte[] manifest = TestArchives.manifest(List.of(
                "Import-Package: javax.el,org.osgi.framework",
                "DynamicImport-Package: javax.websocket",
                "Export-Package: com.example.api"));

        assertThat(ManifestChecker.check(manifest, rules, "bundle.jar"))
                .extracting(Violation::subject)
                .containsExactlyInAnyOrder("javax.el", "javax.websocket");
    }

    @Test
    void check_shouldReportTheHeaderName() throws IOException {
        byte[] manifest = TestArchives.manifest(List.of("Require-Bundle: javax.servlet-api,com.polarion.alm.ui"));

        assertThat(ManifestChecker.check(manifest, rules, "bundle.jar")).singleElement()
                .extracting(Violation::detail)
                .isEqualTo("Require-Bundle: javax.servlet-api,com.polarion.alm.ui");
    }

    @Test
    void check_shouldLookBeyondOsgiHeaders() throws IOException {
        byte[] manifest = TestArchives.manifest(List.of("Bundle-SymbolicName: javax.mail"));

        assertThat(ManifestChecker.check(manifest, rules, "bundle.jar")).singleElement()
                .extracting(Violation::subject).isEqualTo("javax.mail");
    }

    @Test
    void check_shouldAcceptJakartaHeaders() throws IOException {
        byte[] manifest = TestArchives.manifest(List.of(
                "Import-Package: org.osgi.framework,jakarta.annotation;bundle-symbolic-name=\"jakarta.annotation-api\"",
                "Require-Bundle: jakarta.servlet-api,jakarta.ws.rs-api,com.polarion.alm.ui"));

        assertThat(ManifestChecker.check(manifest, rules, "bundle.jar")).isEmpty();
    }

    @Test
    void check_shouldAcceptManifestWithoutOsgiHeaders() throws IOException {
        assertThat(ManifestChecker.check(TestArchives.manifest(List.of("Created-By: test")), rules, "bundle.jar")).isEmpty();
    }

    @Test
    void abbreviate_shouldShortenLongHeaderValues() {
        assertThat(ManifestChecker.abbreviate("short")).isEqualTo("short");
        assertThat(ManifestChecker.abbreviate("x".repeat(200))).hasSize(120).endsWith("...");
    }


    @Test
    void check_shouldIgnoreABlankHeaderValue() throws IOException {
        byte[] manifest = TestArchives.manifest(List.of("X-Blank: ", "Import-Package: javax.el"));

        assertThat(ManifestChecker.check(manifest, rules, "bundle.jar"))
                .extracting(Violation::subject)
                .containsExactly("javax.el");
    }
}
