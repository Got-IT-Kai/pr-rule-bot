package com.code.webhook.application.service;

import com.code.events.webhook.PullRequestReceivedEvent;
import com.code.webhook.application.port.outbound.EventPublisher;
import com.code.webhook.application.port.outbound.SignatureValidator;
import com.code.webhook.domain.model.WebhookValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceImplTest {

    @Mock
    private SignatureValidator signatureValidator;

    @Mock
    private EventPublisher eventPublisher;

    private WebhookServiceImpl webhookService;

    private static final String WEBHOOK_SECRET = "test-secret";
    private static final String VALID_SIGNATURE = "sha256=abc123";
    private static final String DELIVERY_ID = "test-delivery-123";

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
                    "title": "Test PR",
                    "user": {
                        "login": "test-user"
                    },
                    "head": {
                        "sha": "abc123def456"
                    },
                    "html_url": "https://github.com/test/pr/123",
                    "diff_url": "https://github.com/test/pr/123.diff"
                }
            }
            """;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        webhookService = new WebhookServiceImpl(
                signatureValidator,
                eventPublisher,
                objectMapper,
                WEBHOOK_SECRET
        );
    }

    @Test
    void receive_validPayload_publishesEvent() {
        // Given
        byte[] payload = VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());
        when(eventPublisher.publish(any(PullRequestReceivedEvent.class)))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .verifyComplete();

        verify(signatureValidator).validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET);
        verify(eventPublisher).publish(any(PullRequestReceivedEvent.class));
    }

    @Test
    void receive_nullPayload_throwsException() {
        // When & Then
        StepVerifier.create(webhookService.receive(null, VALID_SIGNATURE, DELIVERY_ID))
                .expectErrorMatches(error ->
                        error instanceof WebhookServiceImpl.WebhookValidationException &&
                                error.getMessage().contains("Payload cannot be null"))
                .verify();

        verify(signatureValidator, never()).validate(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void receive_emptyPayload_throwsException() {
        // Given
        byte[] emptyPayload = new byte[0];

        // When & Then
        StepVerifier.create(webhookService.receive(emptyPayload, VALID_SIGNATURE, DELIVERY_ID))
                .expectErrorMatches(error ->
                        error instanceof WebhookServiceImpl.WebhookValidationException &&
                                error.getMessage().contains("Payload cannot be null or empty"))
                .verify();

        verify(signatureValidator, never()).validate(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void receive_invalidSignature_throwsException() {
        // Given
        byte[] payload = VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.invalid("Signature mismatch"));

        // When & Then
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .expectErrorMatches(error ->
                        error instanceof WebhookServiceImpl.WebhookValidationException &&
                                error.getMessage().contains("Invalid signature"))
                .verify();

        verify(signatureValidator).validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void receive_nullSignature_throwsException() {
        // Given
        byte[] payload = VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8);

        // When & Then
        StepVerifier.create(webhookService.receive(payload, null, DELIVERY_ID))
                .expectErrorMatches(error ->
                        error instanceof WebhookServiceImpl.WebhookValidationException &&
                                error.getMessage().contains("Signature cannot be null"))
                .verify();

        verify(signatureValidator, never()).validate(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void receive_duplicateDelivery_skipsProcessing() {
        // Given
        byte[] payload = VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());
        when(eventPublisher.publish(any(PullRequestReceivedEvent.class)))
                .thenReturn(Mono.empty());

        // First call - should succeed
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .verifyComplete();

        // When & Then: Second call with same delivery ID - should skip
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .verifyComplete();

        // Signature should be validated twice (early validation pattern)
        verify(signatureValidator, times(2)).validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET);
        // Event should only be published once due to idempotency
        verify(eventPublisher, times(1)).publish(any(PullRequestReceivedEvent.class));
    }

    @Test
    void receive_invalidJson_throwsParseException() {
        // Given
        byte[] invalidPayload = "invalid json {{{".getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(invalidPayload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());

        // When & Then
        StepVerifier.create(webhookService.receive(invalidPayload, VALID_SIGNATURE, DELIVERY_ID))
                .expectErrorMatches(error ->
                        error instanceof WebhookServiceImpl.WebhookParseException &&
                                error.getMessage().contains("Invalid webhook payload format"))
                .verify();

        verify(signatureValidator).validate(invalidPayload, VALID_SIGNATURE, WEBHOOK_SECRET);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void receive_closedAction_skipsEventPublishing() {
        // Given
        String closedPayload = """
                {
                    "action": "closed",
                    "number": 123,
                    "repository": {
                        "owner": {"login": "test-owner"},
                        "name": "test-repo"
                    },
                    "pull_request": {
                        "title": "Test PR",
                        "user": {"login": "test-user"},
                        "head": {"sha": "abc123"},
                        "html_url": "https://github.com/test/pr/123",
                        "diff_url": "https://github.com/test/pr/123.diff"
                    }
                }
                """;
        byte[] payload = closedPayload.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());

        // When & Then
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .verifyComplete();

        // Event should not be published for closed action
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void receive_synchronizeAction_publishesEvent() {
        // Given
        String synchronizePayload = """
                {
                    "action": "synchronize",
                    "number": 123,
                    "repository": {
                        "owner": {"login": "test-owner"},
                        "name": "test-repo"
                    },
                    "pull_request": {
                        "title": "Test PR",
                        "user": {"login": "test-user"},
                        "head": {"sha": "def456"},
                        "html_url": "https://github.com/test/pr/123",
                        "diff_url": "https://github.com/test/pr/123.diff"
                    }
                }
                """;
        byte[] payload = synchronizePayload.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());
        when(eventPublisher.publish(any(PullRequestReceivedEvent.class)))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .verifyComplete();

        verify(eventPublisher).publish(any(PullRequestReceivedEvent.class));
    }

    @Test
    void receive_reopenedAction_publishesEvent() {
        // Given
        String reopenedPayload = """
                {
                    "action": "reopened",
                    "number": 123,
                    "repository": {
                        "owner": {"login": "test-owner"},
                        "name": "test-repo"
                    },
                    "pull_request": {
                        "title": "Test PR",
                        "user": {"login": "test-user"},
                        "head": {"sha": "def456"},
                        "html_url": "https://github.com/test/pr/123",
                        "diff_url": "https://github.com/test/pr/123.diff"
                    }
                }
                """;
        byte[] payload = reopenedPayload.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());
        when(eventPublisher.publish(any(PullRequestReceivedEvent.class)))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .verifyComplete();

        verify(eventPublisher).publish(any(PullRequestReceivedEvent.class));
    }

    @Test
    void receive_eventPublishingFails_propagatesError() {
        // Given
        byte[] payload = VALID_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());
        RuntimeException publishError = new RuntimeException("Publishing failed");
        when(eventPublisher.publish(any(PullRequestReceivedEvent.class)))
                .thenReturn(Mono.error(publishError));

        // When & Then
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .expectError(RuntimeException.class)
                .verify();

        verify(eventPublisher).publish(any(PullRequestReceivedEvent.class));
    }

    @Test
    void receive_unknownAction_skipsProcessing() {
        // Given
        String unknownActionPayload = """
                {
                    "action": "ready_for_review",
                    "number": 123,
                    "repository": {
                        "owner": {"login": "test-owner"},
                        "name": "test-repo"
                    },
                    "pull_request": {
                        "title": "Test PR",
                        "user": {"login": "test-user"},
                        "head": {"sha": "abc123"},
                        "html_url": "https://github.com/test/pr/123",
                        "diff_url": "https://github.com/test/pr/123.diff"
                    }
                }
                """;
        byte[] payload = unknownActionPayload.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());

        // When & Then
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .verifyComplete();

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void receive_nullAction_skipsProcessing() {
        // Given
        String nullActionPayload = """
                {
                    "action": null,
                    "number": 123,
                    "repository": {
                        "owner": {"login": "test-owner"},
                        "name": "test-repo"
                    },
                    "pull_request": {
                        "title": "Test PR",
                        "user": {"login": "test-user"},
                        "head": {"sha": "abc123"},
                        "html_url": "https://github.com/test/pr/123",
                        "diff_url": "https://github.com/test/pr/123.diff"
                    }
                }
                """;
        byte[] payload = nullActionPayload.getBytes(StandardCharsets.UTF_8);
        when(signatureValidator.validate(payload, VALID_SIGNATURE, WEBHOOK_SECRET))
                .thenReturn(WebhookValidationResult.valid());

        // When & Then
        StepVerifier.create(webhookService.receive(payload, VALID_SIGNATURE, DELIVERY_ID))
                .verifyComplete();

        verify(eventPublisher, never()).publish(any());
    }
}
