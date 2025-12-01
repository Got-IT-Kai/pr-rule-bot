package com.code.context.application.port.outbound;

import com.code.events.context.ContextCollectedEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {

    Mono<Void> publish(ContextCollectedEvent event);
}
