package com.code.review.infrastructure.adapter.outbound.event;

import com.code.events.review.ReviewCompletedEvent;
import com.code.events.review.ReviewFailedEvent;
import com.code.events.review.ReviewStartedEvent;
import com.code.review.application.port.outbound.EventPublisher;
import com.code.review.infrastructure.config.KafkaTopicProperties;
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
    public Mono<Void> publish(ReviewStartedEvent event) {
        String key = event.repositoryOwner() + "/" + event.repositoryName();

        log.debug("Publishing ReviewStartedEvent to Kafka: eventId={}, reviewId={}, repo={}",
            event.eventId(), event.reviewId(), key);

        // Convert CompletableFuture to Mono and await completion
        return Mono.fromFuture(
            kafkaTemplate.send(topicProperties.reviewStarted(), key, event)
        )
        .doOnSuccess(result -> log.debug("Successfully published ReviewStartedEvent: eventId={}, reviewId={}, partition={}",
            event.eventId(), event.reviewId(), result.getRecordMetadata().partition()))
        .doOnError(error -> log.error("Failed to publish ReviewStartedEvent: eventId={}, reviewId={}, error={}",
            event.eventId(), event.reviewId(), error.getMessage()))
        .then();
    }

    @Override
    public Mono<Void> publish(ReviewCompletedEvent event) {
        String key = event.repositoryOwner() + "/" + event.repositoryName();

        log.debug("Publishing ReviewCompletedEvent to Kafka: eventId={}, reviewId={}, repo={}",
            event.eventId(), event.reviewId(), key);

        // Convert CompletableFuture to Mono and await completion
        return Mono.fromFuture(
            kafkaTemplate.send(topicProperties.reviewCompleted(), key, event)
        )
        .doOnSuccess(result -> log.debug("Successfully published ReviewCompletedEvent: eventId={}, reviewId={}, partition={}",
            event.eventId(), event.reviewId(), result.getRecordMetadata().partition()))
        .doOnError(error -> log.error("Failed to publish ReviewCompletedEvent: eventId={}, reviewId={}, error={}",
            event.eventId(), event.reviewId(), error.getMessage()))
        .then();
    }

    @Override
    public Mono<Void> publish(ReviewFailedEvent event) {
        String key = event.repositoryOwner() + "/" + event.repositoryName();

        log.debug("Publishing ReviewFailedEvent to Kafka: eventId={}, reviewId={}, repo={}",
            event.eventId(), event.reviewId(), key);

        // Convert CompletableFuture to Mono and await completion
        return Mono.fromFuture(
            kafkaTemplate.send(topicProperties.reviewFailed(), key, event)
        )
        .doOnSuccess(result -> log.debug("Successfully published ReviewFailedEvent: eventId={}, reviewId={}, partition={}",
            event.eventId(), event.reviewId(), result.getRecordMetadata().partition()))
        .doOnError(error -> log.error("Failed to publish ReviewFailedEvent: eventId={}, reviewId={}, error={}",
            event.eventId(), event.reviewId(), error.getMessage()))
        .then();
    }
}
