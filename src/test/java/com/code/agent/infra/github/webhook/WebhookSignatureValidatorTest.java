package com.code.agent.infra.github.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;

    private static final String TEST_SECRET = "test-secret-key";
    private static final String TEST_PAYLOAD = "{\"action\":\"opened\",\"number\":123}";

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator();
    }

    @Test
    void shouldValidateCorrectSignature() {
        // Given: Payload and correct signature
        String correctSignature = "sha256=c8b3e1c9d8e5a8c8f9d0b1a2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2";
        String payload = "test payload";
        String secret = "my-secret";

        // Calculate expected signature
        String expectedSignature = calculateExpectedSignature(payload, secret);

        // When: Validate signature
        boolean result = validator.isValid(payload, expectedSignature, secret);

        // Then: Should return true
        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectInvalidSignature() {
        // Given: Payload with incorrect signature
        String invalidSignature = "sha256=invalid_hash_value";

        // When: Validate signature
        boolean result = validator.isValid(TEST_PAYLOAD, invalidSignature, TEST_SECRET);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectSignatureWithoutPrefix() {
        // Given: Signature without "sha256=" prefix
        String signatureWithoutPrefix = "c8b3e1c9d8e5a8c8f9d0b1a2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2";

        // When: Validate signature
        boolean result = validator.isValid(TEST_PAYLOAD, signatureWithoutPrefix, TEST_SECRET);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectNullPayload() {
        // Given: Null payload
        String validSignature = "sha256=somehash";

        // When: Validate with null payload
        boolean result = validator.isValid(null, validSignature, TEST_SECRET);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectNullSignature() {
        // Given: Null signature
        // When: Validate with null signature
        boolean result = validator.isValid(TEST_PAYLOAD, null, TEST_SECRET);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectNullSecret() {
        // Given: Null secret
        String validSignature = "sha256=somehash";

        // When: Validate with null secret
        boolean result = validator.isValid(TEST_PAYLOAD, validSignature, null);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldValidateEmptyAndWhitespacePayload(String payload) {
        // Given: Empty or whitespace payload (valid but empty)
        String signature = calculateExpectedSignature(payload, TEST_SECRET);

        // When: Validate empty/whitespace payload
        boolean result = validator.isValid(payload, signature, TEST_SECRET);

        // Then: Should return true (empty/whitespace are valid payloads, just contain no data)
        assertThat(result).isTrue();
    }

    @Test
    void shouldValidateKnownTestVector() {
        // Given: Known test vector from GitHub documentation style
        String payload = "Hello, World!";
        String secret = "It's a Secret to Everybody";

        // Calculate expected signature for this test vector
        String expectedSignature = calculateExpectedSignature(payload, secret);

        // When: Validate with known values
        boolean result = validator.isValid(payload, expectedSignature, secret);

        // Then: Should return true
        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectSignatureWithDifferentSecret() {
        // Given: Signature calculated with different secret
        String correctSecret = "correct-secret";
        String wrongSecret = "wrong-secret";
        String payload = "test payload";

        String signatureWithWrongSecret = calculateExpectedSignature(payload, wrongSecret);

        // When: Validate with correct secret but signature from wrong secret
        boolean result = validator.isValid(payload, signatureWithWrongSecret, correctSecret);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    void shouldValidateUnicodePayload() {
        // Given: Payload with unicode characters
        String unicodePayload = "{\"message\":\"Hello 世界 🌍\"}";
        String secret = "unicode-secret";
        String signature = calculateExpectedSignature(unicodePayload, secret);

        // When: Validate unicode payload
        boolean result = validator.isValid(unicodePayload, signature, secret);

        // Then: Should return true (UTF-8 encoding handled correctly)
        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectModifiedPayload() {
        // Given: Original payload and its signature
        String originalPayload = "{\"action\":\"opened\"}";
        String secret = "my-secret";
        String originalSignature = calculateExpectedSignature(originalPayload, secret);

        // Modified payload (attacker changed content)
        String modifiedPayload = "{\"action\":\"closed\"}";

        // When: Validate modified payload with original signature
        boolean result = validator.isValid(modifiedPayload, originalSignature, secret);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    void shouldHandleEmptyPayload() {
        // Given: Empty payload with correct signature
        String emptyPayload = "";
        String secret = "test-secret";
        String signature = calculateExpectedSignature(emptyPayload, secret);

        // When: Validate empty payload
        boolean result = validator.isValid(emptyPayload, signature, secret);

        // Then: Should return true (empty is valid)
        assertThat(result).isTrue();
    }

    @Test
    void shouldBeCaseSensitiveForHexSignature() {
        // Given: Signature with uppercase hex
        String payload = "test";
        String secret = "secret";
        String lowercaseSignature = calculateExpectedSignature(payload, secret);
        String uppercaseSignature = lowercaseSignature.toUpperCase(java.util.Locale.ROOT);

        // When: Validate with uppercase signature
        boolean result = validator.isValid(payload, uppercaseSignature, secret);

        // Then: Should return false (hex must be lowercase)
        assertThat(result).isFalse();
    }

    @Test
    void shouldHandleLargePayload() {
        // Given: Large payload (1MB)
        String largePayload = "x".repeat(1024 * 1024);
        String secret = "test-secret";
        String signature = calculateExpectedSignature(largePayload, secret);

        // When: Validate large payload
        boolean result = validator.isValid(largePayload, signature, secret);

        // Then: Should return true
        assertThat(result).isTrue();
    }

    /**
     * Helper method to calculate expected GitHub signature for testing.
     */
    private String calculateExpectedSignature(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "sha256=" + bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate signature", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
