package com.code.context.infrastructure.adapter.inbound.event;

import com.code.context.application.port.inbound.ContextCollectionService;
import com.code.events.webhook.PullRequestReceivedEvent;
import com.code.platform.metrics.MetricsHelper;
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
public class PullRequestEventListener {

    private final ContextCollectionService contextCollectionService;
    private final MetricsHelper metricsHelper;

    // Bounded cache with TTL to prevent memory leak
    private final Cache<String, Boolean> processedEvents = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build();

    @KafkaListener(
        topics = "${kafka.topics.pull-request-received}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPullRequestReceived(PullRequestReceivedEvent event, Acknowledgment ack) {
        log.info("Received PullRequestReceived event: eventId={}, repo={}/{}, PR #{}, platform={}, installationId={}",
            event.eventId(), event.repositoryOwner(), event.repositoryName(), event.pullRequestNumber(),
            event.platform(), event.installationId());

        // Check for duplicate event
        if (isDuplicate(event.eventId())) {
            ack.acknowledge();
            return;
        }

        // Only process events that trigger review
        if (!event.triggersReview()) {
            log.debug("Event does not trigger review, skipping: action={}", event.action());
            ack.acknowledge();
            return;
        }

        // Construct diff URL for GitHub API
        String diffUrl = String.format("/repos/%s/%s/pulls/%d",
            event.repositoryOwner(), event.repositoryName(), event.pullRequestNumber());

        log.debug("Starting context collection for PR #{}: diffUrl={}", event.pullRequestNumber(), diffUrl);

        contextCollectionService.collect(
                event.repositoryOwner(),
                event.repositoryName(),
                event.pullRequestNumber(),
                event.title(),
                diffUrl,
                event.correlationId()
        ).subscribe(
                context -> {
                    metricsHelper.incrementCounter("context.collection", "status", "success");
                    log.info("Context collection completed for PR #{}", event.pullRequestNumber());
                    ack.acknowledge();
                },
                error -> {
                    metricsHelper.incrementCounter("context.collection", "status", "failure");
                    log.error("Context collection failed for PR #{}", event.pullRequestNumber(), error);
                    ack.acknowledge();
                }
        );
    }

    private boolean isDuplicate(String eventId) {
        if (eventId == null) {
            return false;
        }

        // Atomic check-and-insert: putIfAbsent returns null if key was absent (first time)
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
