package com.code.webhook.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookValidationResultTest {

    @Test
    void valid_createsValidResult() {
        // When
        WebhookValidationResult result = WebhookValidationResult.valid();

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void invalid_withReason_createsInvalidResult() {
        // Given
        String reason = "Signature mismatch";

        // When
        WebhookValidationResult result = WebhookValidationResult.invalid(reason);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(reason);
    }

    @Test
    void invalid_withDetailedReason_preservesMessage() {
        // Given
        String detailedReason = "Invalid signature format: missing 'sha256=' prefix";

        // When
        WebhookValidationResult result = WebhookValidationResult.invalid(detailedReason);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).isEqualTo(detailedReason);
    }

    @Test
    void invalid_withNullReason_throwsException() {
        // When & Then
        assertThatThrownBy(() -> WebhookValidationResult.invalid(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failure reason must not be null or blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void invalid_withBlankReason_throwsException(String blankReason) {
        // When & Then
        assertThatThrownBy(() -> WebhookValidationResult.invalid(blankReason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failure reason must not be null or blank");
    }

    @Test
    void equals_sameValues_areEqual() {
        // Given
        WebhookValidationResult result1 = WebhookValidationResult.invalid("Same reason");
        WebhookValidationResult result2 = WebhookValidationResult.invalid("Same reason");

        // When & Then
        assertThat(result1).isEqualTo(result2);
    }

    @Test
    void equals_differentReasons_areNotEqual() {
        // Given
        WebhookValidationResult result1 = WebhookValidationResult.invalid("Reason 1");
        WebhookValidationResult result2 = WebhookValidationResult.invalid("Reason 2");

        // When & Then
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void equals_validAndInvalid_areNotEqual() {
        // Given
        WebhookValidationResult valid = WebhookValidationResult.valid();
        WebhookValidationResult invalid = WebhookValidationResult.invalid("Some reason");

        // When & Then
        assertThat(valid).isNotEqualTo(invalid);
    }

    @Test
    void hashCode_sameValues_haveSameHashCode() {
        // Given
        WebhookValidationResult result1 = WebhookValidationResult.valid();
        WebhookValidationResult result2 = WebhookValidationResult.valid();

        // When & Then
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void toString_validResult_containsIsValid() {
        // Given
        WebhookValidationResult result = WebhookValidationResult.valid();

        // When
        String string = result.toString();

        // Then
        assertThat(string).contains("isValid=true");
        assertThat(string).contains("failureReason=null");
    }

    @Test
    void toString_invalidResult_containsReasonAndIsValid() {
        // Given
        WebhookValidationResult result = WebhookValidationResult.invalid("Test reason");

        // When
        String string = result.toString();

        // Then
        assertThat(string).contains("isValid=false");
        assertThat(string).contains("failureReason=Test reason");
    }
}
