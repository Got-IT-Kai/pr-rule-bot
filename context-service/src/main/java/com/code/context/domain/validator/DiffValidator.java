package com.code.context.domain.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DiffValidator {

    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/.+ b/.+", Pattern.MULTILINE);
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+\\d+(?:,\\d+)? @@", Pattern.MULTILINE);
    private static final Pattern RENAME_FROM = Pattern.compile("^rename from ", Pattern.MULTILINE);
    private static final Pattern RENAME_TO = Pattern.compile("^rename to ", Pattern.MULTILINE);
    private static final Pattern COPY_FROM = Pattern.compile("^copy from ", Pattern.MULTILINE);
    private static final Pattern COPY_TO = Pattern.compile("^copy to ", Pattern.MULTILINE);
    private static final Pattern NEW_FILE = Pattern.compile("^new file mode ", Pattern.MULTILINE);
    private static final Pattern DELETED_FILE = Pattern.compile("^deleted file mode ", Pattern.MULTILINE);
    private static final Pattern OLD_MODE = Pattern.compile("^old mode ", Pattern.MULTILINE);
    private static final Pattern NEW_MODE = Pattern.compile("^new mode ", Pattern.MULTILINE);

    private record ValidationCheck(Predicate<String> predicate, ValidationReason reason) {}

    // Ordered list evaluated until first match
    private static final List<ValidationCheck> HUNKLESS_CHECKS = List.of(
        new ValidationCheck(DiffValidator::isBinary, ValidationReason.BINARY_FILE),
        new ValidationCheck(DiffValidator::isRename, ValidationReason.RENAME_ONLY),
        new ValidationCheck(DiffValidator::isCopy, ValidationReason.COPY_ONLY),
        new ValidationCheck(DiffValidator::isModeOnly, ValidationReason.PERMISSION_ONLY),
        new ValidationCheck(DiffValidator::isNewFile, ValidationReason.NEW_EMPTY_FILE),
        new ValidationCheck(DiffValidator::isDeletedFile, ValidationReason.DELETED_FILE)
    );

    public ValidationResult validate(String diff) {
        // Check for invalid responses
        ValidationResult invalidCheck = checkInvalidResponse(diff);
        if (invalidCheck != null) {
            logValidation(invalidCheck, diff);
            return invalidCheck;
        }

        // Check for diff header
        if (!DIFF_HEADER.matcher(diff).find()) {
            ValidationResult result = ValidationResult.invalid(ValidationReason.NO_DIFF_HEADER);
            logValidation(result, diff);
            return result;
        }

        // Detect diff type and determine if review is needed
        ValidationResult result = detectDiffType(diff);
        logValidation(result, diff);
        return result;
    }

    private ValidationResult checkInvalidResponse(String diff) {
        if (diff == null || diff.trim().isEmpty()) {
            return ValidationResult.invalid(ValidationReason.EMPTY_RESPONSE);
        }

        String trimmed = diff.trim();

        // Check for JSON error responses
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return ValidationResult.invalid(ValidationReason.JSON_RESPONSE);
        }

        // Check for HTML error pages
        String lowerTrimmed = trimmed.toLowerCase(Locale.ROOT);
        if (lowerTrimmed.startsWith("<!doctype") || lowerTrimmed.startsWith("<html")) {
            return ValidationResult.invalid(ValidationReason.HTML_RESPONSE);
        }

        return null;
    }

    private ValidationResult detectDiffType(String diff) {
        boolean hasHunkHeader = HUNK_HEADER.matcher(diff).find();

        // If it has hunks, it has reviewable content
        if (hasHunkHeader) {
            return ValidationResult.valid();
        }

        // No hunks - check for valid hunk-less cases
        return detectHunklessDiffType(diff);
    }

    // Unknown hunk-less cases are treated as MALFORMED_DIFF for safety
    private ValidationResult detectHunklessDiffType(String diff) {
        return HUNKLESS_CHECKS.stream()
            .filter(check -> check.predicate.test(diff))
            .findFirst()
            .map(check -> ValidationResult.skip(check.reason))
            .orElse(ValidationResult.invalid(ValidationReason.MALFORMED_DIFF));
    }

    private static boolean isBinary(String diff) {
        return diff.contains("Binary files") || diff.contains("GIT binary patch");
    }

    private static boolean isRename(String diff) {
        return RENAME_FROM.matcher(diff).find() && RENAME_TO.matcher(diff).find();
    }

    private static boolean isCopy(String diff) {
        return COPY_FROM.matcher(diff).find() && COPY_TO.matcher(diff).find();
    }

    private static boolean isModeOnly(String diff) {
        return OLD_MODE.matcher(diff).find() && NEW_MODE.matcher(diff).find();
    }

    private static boolean isNewFile(String diff) {
        return NEW_FILE.matcher(diff).find();
    }

    private static boolean isDeletedFile(String diff) {
        return DELETED_FILE.matcher(diff).find();
    }

    private void logValidation(ValidationResult result, String diff) {
        int length = diff != null ? diff.length() : 0;

        switch (result.status()) {
            case VALID -> log.debug("Diff validation: {} ({} bytes)", result.getMessage(), length);
            case SKIP -> log.debug("Diff validation: {} - skipping review ({} bytes)", result.getMessage(), length);
            case INVALID -> {
                log.warn("Diff validation failed: {} (length: {} bytes)", result.getMessage(), length);
                if (log.isDebugEnabled()) {
                    log.debug("Invalid diff sample: {}", truncateForDebug(diff, 200));
                }
            }
        }
    }

    // Only called in debug level to prevent code exposure in production logs
    private String truncateForDebug(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... (truncated)";
    }
}
