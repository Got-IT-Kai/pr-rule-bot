package com.code.webhook.infrastructure.adapter.outbound.security;

import com.code.webhook.application.port.outbound.SignatureValidator;
import com.code.webhook.domain.model.WebhookValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public final class HmacSignatureValidator implements SignatureValidator {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Override
    public WebhookValidationResult validate(byte[] payload, String signature, String secret) {
        if (payload == null || payload.length == 0) {
            return WebhookValidationResult.invalid("Payload is null or empty");
        }

        if (signature == null) {
            return WebhookValidationResult.invalid("Signature is null");
        }

        if (secret == null) {
            return WebhookValidationResult.invalid("Secret is null");
        }

        if (!signature.startsWith(SIGNATURE_PREFIX)) {
            return WebhookValidationResult.invalid("Invalid signature format (missing 'sha256=' prefix)");
        }

        try {
            String hexString = signature.substring(SIGNATURE_PREFIX.length());

            // Validate hex string length is even
            if (hexString.length() % 2 != 0) {
                return WebhookValidationResult.invalid("Invalid signature format: odd-length hex string");
            }

            byte[] expectedHash = calculateSignature(payload, secret);
            byte[] receivedHash = hexToBytes(hexString);

            // Use constant-time comparison to prevent timing attacks
            boolean isValid = MessageDigest.isEqual(expectedHash, receivedHash);

            return isValid ? WebhookValidationResult.valid() :
                    WebhookValidationResult.invalid("Signature mismatch");

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to validate webhook signature due to cryptographic error", e);
            return WebhookValidationResult.invalid("Cryptographic error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse signature hex string", e);
            return WebhookValidationResult.invalid("Invalid signature format");
        }
    }

    private byte[] calculateSignature(byte[] payload, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
        mac.init(secretKeySpec);
        return mac.doFinal(payload);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            char c1 = hex.charAt(i);
            char c2 = hex.charAt(i + 1);

            // GitHub uses lowercase hex only - reject uppercase
            if (!isLowercaseHex(c1) || !isLowercaseHex(c2)) {
                throw new IllegalArgumentException("Invalid hex character (must be lowercase)");
            }

            data[i / 2] = (byte) ((Character.digit(c1, 16) << 4)
                    + Character.digit(c2, 16));
        }
        return data;
    }

    private boolean isLowercaseHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
