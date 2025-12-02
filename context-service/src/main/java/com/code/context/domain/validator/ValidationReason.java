package com.code.context.domain.validator;

public enum ValidationReason {
    // VALID cases
    CONTENT_CHANGES("Diff contains reviewable content changes"),

    // SKIP cases - known scenarios
    RENAME_ONLY("File rename without content changes"),
    NEW_EMPTY_FILE("New empty file created"),
    DELETED_FILE("File deleted"),
    BINARY_FILE("Binary file change"),
    COPY_ONLY("File copy without content changes"),
    PERMISSION_ONLY("File permission change only"),
    DIFF_TOO_LARGE("Diff exceeds Kafka message size limit"),

    // SKIP cases - fallback for future cases
    OTHER_SKIP("Other valid diff that should skip review"),

    // INVALID cases - known errors
    EMPTY_RESPONSE("Empty or null response"),
    JSON_RESPONSE("Response is JSON, not diff format"),
    HTML_RESPONSE("Response is HTML, not diff format"),
    NO_DIFF_HEADER("Missing required diff header"),
    MALFORMED_DIFF("Malformed diff format"),

    // INVALID cases - fallback for future cases
    OTHER_INVALID("Other invalid response format");

    private final String message;

    ValidationReason(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
