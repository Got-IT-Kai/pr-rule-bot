package com.code.agent.infra.github.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.code.agent.infra.github.GitHubConstants.*;

/**
 * Validates GitHub webhook signatures using HMAC-SHA256.
 * Implements secure signature verification to prevent unauthorized webhook requests.
 *
 * @see <a href="https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries">GitHub Webhook Security</a>
 */
@Slf4j
@Component
public class WebhookSignatureValidator {

    /**
     * Validates webhook signature using HMAC-SHA256.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param payload   the raw request body as received from GitHub
     * @param signature the X-Hub-Signature-256 header value
     * @param secret    the webhook secret configured in GitHub
     * @return true if signature is valid, false otherwise
     */
    public boolean isValid(String payload, String signature, String secret) {
        if (payload == null || signature == null || secret == null) {
            log.warn(LOG_VALIDATION_FAILED_NULL);
            return false;
        }

        if (!signature.startsWith(SIGNATURE_PREFIX)) {
            log.warn(LOG_VALIDATION_FAILED_FORMAT, SIGNATURE_PREFIX);
            return false;
        }

        try {
            String expectedSignature = calculateSignature(payload, secret);
            String receivedSignature = signature.substring(SIGNATURE_PREFIX.length());

            // Use constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error(LOG_VALIDATION_FAILED_CRYPTO, e);
            return false;
        }
    }

    private String calculateSignature(String payload, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
        mac.init(secretKeySpec);

        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
