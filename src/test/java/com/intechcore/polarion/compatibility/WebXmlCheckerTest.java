package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebXmlCheckerTest {

    private PackageRules rules;

    @BeforeEach
    void setUp() throws IOException {
        PackageRules.Builder builder = PackageRules.builder();
        RulesetLoader.loadBuiltin("jakarta", builder);
        rules = builder.build();
    }

    @Test
    void isDescriptor_shouldAcceptWebXmlAndWebFragmentOnly() {
        assertThat(WebXmlChecker.isDescriptor("webapp/demo/WEB-INF/web.xml")).isTrue();
        assertThat(WebXmlChecker.isDescriptor("webapp/demo/WEB-INF/web-fragment.xml")).isTrue();
        assertThat(WebXmlChecker.isDescriptor("webapp/demo/WEB-INF/beans.xml")).isFalse();
    }

    @Test
    void isJsp_shouldAcceptOnlyJspPages() {
        assertThat(WebXmlChecker.isJsp("webapp/demo/index.jsp")).isTrue();
        assertThat(WebXmlChecker.isJsp("webapp/demo/index.html")).isFalse();
    }

    @Test
    void check_shouldReturnNothingForAJakartaDescriptor() {
        byte[] descriptor = ("<web-app xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"6.1\"/>")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(WebXmlChecker.check(descriptor, "bundle.jar", "WEB-INF/web.xml")).isEmpty();
    }

    @Test
    void checkJsp_shouldReturnNothingWhenThePageMentionsNoForbiddenPackage() {
        byte[] page = "<%@ page import=\"jakarta.servlet.http.HttpServletRequest\" %>"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(WebXmlChecker.checkJsp(page, rules, "bundle.jar", "index.jsp")).isEmpty();
    }

    @Test
    void checkJsp_shouldNameThePackageAsSubjectAndDetail() {
        byte[] page = "<%@ page import=\"javax.servlet.http.HttpServletRequest\" %>"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(WebXmlChecker.checkJsp(page, rules, "bundle.jar", "index.jsp"))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.kind()).isEqualTo(Violation.Kind.JSP_PAGE);
                    assertThat(violation.subject()).isEqualTo("javax.servlet");
                    assertThat(violation.detail()).isEqualTo("javax.servlet");
                    assertThat(violation.source()).isEqualTo("index.jsp");
                });
    }
}
