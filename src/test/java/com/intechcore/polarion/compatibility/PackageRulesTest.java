package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PackageRulesTest {

    private static PackageRules jakarta() throws IOException {
        PackageRules.Builder builder = PackageRules.builder();
        RulesetLoader.loadBuiltin("jakarta", builder);
        return builder.build();
    }

    @Test
    void all_shouldHoldExactlyPolarionDefaultPackages() throws IOException {
        assertThat(jakarta().all().keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                "javax.activation", "javax.annotation", "javax.ejb", "javax.el", "javax.enterprise",
                "javax.faces", "javax.inject", "javax.jms", "javax.json", "javax.jws", "javax.mail",
                "javax.persistence", "javax.resource", "javax.security.auth.message",
                "javax.security.enterprise", "javax.servlet", "javax.transaction", "javax.validation",
                "javax.websocket", "javax.ws.rs", "javax.xml.bind", "javax.xml.soap"));
    }

    @Test
    void match_shouldAcceptInternalAndDottedForm() throws IOException {
        PackageRules rules = jakarta();
        assertThat(rules.match("javax/servlet/http/HttpServletRequest")).isNotNull();
        assertThat(rules.match("javax.servlet.http.HttpServletRequest")).isNotNull();
        assertThat(rules.match("(Ljavax/servlet/ServletRequest;)V")).isNotNull();
    }

    @Test
    void match_shouldReportTheReplacement() throws IOException {
        assertThat(jakarta().match("Ljavax/ws/rs/core/Response;"))
                .extracting(PackageRules.Rule::packageName, PackageRules.Rule::replacement)
                .containsExactly("javax.ws.rs", "jakarta.ws.rs");
    }

    @Test
    void match_shouldIgnorePackagesWhichStayedInTheJdk() throws IOException {
        PackageRules rules = jakarta();
        assertThat(rules.match("javax/xml/parsers/DocumentBuilderFactory")).isNull();
        assertThat(rules.match("javax/xml/transform/stream/StreamResult")).isNull();
        assertThat(rules.match("javax/xml/xpath/XPathFactory")).isNull();
        assertThat(rules.match("javax/xml/crypto/dsig/XMLSignature")).isNull();
        assertThat(rules.match("javax/xml/namespace/QName")).isNull();
        assertThat(rules.match("javax/security/auth/Subject")).isNull();
        assertThat(rules.match("javax/swing/text/html/HTML")).isNull();
        assertThat(rules.match("javax/crypto/Mac")).isNull();
        assertThat(rules.match("javax/imageio/ImageIO")).isNull();
        assertThat(rules.match("javax/net/ssl/SSLPeerUnverifiedException")).isNull();
        assertThat(rules.match("javax/media/jai/JAI")).isNull();
        assertThat(rules.match("javax/cache/Cache")).isNull();
    }

    @Test
    void match_shouldFollowPolarionAndFlagJdkSubpackagesOfAJakartaParent() throws IOException {
        PackageRules rules = jakarta();
        // Substring matching, no word boundary. Polarion rejects both, so the build must too.
        assertThat(rules.match("javax/transaction/xa/XAResource")).isNotNull();
        assertThat(rules.match("javax/annotation/processing/Processor")).isNotNull();
    }

    @Test
    void match_shouldFollowPolarionAndFlagAForbiddenPackageAnywhereInTheValue() throws IOException {
        PackageRules rules = jakarta();
        assertThat(rules.match("com/example/javax/servlet/Filter")).isNotNull();
        assertThat(rules.match("javax.servlet.context.tempdir")).isNotNull();
        assertThat(rules.match("webapp/x/WEB-INF/lib/javax.mail-1.6.2.jar")).isNotNull();
    }

    @Test
    void match_shouldNotFlagJakartaNames() throws IOException {
        PackageRules rules = jakarta();
        assertThat(rules.match("jakarta/servlet/http/HttpServletRequest")).isNull();
        assertThat(rules.match("jakarta.servlet-api")).isNull();
        assertThat(rules.match("jakarta.annotation;bundle-symbolic-name=\"jakarta.annotation-api\"")).isNull();
    }

    @Test
    void match_shouldReturnNullForNullAndEmpty() throws IOException {
        PackageRules rules = jakarta();
        assertThat(rules.match(null)).isNull();
        assertThat(rules.match("")).isNull();
    }

    @Test
    void match_shouldSuppressWhenAnAllowEntryCoversTheForbiddenPackage() throws IOException {
        PackageRules.Builder builder = PackageRules.builder();
        RulesetLoader.loadBuiltin("jakarta", builder);
        RulesetLoader.loadLines(List.of("!javax.transaction.xa"), builder, "test");
        PackageRules rules = builder.build();

        assertThat(rules.match("javax/transaction/xa/XAResource")).isNull();
        assertThat(rules.match("javax/transaction/UserTransaction")).isNotNull();
    }

    @Test
    void extendedRuleset_shouldAddThePackagesPolarionMisses() throws IOException {
        PackageRules.Builder builder = PackageRules.builder();
        RulesetLoader.loadBuiltin("jakarta", builder);
        RulesetLoader.loadBuiltin("jakarta-extended", builder);
        PackageRules rules = builder.build();

        assertThat(jakarta().match("javax/xml/ws/Service")).isNull();
        assertThat(rules.match("javax/xml/ws/Service")).isNotNull();
        assertThat(rules.match("javax/interceptor/AroundInvoke")).isNotNull();
    }

    @Test
    void loadLines_shouldOverrideBundledRules() throws IOException {
        PackageRules.Builder builder = PackageRules.builder();
        RulesetLoader.loadBuiltin("jakarta", builder);
        RulesetLoader.loadLines(List.of("!javax.servlet", "org.legacy -> org.modern"), builder, "test");
        PackageRules rules = builder.build();

        assertThat(rules.match("javax/servlet/Filter")).isNull();
        assertThat(rules.match("org/legacy/Thing")).isNotNull();
    }

    @Test
    void loadBuiltin_shouldRejectUnknownRuleset() {
        assertThat(catchThrowable(() -> RulesetLoader.loadBuiltin("nope", PackageRules.builder())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unknown ruleset");
    }
}
