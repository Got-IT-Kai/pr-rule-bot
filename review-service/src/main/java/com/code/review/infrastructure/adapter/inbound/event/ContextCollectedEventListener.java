package com.code.review.infrastructure.adapter.inbound.event;

import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.code.platform.metrics.MetricsHelper;
import com.code.review.application.port.inbound.ReviewService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextCollectedEventListener {

    private final ReviewService reviewService;
    private final MetricsHelper metricsHelper;

    // Bounded cache with TTL to prevent memory leak
    private final Cache<String, Boolean> processedEvents = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build();

    @KafkaListener(
        topics = "${kafka.topics.context-collected}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onContextCollected(ContextCollectedEvent event, Acknowledgment ack) {
        log.info("Received ContextCollected event: eventId={}, contextId={}, repo={}/{}, PR #{}",
            event.eventId(), event.contextId(), event.repositoryOwner(),
            event.repositoryName(), event.pullRequestNumber());

        // Check for duplicate event
        if (isDuplicate(event.eventId())) {
            ack.acknowledge();
            return;
        }

        // Only process successfully collected contexts
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
                        metricsHelper.incrementCounter("review.execution", "status", "success");
                        log.info("Review completed for PR #{} (status={})", event.pullRequestNumber(), result.status());
                    } else {
                        metricsHelper.incrementCounter("review.execution", "status", "failure");
                        log.info("Review finished with failure for PR #{} (status={})", event.pullRequestNumber(), result.status());
                    }
                    ack.acknowledge();
                },
                error -> {
                    metricsHelper.incrementCounter("review.execution", "status", "failure");
                    log.error("Review failed for PR #{}", event.pullRequestNumber(), error);
                    ack.acknowledge();
                }
        );
    }

    private boolean isDuplicate(String eventId) {
        if (eventId == null) {
            return false;
        }

        Boolean previous = processedEvents.asMap().putIfAbsent(eventId, Boolean.TRUE);
        if (previous != null) {
            metricsHelper.incrementCounter("event.idempotency", "result", "duplicate");
            log.info("Duplicate event detected: {}, skipping", eventId);
            return true;
        }
        metricsHelper.incrementCounter("event.idempotency", "result", "new");
        return false;
    }
}