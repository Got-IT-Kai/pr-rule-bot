package com.code.webhook.application.service;

import com.code.events.webhook.PullRequestReceivedEvent;
import com.code.events.webhook.WebhookAction;
import com.code.webhook.application.port.inbound.WebhookService;
import com.code.webhook.application.port.outbound.EventPublisher;
import com.code.webhook.application.port.outbound.SignatureValidator;
import com.code.webhook.domain.model.WebhookValidationResult;
import com.code.webhook.infrastructure.adapter.inbound.rest.dto.GitHubPullRequestEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.code.platform.correlation.CorrelationId;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final SignatureValidator signatureValidator;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;
    private final Cache<String, Boolean> processedDeliveries = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(24))
        .maximumSize(10_000)
        .build();

    @Override
    public Mono<Void> receive(byte[] payload, String signature, String deliveryId) {
        if (payload == null || payload.length == 0) {
            return Mono.error(new WebhookValidationException("Payload cannot be null or empty"));
        }

        if (signature == null || signature.isBlank()) {
            return Mono.error(new WebhookValidationException("Signature cannot be null or empty"));
        }

        WebhookValidationResult validationResult = signatureValidator.validate(payload, signature, webhookSecret);
        if (!validationResult.isValid()) {
            log.warn("Webhook signature validation failed: {}", validationResult.failureReason());
            return Mono.error(new WebhookValidationException("Invalid signature: " + validationResult.failureReason()));
        }

        GitHubPullRequestEventDto dto;
        try {
            dto = parseWebhook(payload);
        } catch (WebhookParseException e) {
            log.error("Failed to parse webhook payload", e);
            return Mono.error(e);
        }

        WebhookAction action = mapAction(dto.action());
        if (action == null) {
            log.info("Skipping unsupported action: {} for PR #{}",
                     dto.action(), dto.number());
            return Mono.empty();
        }

        PullRequestReceivedEvent event = mapToEvent(dto, action);

        if (!event.triggersReview()) {
            log.debug("PR action does not trigger review, skipping");
            return Mono.empty();
        }

        return checkIdempotency(deliveryId)
                .flatMap(ok -> {
                    log.info("Publishing PullRequestReceivedEvent: {}", event);
                    return eventPublisher.publish(event);
                })
                .doOnError(error -> {
                    log.error("Webhook processing failed", error);
                    if (deliveryId != null) {
                        processedDeliveries.invalidate(deliveryId);
                    }
                });
    }

    private Mono<Boolean> checkIdempotency(String deliveryId) {
        if (deliveryId == null) {
            return Mono.just(true);
        }

        boolean isFirstDelivery = processedDeliveries.asMap().putIfAbsent(deliveryId, Boolean.TRUE) == null;
        if (!isFirstDelivery) {
            log.info("Duplicate delivery detected: {}, skipping", deliveryId);
            return Mono.empty();
        }

        return Mono.just(true);
    }

    private GitHubPullRequestEventDto parseWebhook(byte[] payload) {
        try {
            return objectMapper.readValue(payload, GitHubPullRequestEventDto.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            throw new WebhookParseException("Invalid webhook payload format", e);
        }
    }

    private PullRequestReceivedEvent mapToEvent(GitHubPullRequestEventDto dto, WebhookAction action) {
        if (dto.repository() == null || dto.repository().owner() == null) {
            throw new WebhookParseException("Missing repository information", null);
        }
        if (dto.pullRequest() == null || dto.pullRequest().head() == null) {
            throw new WebhookParseException("Missing pull request information", null);
        }
        if (dto.number() == null || dto.number() <= 0) {
            throw new WebhookParseException("Missing or invalid pull request number", null);
        }

        String owner = Optional.ofNullable(dto.repository().owner().login())
                .orElseThrow(() -> new WebhookParseException("Missing repository owner login", null));
        String repoName = Optional.ofNullable(dto.repository().name())
                .orElseThrow(() -> new WebhookParseException("Missing repository name", null));
        String title = Optional.ofNullable(dto.pullRequest().title())
                .orElse("");
        String sha = Optional.ofNullable(dto.pullRequest().head().sha())
                .orElseThrow(() -> new WebhookParseException("Missing commit SHA", null));

        String installationId = dto.installation() != null && dto.installation().id() != null
                ? String.valueOf(dto.installation().id())
                : "";

        return new PullRequestReceivedEvent(
                UUID.randomUUID().toString(),
                owner,
                repoName,
                dto.number(),
                action,
                title,
                dto.pullRequest().user() != null ? dto.pullRequest().user().login() : "unknown",
                sha,
                Instant.now(),
                CorrelationId.generate(),
                "github",
                installationId
        );
    }

    private WebhookAction mapAction(String action) {
        if (action == null || action.isBlank()) {
            log.debug("Null or blank action received, skipping");
            return null;
        }

        return switch (action.toLowerCase(Locale.ROOT)) {
            case "opened" -> WebhookAction.OPENED;
            case "synchronize" -> WebhookAction.SYNCHRONIZE;
            case "reopened" -> WebhookAction.REOPENED;
            case "closed" -> WebhookAction.CLOSED;
            case "edited" -> WebhookAction.EDITED;
            default -> {
                log.debug("Unsupported action received: {}, skipping", action);
                yield null;
            }
        };
    }

    public static class WebhookValidationException extends RuntimeException {
        public WebhookValidationException(String message) {
            super(message);
        }
    }

    public static class WebhookParseException extends RuntimeException {
        public WebhookParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
