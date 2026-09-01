package com.intechcore.polarion.compatibility;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobMatcherTest {

    @Test
    void matches_shouldMatchFileNameWhenPatternHasNoSlash() {
        GlobMatcher matcher = new GlobMatcher(List.of("fop-core-*.jar"));
        assertThat(matcher.matches("bundle.jar!/webapp/x/WEB-INF/lib/fop-core-2.11.jar")).isTrue();
        assertThat(matcher.matches("bundle.jar!/webapp/x/WEB-INF/lib/fop-util-2.11.jar")).isFalse();
    }

    @Test
    void matches_shouldMatchFullPathWhenPatternHasSlash() {
        GlobMatcher matcher = new GlobMatcher(List.of("**/WEB-INF/lib/legacy-*.jar"));
        assertThat(matcher.matches("bundle.jar!/webapp/x/WEB-INF/lib/legacy-1.0.jar")).isTrue();
        assertThat(matcher.matches("bundle.jar!/other/legacy-1.0.jar")).isFalse();
    }

    @Test
    void matches_shouldTreatSingleStarAsSegmentLocal() {
        GlobMatcher matcher = new GlobMatcher(List.of("bundle.jar!/*.jar"));
        assertThat(matcher.matches("bundle.jar!/nested.jar")).isTrue();
        assertThat(matcher.matches("bundle.jar!/lib/nested.jar")).isFalse();
    }

    @Test
    void matches_shouldEscapeRegexCharacters() {
        GlobMatcher matcher = new GlobMatcher(List.of("a+b(1).jar"));
        assertThat(matcher.matches("a+b(1).jar")).isTrue();
        assertThat(matcher.matches("aab1.jar")).isFalse();
    }

    @Test
    void matches_shouldMatchNothingWhenEmpty() {
        GlobMatcher matcher = new GlobMatcher(List.of());
        assertThat(matcher.isEmpty()).isTrue();
        assertThat(matcher.matches("anything.jar")).isFalse();
    }
}
