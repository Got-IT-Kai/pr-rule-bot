package com.code.review.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrContextTest {

    @Test
    void shouldCreateContextFromValidTitle() {
        PrContext context = PrContext.from("fix: resolve authentication bug");

        assertNotNull(context);
        assertEquals(PrType.BUGFIX, context.type());
        assertEquals("fix: resolve authentication bug", context.title());
        assertNotNull(context.focus());
    }

    @Test
    void shouldHandleNullTitleGracefully() {
        PrContext context = PrContext.from(null);

        assertNotNull(context);
        assertEquals(PrType.UNKNOWN, context.type());
        assertNull(context.title());
        assertNull(context.focus());
    }

    @Test
    void shouldHandleEmptyTitleGracefully() {
        PrContext context = PrContext.from("");

        assertNotNull(context);
        assertEquals(PrType.UNKNOWN, context.type());
        assertEquals("", context.title());
        assertNull(context.focus());
    }

    @Test
    void shouldProvideTypeSpecificFocus() {
        PrContext bugfixContext = PrContext.from("fix: critical error");
        assertEquals(PrType.BUGFIX, bugfixContext.type());
        assertNotNull(bugfixContext.focus());
        assertTrue(bugfixContext.focus().contains("bug"));

        PrContext refactorContext = PrContext.from("refactor: improve service layer");
        assertEquals(PrType.REFACTOR, refactorContext.type());
        assertNotNull(refactorContext.focus());
        assertTrue(refactorContext.focus().contains("maintainability"));

        PrContext securityContext = PrContext.from("security: patch XSS vulnerability");
        assertEquals(PrType.SECURITY, securityContext.type());
        assertNotNull(securityContext.focus());
        assertTrue(securityContext.focus().toLowerCase().contains("security"));
    }

    @Test
    void shouldNotProvideFocusForGenericTypes() {
        PrContext featureContext = PrContext.from("feat: add new dashboard");
        assertEquals(PrType.FEATURE, featureContext.type());
        assertNull(featureContext.focus());

        PrContext choreContext = PrContext.from("chore: update dependencies");
        assertEquals(PrType.CHORE, choreContext.type());
        assertNull(choreContext.focus());
    }

    @Test
    void shouldWorkWithNaturalLanguageTitles() {
        PrContext context = PrContext.from("Update user authentication system");

        assertNotNull(context);
        assertEquals(PrType.FEATURE, context.type());
        assertEquals("Update user authentication system", context.title());
        assertNull(context.focus());
    }
}
