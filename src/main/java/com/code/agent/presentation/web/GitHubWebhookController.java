package com.code.agent.presentation.web;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.application.service.ReviewCoordinator;
import com.code.agent.infra.github.config.GitHubProperties;
import com.code.agent.infra.github.event.GitHubPullRequestEvent;
import com.code.agent.infra.github.webhook.WebhookSignatureValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static com.code.agent.infra.github.GitHubConstants.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GitHubWebhookController {
    private final ReviewCoordinator reviewCoordinator;
    private final WebhookSignatureValidator signatureValidator;
    private final GitHubProperties gitHubProperties;
    private final ObjectMapper objectMapper;

    @PostMapping(
            path = "/api/v1/webhooks/github/pull_request",
            headers = WEBHOOK_EVENT_HEADER + "=pull_request"
    )
    public Mono<ResponseEntity<Void>> handleGitHubWebhook(
            ServerHttpRequest request,
            @RequestHeader(value = WEBHOOK_SIGNATURE_HEADER, required = false) String signature) {

        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance.wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    int byteCount = dataBuffer.readableByteCount();

                    // Check for empty payload
                    if (byteCount == 0) {
                        DataBufferUtils.release(dataBuffer);
                        log.warn("Empty webhook payload received from IP: {}", request.getRemoteAddress());
                        return Mono.just(ResponseEntity.badRequest().build());
                    }

                    byte[] bytes = new byte[byteCount];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String payload = new String(bytes, StandardCharsets.UTF_8);

                    // Check for blank payload
                    if (payload.isBlank()) {
                        log.warn("Blank webhook payload received from IP: {}", request.getRemoteAddress());
                        return Mono.just(ResponseEntity.badRequest().build());
                    }

                    // Validate webhook signature
                    if (!signatureValidator.isValid(payload, signature, gitHubProperties.webhookSecret())) {
                        log.warn(LOG_INVALID_SIGNATURE, request.getRemoteAddress());
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }

                    // Parse event after validation
                    GitHubPullRequestEvent event;
                    try {
                        event = objectMapper.readValue(payload, GitHubPullRequestEvent.class);
                    } catch (JsonProcessingException e) {
                        log.error(LOG_PARSE_ERROR, e);
                        return Mono.just(ResponseEntity.badRequest().build());
                    }

                    log.info(LOG_EVENT_RECEIVED, event);
                    if (!event.isReviewTriggered()) {
                        return Mono.just(ResponseEntity.ok().build());
                    }

                    // Validate required fields
                    if (event.repository() == null || event.repository().owner() == null ||
                        event.pullRequest() == null || event.pullRequest().diffUrl() == null) {
                        log.warn("Missing required fields in webhook payload from IP: {}", request.getRemoteAddress());
                        return Mono.just(ResponseEntity.badRequest().build());
                    }

                    return reviewCoordinator.startReview(new PullRequestReviewInfo(
                            event.repository().owner().login(),
                            event.repository().name(),
                            event.number(),
                            event.pullRequest().diffUrl()))
                            .thenReturn(ResponseEntity.ok().build());
                });
    }
}
