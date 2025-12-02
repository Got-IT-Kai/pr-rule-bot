package com.code.integration.infrastructure.adapter.inbound.event;

import com.code.events.integration.CommentPostingFailedEvent;
import com.code.events.review.ReviewCompletedEvent;
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
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCompletedEventListener {

    private final CommentPostingService commentPostingService;
    private final EventPublisher eventPublisher;
    private final MetricsHelper metricsHelper;

    private final Cache<String, Boolean> processedEvents = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(10_000)
            .build();

    @KafkaListener(
        topics = "${kafka.topics.review-completed}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onReviewCompleted(ReviewCompletedEvent event, Acknowledgment ack) {
        log.info("Received ReviewCompleted event: eventId={}, reviewId={}, repo={}/{}, PR #{}",
            event.eventId(), event.reviewId(), event.repositoryOwner(),
            event.repositoryName(), event.pullRequestNumber());

        if (isDuplicate(event.eventId())) {
            ack.acknowledge();
            return;
        }

        ReviewComment comment = new ReviewComment(
                event.repositoryOwner(),
                event.repositoryName(),
                event.pullRequestNumber(),
                event.reviewMarkdown(),
                List.of()
        );

        log.debug("Posting review comment for PR #{}: reviewId={}", event.pullRequestNumber(), event.reviewId());

        commentPostingService.postComment(comment)
            .doOnSuccess(result -> {
                metricsHelper.incrementCounter("comment.posting", "status", "success", "type", "review_completed");
                log.info("Successfully posted comment for review: reviewId={}, PR #{}",
                    event.reviewId(), event.pullRequestNumber());
            })
            .onErrorResume(error -> {
                ErrorType errorType = ErrorType.from(error);
                metricsHelper.incrementCounter("comment.posting", "status", "failure", "type", errorType.name());
                log.error("Failed to post comment for review: reviewId={}, PR #{}, errorType={}",
                    event.reviewId(), event.pullRequestNumber(), errorType, error);

                return publishFailedEvent(event, error, errorType.name())
                    .doOnError(publishErr -> {
                        log.error("Failed to publish CommentPostingFailedEvent: reviewId={}, PR #{}",
                            event.reviewId(), event.pullRequestNumber(), publishErr);
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

    private reactor.core.publisher.Mono<Void> publishFailedEvent(ReviewCompletedEvent event, Throwable error, String errorType) {
        CommentPostingFailedEvent failedEvent = new CommentPostingFailedEvent(
                UUID.randomUUID().toString(),
                event.reviewId(),
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
