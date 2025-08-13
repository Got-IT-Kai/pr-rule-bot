package com.code.agent.application.port.out;

import reactor.core.publisher.Mono;

public interface EventBusPort {
    Mono<Void> publishEvent(Object event);
}
