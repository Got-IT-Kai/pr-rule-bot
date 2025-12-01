package com.code.webhook.application.port.outbound;

import com.code.events.webhook.PullRequestReceivedEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {

    Mono<Void> publish(PullRequestReceivedEvent event);
}
