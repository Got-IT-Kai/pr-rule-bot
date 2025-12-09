package com.code.review.infrastructure.adapter.inbound.event;

import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.code.platform.dlt.DltPublisher;
import com.code.platform.idempotency.IdempotencyStore;
import com.code.review.application.port.inbound.ReviewService;
import com.code.review.infrastructure.config.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextCollectedEventListener {

    private final ReviewService reviewService;
    private final IdempotencyStore idempotencyStore;
    private final DltPublisher dltPublisher;
    private final KafkaTopicProperties topicProperties;

    @KafkaListener(
        topics = "${kafka.topics.context-collected}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onContextCollected(ContextCollectedEvent event, Acknowledgment ack) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", event.correlationId())) {
            log.info("Received ContextCollected event: eventId={}, contextId={}, repo={}/{}, PR #{}",
                event.eventId(), event.contextId(), event.repositoryOwner(),
                event.repositoryName(), event.pullRequestNumber());

            if (!idempotencyStore.tryStart(event.eventId())) {
                log.info("Duplicate event detected: {}, skipping", event.eventId());
                ack.acknowledge();
                return;
            }

            if (event.status() != ContextCollectionStatus.COMPLETED) {
                log.debug("Context collection not completed, skipping review: status={}", event.status());
                ack.acknowledge();
                return;
            }

            if (event.diff() == null || event.diff().isBlank()) {
                log.warn("Context collected but diff is empty, skipping review: contextId={}", event.contextId());
                ack.acknowledge();
                return;
            }

            log.debug("Starting code review for PR #{}: contextId={}", event.pullRequestNumber(), event.contextId());

            reviewService.perform(
                    event.contextId(),
                    event.repositoryOwner(),
                    event.repositoryName(),
                    event.pullRequestNumber(),
                    event.title(),
                    event.diff(),
                    event.correlationId()
            ).subscribe(
                    result -> {
                        if (result.isSuccessful()) {
                            log.info("Review completed for PR #{} (status={})", event.pullRequestNumber(), result.status());
                            idempotencyStore.markProcessed(event.eventId());
                            ack.acknowledge();
                        } else {
                            log.info("Review finished with failure for PR #{} (status={})", event.pullRequestNumber(), result.status());
                            String dltTopic = topicProperties.contextCollected() + ".dlt";
                            dltPublisher.forwardToDlt(dltTopic, event.eventId(), event, ack);
                        }
                    },
                    error -> {
                        log.error("Review failed for PR #{}", event.pullRequestNumber(), error);
                        String dltTopic = topicProperties.contextCollected() + ".dlt";
                        dltPublisher.forwardToDlt(dltTopic, event.eventId(), event, ack);
                    }
                );
        }
    }
}
