package com.code.agent.presentation.web;

import com.code.agent.application.service.ReviewCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static com.code.agent.infra.github.GitHubConstants.HMAC_ALGORITHM;
import static com.code.agent.infra.github.GitHubConstants.SIGNATURE_PREFIX;
import static com.code.agent.infra.github.GitHubConstants.WEBHOOK_EVENT_HEADER;
import static com.code.agent.infra.github.GitHubConstants.WEBHOOK_SIGNATURE_HEADER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhookSignatureIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReviewCoordinator reviewCoordinator;

    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final String VALID_PAYLOAD = """
            {
                "action": "opened",
                "number": 123,
                "pull_request": {
                    "diff_url": "https://github.com/test/repo/pull/123.diff"
                },
                "repository": {
                    "name": "repo",
                    "owner": {
                        "login": "test"
                    }
                }
            }
            """;

    @Test
    void shouldAcceptRequestWithValidSignature() {
        // Given: Valid payload and correct signature
        String signature = calculateGitHubSignature(VALID_PAYLOAD, WEBHOOK_SECRET);
        when(reviewCoordinator.startReview(any())).thenReturn(Mono.empty());

        // When: Send webhook request with valid signature
        // Then: Should return 200 OK
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .header(WEBHOOK_SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_PAYLOAD)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectRequestWithInvalidSignature() {
        // Given: Valid payload but incorrect signature
        String invalidSignature = SIGNATURE_PREFIX + "invalid_signature_hash_123456789abcdef";

        // When: Send webhook request with invalid signature
        // Then: Should return 401 Unauthorized
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .header(WEBHOOK_SIGNATURE_HEADER, invalidSignature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_PAYLOAD)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectRequestWithMissingSignature() {
        // Given: Valid payload but no signature header
        // When: Send webhook request without signature
        // Then: Should return 401 Unauthorized
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_PAYLOAD)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectRequestWithInvalidSignatureFormat() {
        // Given: Signature without "sha256=" prefix
        String signatureWithoutPrefix = "abc123def456789";

        // When: Send webhook request with malformed signature
        // Then: Should return 401 Unauthorized
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .header(WEBHOOK_SIGNATURE_HEADER, signatureWithoutPrefix)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_PAYLOAD)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectRequestWhenPayloadIsModified() {
        // Given: Signature calculated for original payload
        String originalPayload = VALID_PAYLOAD;
        String signature = calculateGitHubSignature(originalPayload, WEBHOOK_SECRET);

        // Modified payload (attacker changed the content)
        String modifiedPayload = originalPayload.replace("opened", "closed");

        // When: Send webhook with original signature but modified payload
        // Then: Should return 401 Unauthorized (signature mismatch)
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .header(WEBHOOK_SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(modifiedPayload)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldHandleEmptyPayload() {
        // Given: Empty payload with correct signature
        String emptyPayload = "";
        String signature = calculateGitHubSignature(emptyPayload, WEBHOOK_SECRET);

        // When: Send webhook with empty payload
        // Then: Should return 400 Bad Request (invalid JSON)
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .header(WEBHOOK_SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(emptyPayload)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldHandleMalformedJson() {
        // Given: Malformed JSON with valid signature
        String malformedJson = "{invalid json}";
        String signature = calculateGitHubSignature(malformedJson, WEBHOOK_SECRET);

        // When: Send webhook with malformed JSON
        // Then: Should return 400 Bad Request
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .header(WEBHOOK_SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(malformedJson)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldAcceptNonTriggeringAction() {
        // Given: Valid signature but action that doesn't trigger review
        String nonTriggeringPayload = """
                {
                    "action": "closed",
                    "number": 123,
                    "pull_request": {
                        "diff_url": "https://github.com/test/repo/pull/123.diff"
                    },
                    "repository": {
                        "name": "repo",
                        "owner": {
                            "login": "test"
                        }
                    }
                }
                """;
        String signature = calculateGitHubSignature(nonTriggeringPayload, WEBHOOK_SECRET);

        // When: Send webhook with non-triggering action
        // Then: Should return 200 OK (but no review triggered)
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .header(WEBHOOK_SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(nonTriggeringPayload)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        // Given: Valid signature but missing required fields
        String incompletePayload = """
                {
                    "action": "opened",
                    "number": 123
                }
                """;
        String signature = calculateGitHubSignature(incompletePayload, WEBHOOK_SECRET);

        // When: Send webhook with incomplete payload
        // Then: Should return 400 Bad Request (missing fields)
        webTestClient.post()
                .uri("/api/v1/webhooks/github/pull_request")
                .header(WEBHOOK_EVENT_HEADER, "pull_request")
                .header(WEBHOOK_SIGNATURE_HEADER, signature)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(incompletePayload)
                .exchange()
                .expectStatus().isBadRequest();
    }

    /**
     * Calculate GitHub webhook signature using HMAC-SHA256.
     * Mimics GitHub's signature generation for testing.
     *
     * @param payload the request body
     * @param secret  the webhook secret
     * @return signature in format "sha256=<hex>"
     */
    private String calculateGitHubSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_PREFIX + bytesToHex(hash);
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
