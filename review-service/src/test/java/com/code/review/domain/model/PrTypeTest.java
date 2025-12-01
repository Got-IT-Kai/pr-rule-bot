package com.code.review.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrTypeTest {

    @Test
    void shouldDetectConventionalCommitFormat() {
        assertEquals(PrType.FEATURE, PrType.detect("feat: add new feature"));
        assertEquals(PrType.FEATURE, PrType.detect("feature: implement user login"));
        assertEquals(PrType.FEATURE, PrType.detect("feat(auth): add OAuth support"));

        assertEquals(PrType.BUGFIX, PrType.detect("fix: resolve null pointer exception"));
        assertEquals(PrType.BUGFIX, PrType.detect("bugfix: handle edge case"));
        assertEquals(PrType.BUGFIX, PrType.detect("fix(api): correct response format"));

        assertEquals(PrType.REFACTOR, PrType.detect("refactor: improve code structure"));
        assertEquals(PrType.REFACTOR, PrType.detect("refactor(service): extract common logic"));

        assertEquals(PrType.DOCS, PrType.detect("docs: update README"));
        assertEquals(PrType.DOCS, PrType.detect("doc: add API documentation"));

        assertEquals(PrType.TEST, PrType.detect("test: add unit tests"));
        assertEquals(PrType.TEST, PrType.detect("test(auth): add integration tests"));

        assertEquals(PrType.CHORE, PrType.detect("chore: update dependencies"));
        assertEquals(PrType.CHORE, PrType.detect("chore(deps): bump version"));

        assertEquals(PrType.PERFORMANCE, PrType.detect("perf: optimize database queries"));
        assertEquals(PrType.PERFORMANCE, PrType.detect("perf(api): reduce response time"));

        assertEquals(PrType.SECURITY, PrType.detect("security: patch SQL injection vulnerability"));
        assertEquals(PrType.SECURITY, PrType.detect("security(api): update authentication"));
    }

    @Test
    void shouldDetectKeywordsInNaturalLanguage() {
        assertEquals(PrType.BUGFIX, PrType.detect("Fix critical bug in payment"));
        assertEquals(PrType.BUGFIX, PrType.detect("Bug fix for authentication"));

        assertEquals(PrType.REFACTOR, PrType.detect("Refactor authentication module"));

        assertEquals(PrType.TEST, PrType.detect("Add test coverage for service"));

        assertEquals(PrType.PERFORMANCE, PrType.detect("Optimize rendering performance"));

        assertEquals(PrType.SECURITY, PrType.detect("Security vulnerability patch"));
    }

    @Test
    void shouldPrioritizeConventionalCommitOverKeywords() {
        assertEquals(PrType.BUGFIX, PrType.detect("fix: add security feature"));
        assertEquals(PrType.FEATURE, PrType.detect("feat: fix bug in old code"));
    }

    @Test
    void shouldReturnUnknownForNullOrEmpty() {
        assertEquals(PrType.UNKNOWN, PrType.detect(null));
        assertEquals(PrType.UNKNOWN, PrType.detect(""));
        assertEquals(PrType.UNKNOWN, PrType.detect("   "));
    }

    @Test
    void shouldDefaultToFeatureForGenericTitles() {
        assertEquals(PrType.FEATURE, PrType.detect("Update user interface"));
        assertEquals(PrType.FEATURE, PrType.detect("Implement new dashboard"));
        assertEquals(PrType.FEATURE, PrType.detect("Random PR title"));
    }

    @Test
    void shouldProvideFocusForSpecificTypes() {
        assertNotNull(PrType.BUGFIX.focus());
        assertNotNull(PrType.REFACTOR.focus());
        assertNotNull(PrType.PERFORMANCE.focus());
        assertNotNull(PrType.SECURITY.focus());
        assertNotNull(PrType.TEST.focus());
        assertNotNull(PrType.DOCS.focus());

        assertNull(PrType.FEATURE.focus());
        assertNull(PrType.CHORE.focus());
        assertNull(PrType.UNKNOWN.focus());
    }

    @Test
    void shouldHaveMeaningfulFocusContent() {
        assertTrue(PrType.BUGFIX.focus().toLowerCase().contains("bug"));
        assertTrue(PrType.REFACTOR.focus().toLowerCase().contains("maintainability"));
        assertTrue(PrType.PERFORMANCE.focus().toLowerCase().contains("performance"));
        assertTrue(PrType.SECURITY.focus().toLowerCase().contains("security"));
        assertTrue(PrType.TEST.focus().toLowerCase().contains("test"));
        assertTrue(PrType.DOCS.focus().toLowerCase().contains("documentation"));
    }

    @Test
    void shouldHandleCaseInsensitiveInput() {
        assertEquals(PrType.BUGFIX, PrType.detect("Fix: critical issue"));
        assertEquals(PrType.BUGFIX, PrType.detect("FIX: memory leak"));
        assertEquals(PrType.BUGFIX, PrType.detect("fix: URGENT BUG"));

        assertEquals(PrType.FEATURE, PrType.detect("Feat: new feature"));
        assertEquals(PrType.FEATURE, PrType.detect("FEATURE: add support"));
    }

    @Test
    void shouldHandleMultipleKeywordsPrioritizeFirst() {
        assertEquals(PrType.BUGFIX, PrType.detect("fix: refactor security module"));
        assertEquals(PrType.BUGFIX, PrType.detect("refactor: fix old bug"));
        assertEquals(PrType.SECURITY, PrType.detect("security: patch vulnerability"));

        assertEquals(PrType.BUGFIX, PrType.detect("Fix security issue in refactor"));
        assertEquals(PrType.REFACTOR, PrType.detect("Refactor authentication module"));
    }

    @Test
    void shouldHandleSpecialCharacters() {
        assertEquals(PrType.BUGFIX, PrType.detect("fix!: breaking change"));
        assertEquals(PrType.FEATURE, PrType.detect("feat(scope)!: major update"));
        assertEquals(PrType.BUGFIX, PrType.detect("fix: bug with special-chars_123"));
    }

    @Test
    void shouldHandleVeryLongTitles() {
        String longTitle = "fix: ".repeat(50) + "very long description with many repeated words";
        assertEquals(PrType.BUGFIX, PrType.detect(longTitle));

        String longGenericTitle = "Update system to handle very long titles that exceed normal character limits and continue for many more words";
        assertEquals(PrType.FEATURE, PrType.detect(longGenericTitle));
    }

    @Test
    void shouldHandleNonAsciiCharacters() {
        assertEquals(PrType.BUGFIX, PrType.detect("fix: resolve encoding issue"));
        assertEquals(PrType.FEATURE, PrType.detect("feat: add internationalization support"));
        assertEquals(PrType.BUGFIX, PrType.detect("fix: handle unicode characters properly"));
    }

    @Test
    void shouldHandleWhitespaceVariations() {
        assertEquals(PrType.BUGFIX, PrType.detect("fix:no space"));
        assertEquals(PrType.BUGFIX, PrType.detect("fix:  extra  spaces"));
        assertEquals(PrType.BUGFIX, PrType.detect("fix:\ttab character"));
        assertEquals(PrType.BUGFIX, PrType.detect("  fix: leading spaces"));
    }
}
