package com.code.context.infrastructure.adapter.outbound.event;

import com.code.context.application.port.outbound.EventPublisher;
import com.code.context.infrastructure.config.KafkaTopicProperties;
import com.code.events.context.ContextCollectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    @Override
    public Mono<Void> publish(ContextCollectedEvent event) {
        // Use repository as key for partitioning (maintains ordering per repository)
        String key = event.repositoryOwner() + "/" + event.repositoryName();

        log.debug("Publishing ContextCollectedEvent to Kafka: eventId={}, contextId={}, repo={}",
            event.eventId(), event.contextId(), key);

        // Convert CompletableFuture to Mono and await completion
        return Mono.fromFuture(
            kafkaTemplate.send(topicProperties.contextCollected(), key, event)
        )
        .doOnSuccess(result -> log.debug("Successfully published ContextCollectedEvent: eventId={}, contextId={}, partition={}",
            event.eventId(), event.contextId(), result.getRecordMetadata().partition()))
        .doOnError(error -> log.error("Failed to publish ContextCollectedEvent: eventId={}, contextId={}, error={}",
            event.eventId(), event.contextId(), error.getMessage()))
        .then();
    }
}
