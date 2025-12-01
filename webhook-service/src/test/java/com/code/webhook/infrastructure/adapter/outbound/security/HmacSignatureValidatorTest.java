package com.code.webhook.infrastructure.adapter.outbound.security;

import com.code.webhook.domain.model.WebhookValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureValidatorTest {

    private HmacSignatureValidator validator;

    private static final String TEST_SECRET = "test-secret-key";
    private static final String TEST_PAYLOAD = "{\"action\":\"opened\",\"number\":123}";

    @BeforeEach
    void setUp() {
        validator = new HmacSignatureValidator();
    }

    @Test
    void validate_correctSignature_returnsValid() {
        // Given
        byte[] payload = "test payload".getBytes(StandardCharsets.UTF_8);
        String secret = "my-secret";
        String correctSignature = calculateExpectedSignature(payload, secret);

        // When
        WebhookValidationResult result = validator.validate(payload, correctSignature, secret);

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void validate_invalidSignature_returnsInvalid() {
        // Given
        byte[] payload = TEST_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String invalidSignature = "sha256=aabbccdd";  // Valid hex format but wrong signature

        // When
        WebhookValidationResult result = validator.validate(payload, invalidSignature, TEST_SECRET);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).contains("Signature mismatch");
    }

    @Test
    void validate_signatureWithoutPrefix_returnsInvalid() {
        // Given
        byte[] payload = TEST_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String signatureWithoutPrefix = "c8b3e1c9d8e5a8c8f9d0b1a2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2";

        // When
        WebhookValidationResult result = validator.validate(payload, signatureWithoutPrefix, TEST_SECRET);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).contains("missing 'sha256=' prefix");
    }

    @Test
    void validate_nullPayload_returnsInvalid() {
        // Given
        String validSignature = "sha256=somehash";

        // When
        WebhookValidationResult result = validator.validate(null, validSignature, TEST_SECRET);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).contains("Payload is null or empty");
    }

    @Test
    void validate_emptyPayload_returnsInvalid() {
        // Given
        byte[] emptyPayload = new byte[0];
        String validSignature = "sha256=somehash";

        // When
        WebhookValidationResult result = validator.validate(emptyPayload, validSignature, TEST_SECRET);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).contains("Payload is null or empty");
    }

    @Test
    void validate_nullSignature_returnsInvalid() {
        // Given
        byte[] payload = TEST_PAYLOAD.getBytes(StandardCharsets.UTF_8);

        // When
        WebhookValidationResult result = validator.validate(payload, null, TEST_SECRET);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).contains("Signature is null");
    }

    @Test
    void validate_nullSecret_returnsInvalid() {
        // Given
        byte[] payload = TEST_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String validSignature = "sha256=somehash";

        // When
        WebhookValidationResult result = validator.validate(payload, validSignature, null);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).contains("Secret is null");
    }

    @Test
    void validate_knownTestVector_returnsValid() {
        // Given: Known test vector
        String payloadString = "Hello, World!";
        byte[] payload = payloadString.getBytes(StandardCharsets.UTF_8);
        String secret = "It's a Secret to Everybody";
        String expectedSignature = calculateExpectedSignature(payload, secret);

        // When
        WebhookValidationResult result = validator.validate(payload, expectedSignature, secret);

        // Then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_signatureWithDifferentSecret_returnsInvalid() {
        // Given
        String correctSecret = "correct-secret";
        String wrongSecret = "wrong-secret";
        String payloadString = "test payload";
        byte[] payload = payloadString.getBytes(StandardCharsets.UTF_8);

        String signatureWithWrongSecret = calculateExpectedSignature(payload, wrongSecret);

        // When
        WebhookValidationResult result = validator.validate(payload, signatureWithWrongSecret, correctSecret);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).contains("Signature mismatch");
    }

    @Test
    void validate_unicodePayload_returnsValid() {
        // Given
        String unicodePayloadString = "{\"message\":\"Hello World with unicode characters\"}";
        byte[] unicodePayload = unicodePayloadString.getBytes(StandardCharsets.UTF_8);
        String secret = "unicode-secret";
        String signature = calculateExpectedSignature(unicodePayload, secret);

        // When
        WebhookValidationResult result = validator.validate(unicodePayload, signature, secret);

        // Then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_modifiedPayload_returnsInvalid() {
        // Given
        String originalPayloadString = "{\"action\":\"opened\"}";
        byte[] originalPayload = originalPayloadString.getBytes(StandardCharsets.UTF_8);
        String secret = "my-secret";
        String originalSignature = calculateExpectedSignature(originalPayload, secret);

        // Modified payload (attacker changed content)
        String modifiedPayloadString = "{\"action\":\"closed\"}";
        byte[] modifiedPayload = modifiedPayloadString.getBytes(StandardCharsets.UTF_8);

        // When
        WebhookValidationResult result = validator.validate(modifiedPayload, originalSignature, secret);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).contains("Signature mismatch");
    }

    @Test
    void validate_uppercaseHexSignature_returnsInvalid() {
        // Given
        String payloadString = "test";
        byte[] payload = payloadString.getBytes(StandardCharsets.UTF_8);
        String secret = "secret";
        String lowercaseSignature = calculateExpectedSignature(payload, secret);
        String uppercaseSignature = lowercaseSignature.toUpperCase(java.util.Locale.ROOT);

        // When
        WebhookValidationResult result = validator.validate(payload, uppercaseSignature, secret);

        // Then: Hex must be lowercase
        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validate_largePayload_returnsValid() {
        // Given: Large payload (1MB)
        byte[] largePayload = "x".repeat(1024 * 1024).getBytes(StandardCharsets.UTF_8);
        String secret = "test-secret";
        String signature = calculateExpectedSignature(largePayload, secret);

        // When
        WebhookValidationResult result = validator.validate(largePayload, signature, secret);

        // Then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_invalidHexFormat_returnsInvalid() {
        // Given
        byte[] payload = TEST_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String invalidHexSignature = "sha256=zzzz"; // 'z' is not a hex character

        // When
        WebhookValidationResult result = validator.validate(payload, invalidHexSignature, TEST_SECRET);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).isNotNull();
    }

    @Test
    void validate_oddLengthHex_returnsInvalid() {
        // Given
        byte[] payload = TEST_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        String oddLengthSignature = "sha256=abc"; // Odd number of hex characters

        // When
        WebhookValidationResult result = validator.validate(payload, oddLengthSignature, TEST_SECRET);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.failureReason()).isNotNull();
    }

    private String calculateExpectedSignature(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload);
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
