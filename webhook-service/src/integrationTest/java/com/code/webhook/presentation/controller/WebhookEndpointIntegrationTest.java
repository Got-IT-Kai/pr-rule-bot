package com.code.webhook.presentation.controller;

import com.code.webhook.application.port.outbound.EventPublisher;
import com.code.webhook.domain.model.WebhookValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "webhook.secret=test-secret-key",
        // GitHub API configuration for platform-commons auto-configuration
        "github.api.base-url=https://api.github.com",
        "github.api.token=test-github-token-for-integration-test",
        "github.api.timeout.connect=5s",
        "github.api.timeout.read=10s",
        "github.api.retry.max-attempts=3",
        "github.api.retry.backoff=1s"
})
@DisplayName("Webhook Endpoint Integration Test")
class WebhookEndpointIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private EventPublisher eventPublisher;

    @MockitoBean
    private com.code.webhook.application.port.outbound.SignatureValidator signatureValidator;

    private static final String WEBHOOK_ENDPOINT = "/api/v1/webhooks/github/pull_request";
    private static final String WEBHOOK_SECRET = "test-secret-key";
    private static final String DELIVERY_ID = "test-delivery-123";
    private static final String GITHUB_EVENT_HEADER = "X-GitHub-Event";

    private static final String VALID_PAYLOAD = """
            {
                "action": "opened",
                "number": 123,
                "repository": {
                    "owner": {
                        "login": "test-owner"
                    },
                    "name": "test-repo"
                },
                "pull_request": {
                    "diff_url": "https://api.github.com/repos/test-owner/test-repo/pulls/123.diff",
                    "html_url": "https://github.com/test-owner/test-repo/pull/123",
                    "title": "Test PR",
                    "user": {
                        "login": "test-user"
                    },
                    "head": {
                        "sha": "abc123def456"
                    }
                }
            }
            """;

    @Nested
    @DisplayName("when receiving valid webhook")
    class WhenReceivingValidWebhook {

        @Test
        @DisplayName("should return 202 Accepted")
        void shouldReturn202Accepted() {
            when(signatureValidator.validate(any(byte[].class), anyString(), anyString()))
                    .thenReturn(WebhookValidationResult.valid());
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            byte[] payloadBytes = VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8);
            String validSignature = calculateSignature(VALID_PAYLOAD);

            webTestClient.post()
                    .uri(WEBHOOK_ENDPOINT)
                    .header(GITHUB_EVENT_HEADER, "pull_request")
                    .header("X-Hub-Signature-256", validSignature)
                    .header("X-GitHub-Delivery", "delivery-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payloadBytes)
                    .exchange()
                    .expectStatus().isAccepted();
        }

        @Test
        @DisplayName("should publish event")
        void shouldPublishEvent() {
            when(signatureValidator.validate(any(byte[].class), anyString(), anyString()))
                    .thenReturn(WebhookValidationResult.valid());
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());

            byte[] payloadBytes = VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8);
            String validSignature = calculateSignature(VALID_PAYLOAD);

            webTestClient.post()
                    .uri(WEBHOOK_ENDPOINT)
                    .header(GITHUB_EVENT_HEADER, "pull_request")
                    .header("X-Hub-Signature-256", validSignature)
                    .header("X-GitHub-Delivery", "delivery-2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payloadBytes)
                    .exchange()
                    .expectStatus().isAccepted();

            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("when receiving invalid webhook")
    class WhenReceivingInvalidWebhook {

        @Test
        @DisplayName("should return 401 for invalid signature")
        void shouldReturn401ForInvalidSignature() {
            when(signatureValidator.validate(any(byte[].class), anyString(), anyString()))
                    .thenReturn(WebhookValidationResult.invalid("Signature mismatch"));

            webTestClient.post()
                    .uri(WEBHOOK_ENDPOINT)
                    .header(GITHUB_EVENT_HEADER, "pull_request")
                    .header("X-Hub-Signature-256", "sha256=invalid")
                    .header("X-GitHub-Delivery", DELIVERY_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(VALID_PAYLOAD)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should return 401 for missing signature header")
        void shouldReturn401ForMissingSignature() {
            when(signatureValidator.validate(any(byte[].class), anyString(), anyString()))
                    .thenReturn(WebhookValidationResult.invalid("Signature missing"));

            webTestClient.post()
                    .uri(WEBHOOK_ENDPOINT)
                    .header(GITHUB_EVENT_HEADER, "pull_request")
                    .header("X-GitHub-Delivery", DELIVERY_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(VALID_PAYLOAD)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should return 401 for missing delivery ID")
        void shouldReturn401ForMissingDeliveryId() {
            when(signatureValidator.validate(any(byte[].class), anyString(), anyString()))
                    .thenReturn(WebhookValidationResult.invalid("Delivery ID missing"));

            webTestClient.post()
                    .uri(WEBHOOK_ENDPOINT)
                    .header(GITHUB_EVENT_HEADER, "pull_request")
                    .header("X-Hub-Signature-256", "sha256=test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(VALID_PAYLOAD)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should return 400 for invalid JSON")
        void shouldReturn400ForInvalidJson() {
            when(signatureValidator.validate(any(byte[].class), anyString(), anyString()))
                    .thenReturn(WebhookValidationResult.valid());

            String invalidJson = "invalid json";

            webTestClient.post()
                    .uri(WEBHOOK_ENDPOINT)
                    .header(GITHUB_EVENT_HEADER, "pull_request")
                    .header("X-Hub-Signature-256", "sha256=test")
                    .header("X-GitHub-Delivery", DELIVERY_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invalidJson)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("should return 500 for event publishing failure")
        void shouldReturn500ForEventPublishingFailure() {
            when(signatureValidator.validate(any(byte[].class), anyString(), anyString()))
                    .thenReturn(WebhookValidationResult.valid());
            when(eventPublisher.publish(any())).thenReturn(Mono.error(new RuntimeException("Kafka connection failed")));

            byte[] payloadBytes = VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8);
            String validSignature = calculateSignature(VALID_PAYLOAD);

            webTestClient.post()
                    .uri(WEBHOOK_ENDPOINT)
                    .header(GITHUB_EVENT_HEADER, "pull_request")
                    .header("X-Hub-Signature-256", validSignature)
                    .header("X-GitHub-Delivery", DELIVERY_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payloadBytes)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    /**
     * Calculate HMAC-SHA256 signature for webhook payload.
     * Matches GitHub's signature format: "sha256={hex}"
     */
    private String calculateSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder("sha256=");
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate signature", e);
        }
    }
}
