package com.code.context.domain.validator;

import java.util.Objects;

public record ValidationResult(
        Status status,
        ValidationReason reason
) {
    public enum Status {
        VALID,

        SKIP,

        INVALID
    }

    public static ValidationResult valid() {
        return new ValidationResult(Status.VALID, ValidationReason.CONTENT_CHANGES);
    }

    public static ValidationResult skip(ValidationReason reason) {
        Objects.requireNonNull(reason, "ValidationReason cannot be null");
        return new ValidationResult(Status.SKIP, reason);
    }

    public static ValidationResult invalid(ValidationReason reason) {
        Objects.requireNonNull(reason, "ValidationReason cannot be null");
        return new ValidationResult(Status.INVALID, reason);
    }

    public boolean isValid() {
        return status == Status.VALID;
    }

    public boolean shouldSkip() {
        return status == Status.SKIP;
    }

    public boolean isInvalid() {
        return status == Status.INVALID;
    }

    public String getMessage() {
        return reason.getMessage();
    }
}
