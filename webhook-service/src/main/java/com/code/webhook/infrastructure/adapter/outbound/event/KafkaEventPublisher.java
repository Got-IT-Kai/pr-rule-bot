package com.code.webhook.infrastructure.adapter.outbound.event;

import com.code.events.webhook.PullRequestReceivedEvent;
import com.code.webhook.application.port.outbound.EventPublisher;
import com.code.webhook.infrastructure.config.KafkaTopicProperties;
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
    public Mono<Void> publish(PullRequestReceivedEvent event) {
        // Use repository as key for partitioning (maintains ordering per repository)
        String key = event.repositoryOwner() + "/" + event.repositoryName();

        log.debug("Publishing PullRequestReceivedEvent to Kafka: eventId={}, repo={}",
            event.eventId(), key);

        // Convert CompletableFuture to Mono and await completion
        return Mono.fromFuture(
            kafkaTemplate.send(topicProperties.pullRequestReceived(), key, event)
        )
        .doOnSuccess(result -> log.debug("Successfully published PullRequestReceivedEvent: eventId={}, partition={}",
            event.eventId(), result.getRecordMetadata().partition()))
        .doOnError(error -> log.error("Failed to publish PullRequestReceivedEvent: eventId={}, error={}",
            event.eventId(), error.getMessage()))
        .then();
    }
}
