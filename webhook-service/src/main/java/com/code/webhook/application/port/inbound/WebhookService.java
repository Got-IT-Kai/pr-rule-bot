package com.code.webhook.application.port.inbound;

import reactor.core.publisher.Mono;

public interface WebhookService {

    Mono<Void> receive(byte[] payload, String signature, String deliveryId);
}
