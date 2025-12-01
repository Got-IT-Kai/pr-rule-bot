package com.code.webhook.infrastructure.adapter.inbound.rest;

import com.code.webhook.application.port.inbound.WebhookService;
import com.code.webhook.application.service.WebhookServiceImpl.WebhookParseException;
import com.code.webhook.application.service.WebhookServiceImpl.WebhookValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private static final String WEBHOOK_SIGNATURE_HEADER = "X-Hub-Signature-256";
    private static final String WEBHOOK_EVENT_HEADER = "X-GitHub-Event";
    private static final String WEBHOOK_DELIVERY_HEADER = "X-GitHub-Delivery";

    private final WebhookService webhookService;

    @PostMapping(
            path = "/api/v1/webhooks/github/pull_request",
            headers = WEBHOOK_EVENT_HEADER + "=pull_request"
    )
    public Mono<ResponseEntity<Void>> handleGitHubWebhook(
            @RequestBody Mono<byte[]> body,
            @RequestHeader(value = WEBHOOK_SIGNATURE_HEADER, required = false) String signature,
            @RequestHeader(value = WEBHOOK_DELIVERY_HEADER, required = false) String deliveryId) {

        log.debug("Received webhook with delivery ID: {}", deliveryId);

        return body.switchIfEmpty(Mono.error(new IllegalArgumentException("Empty body")))
                .flatMap(payload -> webhookService.receive(payload, signature, deliveryId)
                        .thenReturn(ResponseEntity.accepted().<Void>build()))
                .switchIfEmpty(Mono.just(ResponseEntity.accepted().build()))
                .onErrorResume(IllegalArgumentException.class, ex -> {
                    log.warn("Bad request: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(WebhookValidationException.class, ex -> {
                    log.warn("Webhook validation failed: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                })
                .onErrorResume(WebhookParseException.class, ex -> {
                    log.warn("Webhook parse failed: {}", ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                })
                .onErrorResume(ex -> {
                    log.error("Unexpected error processing webhook", ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
