package com.code.webhook.domain.model;

import com.code.events.webhook.WebhookAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookActionTest {

    @Test
    void triggersReview_opened_returnsTrue() {
        // When
        boolean result = WebhookAction.OPENED.triggersReview();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void triggersReview_synchronize_returnsTrue() {
        // When
        boolean result = WebhookAction.SYNCHRONIZE.triggersReview();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void triggersReview_reopened_returnsTrue() {
        // When
        boolean result = WebhookAction.REOPENED.triggersReview();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void triggersReview_closed_returnsFalse() {
        // When
        boolean result = WebhookAction.CLOSED.triggersReview();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void triggersReview_edited_returnsFalse() {
        // When
        boolean result = WebhookAction.EDITED.triggersReview();

        // Then
        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @EnumSource(names = {"OPENED", "SYNCHRONIZE", "REOPENED"})
    void triggersReview_reviewTriggeringActions_returnTrue(WebhookAction action) {
        // When
        boolean result = action.triggersReview();

        // Then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"CLOSED", "EDITED"})
    void triggersReview_nonReviewTriggeringActions_returnFalse(WebhookAction action) {
        // When
        boolean result = action.triggersReview();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void valueOf_allActions_canBeCreatedFromString() {
        // When & Then
        assertThat(WebhookAction.valueOf("OPENED")).isEqualTo(WebhookAction.OPENED);
        assertThat(WebhookAction.valueOf("SYNCHRONIZE")).isEqualTo(WebhookAction.SYNCHRONIZE);
        assertThat(WebhookAction.valueOf("REOPENED")).isEqualTo(WebhookAction.REOPENED);
        assertThat(WebhookAction.valueOf("CLOSED")).isEqualTo(WebhookAction.CLOSED);
        assertThat(WebhookAction.valueOf("EDITED")).isEqualTo(WebhookAction.EDITED);
    }
}
