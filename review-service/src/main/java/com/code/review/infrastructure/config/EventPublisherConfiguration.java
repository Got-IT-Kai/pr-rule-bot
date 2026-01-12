package com.code.review.infrastructure.config;

import com.code.events.review.ReviewCompletedEvent;
import com.code.events.review.ReviewFailedEvent;
import com.code.events.review.ReviewStartedEvent;
import com.code.review.application.port.outbound.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
public class EventPublisherConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher noOpEventPublisher() {
        log.info("No EventPublisher implementation found. Using NoOpEventPublisher as fallback.");

        return new EventPublisher() {
            @Override
            public Mono<Void> publish(ReviewStartedEvent event) {
                log.debug("NoOp: Skipping publish of ReviewStartedEvent - eventId={}", event.eventId());
                return Mono.empty();
            }

            @Override
            public Mono<Void> publish(ReviewCompletedEvent event) {
                log.debug("NoOp: Skipping publish of ReviewCompletedEvent - eventId={}", event.eventId());
                return Mono.empty();
            }

            @Override
            public Mono<Void> publish(ReviewFailedEvent event) {
                log.debug("NoOp: Skipping publish of ReviewFailedEvent - eventId={}", event.eventId());
                return Mono.empty();
            }
        };
    }
}