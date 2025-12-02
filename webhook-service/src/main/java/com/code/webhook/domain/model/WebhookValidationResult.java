package com.code.webhook.domain.model;

public record WebhookValidationResult(
        boolean isValid,
        String failureReason
) {
    public static WebhookValidationResult valid() {
        return new WebhookValidationResult(true, null);
    }

    public static WebhookValidationResult invalid(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Failure reason must not be null or blank");
        }
        return new WebhookValidationResult(false, reason);
    }
}
