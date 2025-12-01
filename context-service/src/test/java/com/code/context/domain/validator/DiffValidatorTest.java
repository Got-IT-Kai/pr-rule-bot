package com.code.context.domain.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DiffValidator")
class DiffValidatorTest {

    private final DiffValidator validator = new DiffValidator();

    @Nested
    @DisplayName("when validating invalid responses")
    class InvalidResponses {

        @Test
        @DisplayName("should reject null diff")
        void shouldRejectNullDiff() {
            ValidationResult result = validator.validate(null);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.INVALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.EMPTY_RESPONSE);
        }

        @Test
        @DisplayName("should reject empty diff")
        void shouldRejectEmptyDiff() {
            ValidationResult result = validator.validate("");

            assertThat(result.status()).isEqualTo(ValidationResult.Status.INVALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.EMPTY_RESPONSE);
        }

        @Test
        @DisplayName("should reject whitespace-only diff")
        void shouldRejectWhitespaceOnlyDiff() {
            ValidationResult result = validator.validate("   \n\t  ");

            assertThat(result.status()).isEqualTo(ValidationResult.Status.INVALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.EMPTY_RESPONSE);
        }

        @Test
        @DisplayName("should reject JSON response")
        void shouldRejectJsonResponse() {
            String jsonError = "{\"message\": \"Not Found\"}";

            ValidationResult result = validator.validate(jsonError);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.INVALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.JSON_RESPONSE);
        }

        @Test
        @DisplayName("should reject HTML response")
        void shouldRejectHtmlResponse() {
            String htmlError = "<!DOCTYPE html><html><body>Error</body></html>";

            ValidationResult result = validator.validate(htmlError);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.INVALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.HTML_RESPONSE);
        }

        @Test
        @DisplayName("should reject HTML with lowercase doctype")
        void shouldRejectHtmlWithLowercaseDoctype() {
            String htmlError = "<!doctype html><html><body>Error</body></html>";

            ValidationResult result = validator.validate(htmlError);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.INVALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.HTML_RESPONSE);
        }

        @Test
        @DisplayName("should reject diff without header")
        void shouldRejectDiffWithoutHeader() {
            String invalidDiff = "@@ -1,3 +1,3 @@\n-old\n+new";

            ValidationResult result = validator.validate(invalidDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.INVALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.NO_DIFF_HEADER);
        }

        @Test
        @DisplayName("should reject malformed hunk-less diff")
        void shouldRejectMalformedHunklessDiff() {
            String malformedDiff = "diff --git a/file.txt b/file.txt\nindex 1234..5678\nSome random content";

            ValidationResult result = validator.validate(malformedDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.INVALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.MALFORMED_DIFF);
        }
    }

    @Nested
    @DisplayName("when validating valid reviewable diffs")
    class ValidDiffs {

        @Test
        @DisplayName("should accept standard diff with hunks")
        void shouldAcceptStandardDiff() {
            String validDiff = """
                diff --git a/file.txt b/file.txt
                index 1234..5678 100644
                --- a/file.txt
                +++ b/file.txt
                @@ -1,3 +1,3 @@
                 context line
                -old line
                +new line
                 context line
                """;

            ValidationResult result = validator.validate(validDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.VALID);
            assertThat(result.reason()).isEqualTo(ValidationReason.CONTENT_CHANGES);
        }

        @Test
        @DisplayName("should accept diff with multiple hunks")
        void shouldAcceptDiffWithMultipleHunks() {
            String multiHunkDiff = """
                diff --git a/file.txt b/file.txt
                @@ -1,2 +1,2 @@
                -old1
                +new1
                @@ -10,2 +10,2 @@
                -old2
                +new2
                """;

            ValidationResult result = validator.validate(multiHunkDiff);

            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("when validating skip-worthy diffs")
    class SkipDiffs {

        @Test
        @DisplayName("should skip binary file diff (standard Git format)")
        void shouldSkipBinaryFile() {
            String binaryDiff = """
                diff --git a/image.png b/image.png
                Binary files a/image.png and b/image.png differ
                """;

            ValidationResult result = validator.validate(binaryDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.SKIP);
            assertThat(result.reason()).isEqualTo(ValidationReason.BINARY_FILE);
        }

        @Test
        @DisplayName("should skip GIT binary patch (standard Git format)")
        void shouldSkipGitBinaryPatch() {
            String binaryDiff = """
                diff --git a/image.png b/image.png
                GIT binary patch
                literal 1234
                """;

            ValidationResult result = validator.validate(binaryDiff);

            assertThat(result.shouldSkip()).isTrue();
            assertThat(result.reason()).isEqualTo(ValidationReason.BINARY_FILE);
        }

        @Test
        @DisplayName("should skip rename-only diff")
        void shouldSkipRenameOnly() {
            String renameDiff = """
                diff --git a/old.txt b/new.txt
                similarity index 100%
                rename from old.txt
                rename to new.txt
                """;

            ValidationResult result = validator.validate(renameDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.SKIP);
            assertThat(result.reason()).isEqualTo(ValidationReason.RENAME_ONLY);
        }

        @Test
        @DisplayName("should skip copy-only diff")
        void shouldSkipCopyOnly() {
            String copyDiff = """
                diff --git a/original.txt b/copy.txt
                similarity index 100%
                copy from original.txt
                copy to copy.txt
                """;

            ValidationResult result = validator.validate(copyDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.SKIP);
            assertThat(result.reason()).isEqualTo(ValidationReason.COPY_ONLY);
        }

        @Test
        @DisplayName("should skip permission-only change")
        void shouldSkipPermissionOnly() {
            String modeDiff = """
                diff --git a/script.sh b/script.sh
                old mode 100644
                new mode 100755
                """;

            ValidationResult result = validator.validate(modeDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.SKIP);
            assertThat(result.reason()).isEqualTo(ValidationReason.PERMISSION_ONLY);
        }

        @Test
        @DisplayName("should skip new empty file")
        void shouldSkipNewEmptyFile() {
            String newFileDiff = """
                diff --git a/empty.txt b/empty.txt
                new file mode 100644
                index 0000000..e69de29
                """;

            ValidationResult result = validator.validate(newFileDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.SKIP);
            assertThat(result.reason()).isEqualTo(ValidationReason.NEW_EMPTY_FILE);
        }

        @Test
        @DisplayName("should skip deleted file")
        void shouldSkipDeletedFile() {
            String deleteDiff = """
                diff --git a/removed.txt b/removed.txt
                deleted file mode 100644
                index 1234567..0000000
                """;

            ValidationResult result = validator.validate(deleteDiff);

            assertThat(result.status()).isEqualTo(ValidationResult.Status.SKIP);
            assertThat(result.reason()).isEqualTo(ValidationReason.DELETED_FILE);
        }
    }

    @Nested
    @DisplayName("when checking helper methods")
    class HelperMethods {

        @Test
        @DisplayName("should provide human-readable message")
        void shouldProvideReadableMessage() {
            ValidationResult result = ValidationResult.skip(ValidationReason.BINARY_FILE);

            assertThat(result.getMessage()).isEqualTo("Binary file change");
        }

        @Test
        @DisplayName("should provide convenience check methods")
        void shouldProvideConvenienceChecks() {
            ValidationResult valid = ValidationResult.valid();
            ValidationResult skip = ValidationResult.skip(ValidationReason.RENAME_ONLY);
            ValidationResult invalid = ValidationResult.invalid(ValidationReason.EMPTY_RESPONSE);

            assertThat(valid.isValid()).isTrue();
            assertThat(valid.shouldSkip()).isFalse();
            assertThat(valid.isInvalid()).isFalse();

            assertThat(skip.isValid()).isFalse();
            assertThat(skip.shouldSkip()).isTrue();
            assertThat(skip.isInvalid()).isFalse();

            assertThat(invalid.isValid()).isFalse();
            assertThat(invalid.shouldSkip()).isFalse();
            assertThat(invalid.isInvalid()).isTrue();
        }
    }
}
