package com.code.integration.application.port.outbound;

import com.code.events.integration.CommentPostingFailedEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {

    Mono<Void> publish(CommentPostingFailedEvent event);
}
