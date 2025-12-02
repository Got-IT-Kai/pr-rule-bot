package com.code.context.domain.exception;

import com.code.context.domain.validator.ValidationResult;

public class InvalidDiffException extends RuntimeException {

    private final ValidationResult validationResult;

    public InvalidDiffException(ValidationResult validationResult) {
        super("Invalid diff format: " + validationResult.getMessage());
        this.validationResult = validationResult;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
