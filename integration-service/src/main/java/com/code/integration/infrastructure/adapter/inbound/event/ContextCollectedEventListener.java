package com.code.integration.infrastructure.adapter.inbound.event;

import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.code.events.integration.CommentPostingFailedEvent;
import com.code.integration.application.port.inbound.CommentPostingService;
import com.code.integration.application.port.outbound.EventPublisher;
import com.code.integration.domain.model.ErrorType;
import com.code.integration.domain.model.ReviewComment;
import com.code.platform.metrics.MetricsHelper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextCollectedEventListener {

    private final CommentPostingService commentPostingService;
    private final EventPublisher eventPublisher;
    private final MetricsHelper metricsHelper;

    private final Cache<String, Boolean> processedEvents = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build();

    @KafkaListener(
        topics = "${kafka.topics.context-collected}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onContextCollected(ContextCollectedEvent event, Acknowledgment ack) {
        log.info("Received ContextCollected event: eventId={}, contextId={}, repo={}/{}, PR #{}, status={}",
            event.eventId(), event.contextId(), event.repositoryOwner(),
            event.repositoryName(), event.pullRequestNumber(), event.status());

        if (event.status() == ContextCollectionStatus.COMPLETED) {
            log.debug("Context collection completed, skipping (review-service will handle)");
            ack.acknowledge();
            return;
        }

        if (isDuplicate(event.eventId())) {
            ack.acknowledge();
            return;
        }

        String comment = buildStatusComment(event);
        ReviewComment reviewComment = new ReviewComment(
                event.repositoryOwner(),
                event.repositoryName(),
                event.pullRequestNumber(),
                comment,
                List.of()
        );

        log.debug("Posting {} notification for PR #{}: contextId={}",
            event.status(), event.pullRequestNumber(), event.contextId());

        commentPostingService.postComment(reviewComment)
            .doOnSuccess(result -> {
                metricsHelper.incrementCounter("comment.posting",
                    "status", "success",
                    "type", "context_" + event.status().name().toLowerCase());
                log.info("Successfully posted {} notification for PR #{}",
                    event.status(), event.pullRequestNumber());
            })
            .onErrorResume(error -> {
                ErrorType errorType = ErrorType.from(error);
                metricsHelper.incrementCounter("comment.posting",
                    "status", "failure",
                    "type", errorType.name());

                return publishFailedEvent(event, error, errorType.name())
                    .doOnError(publishErr -> {
                        log.error("Failed to publish CommentPostingFailedEvent: contextId={}, PR #{}",
                            event.contextId(), event.pullRequestNumber(), publishErr);
                        metricsHelper.incrementCounter("comment.posting.event", "status", "publish_failed");
                    })
                    .onErrorResume(pubErr -> Mono.empty());
            })
            .then()
            .doFinally(signal -> ack.acknowledge())
            .subscribe();
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

    private String buildStatusComment(ContextCollectedEvent event) {
        return switch (event.status()) {
            case FAILED -> String.format("""
                ## Code Review Context Collection Failed

                The automated code review could not be started due to a failure in collecting the PR context.

                **Repository:** %s/%s
                **Pull Request:** #%d

                Please check the logs or contact the development team for assistance.

                *Correlation ID: %s*
                """,
                event.repositoryOwner(),
                event.repositoryName(),
                event.pullRequestNumber(),
                event.correlationId());

            case SKIPPED -> String.format("""
                ## Code Review Skipped

                The automated code review was skipped for this pull request.

                **Repository:** %s/%s
                **Pull Request:** #%d

                *This may occur when the diff is empty, too large, or consists only of non-reviewable changes.*

                *Correlation ID: %s*
                """,
                event.repositoryOwner(),
                event.repositoryName(),
                event.pullRequestNumber(),
                event.correlationId());

            default -> String.format("""
                ## Unexpected Status

                An unexpected status was encountered during context collection: %s

                *Correlation ID: %s*
                """,
                event.status(),
                event.correlationId());
        };
    }

    private Mono<Void> publishFailedEvent(ContextCollectedEvent event, Throwable error, String errorType) {
        CommentPostingFailedEvent failedEvent = new CommentPostingFailedEvent(
                UUID.randomUUID().toString(),
                event.contextId(),
                event.repositoryOwner(),
                event.repositoryName(),
                event.pullRequestNumber(),
                error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName(),
                errorType,
                event.correlationId(),
                Instant.now()
        );

        return eventPublisher.publish(failedEvent);
    }
}
