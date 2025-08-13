package com.code.agent.infra.eventbus.adapter;

import com.code.agent.application.port.out.EventBusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SpringEventBusAdapter implements EventBusPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public Mono<Void> publishEvent(Object event) {
        return Mono.fromRunnable(() -> applicationEventPublisher.publishEvent(event));
    }
}
