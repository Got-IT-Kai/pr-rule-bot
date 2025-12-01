package com.code.integration.infrastructure.adapter.outbound.event;

import com.code.events.integration.CommentPostingFailedEvent;
import com.code.integration.application.port.outbound.EventPublisher;
import com.code.integration.infrastructure.config.KafkaTopicProperties;
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
    public Mono<Void> publish(CommentPostingFailedEvent event) {
        String key = event.repositoryOwner() + "/" + event.repositoryName();

        log.debug("Publishing CommentPostingFailedEvent to Kafka: eventId={}, reviewId={}, repo={}, errorType={}",
            event.eventId(), event.reviewId(), key, event.errorType());

        return Mono.fromFuture(
            kafkaTemplate.send(topicProperties.commentFailed(), key, event)
        )
        .doOnSuccess(result -> log.debug("Successfully published CommentPostingFailedEvent: eventId={}, reviewId={}, partition={}",
            event.eventId(), event.reviewId(), result.getRecordMetadata().partition()))
        .doOnError(error -> log.error("Failed to publish CommentPostingFailedEvent: eventId={}, reviewId={}, error={}",
            event.eventId(), event.reviewId(),
            error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()))
        .then();
    }
}
