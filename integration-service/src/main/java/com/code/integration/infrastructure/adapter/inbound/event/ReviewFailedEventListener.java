package com.code.integration.infrastructure.adapter.inbound.event;

import com.code.events.integration.CommentPostingFailedEvent;
import com.code.events.review.ReviewFailedEvent;
import com.code.integration.application.port.inbound.CommentPostingService;
import com.code.integration.application.port.outbound.EventPublisher;
import com.code.integration.domain.model.ErrorType;
import com.code.integration.domain.model.ReviewComment;
import com.code.integration.infrastructure.config.KafkaTopicProperties;
import com.code.integration.infrastructure.support.ReactiveRetrySupport;
import com.code.platform.dlt.DltPublisher;
import com.code.platform.idempotency.IdempotencyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewFailedEventListener {

    private final CommentPostingService commentPostingService;
    private final EventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final DltPublisher dltPublisher;
    private final KafkaTopicProperties topicProperties;
    private final ReactiveRetrySupport retrySupport;

    @KafkaListener(
        topics = "${kafka.topics.review-failed}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onReviewFailed(ReviewFailedEvent event, Acknowledgment ack) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", event.correlationId())) {
            log.info("Received ReviewFailed event: eventId={}, reviewId={}, repo={}/{}, PR #{}",
                event.eventId(), event.reviewId(), event.repositoryOwner(),
                event.repositoryName(), event.pullRequestNumber());

            if (!idempotencyStore.tryStart(event.eventId())) {
                log.info("Duplicate event detected: {}, skipping", event.eventId());
                ack.acknowledge();
                return;
            }

            String failureComment = buildFailureComment(event);
            ReviewComment comment = new ReviewComment(
                    event.repositoryOwner(),
                    event.repositoryName(),
                    event.pullRequestNumber(),
                    failureComment,
                    List.of()
            );

            log.debug("Posting failure comment for PR #{}: reviewId={}", event.pullRequestNumber(), event.reviewId());

            commentPostingService.postComment(comment)
                .retryWhen(retrySupport.transientRetry(3, java.time.Duration.ofMillis(100)))
                .doOnSuccess(result ->
                    log.info("Successfully posted failure comment for PR #{}", event.pullRequestNumber())
                )
                .onErrorResume(error -> {
                    Throwable cause = retrySupport.unwrap(error);
                    ErrorType errorType = ErrorType.from(cause);

                    return publishFailedEvent(event, cause, errorType.name())
                        .doOnError(publishErr ->
                            log.error("Failed to publish CommentPostingFailedEvent: reviewId={}, PR #{}",
                                event.reviewId(), event.pullRequestNumber(), publishErr)
                        )
                        .onErrorResume(pubErr -> {
                            log.error("Both comment posting and failure event publish failed, forwarding to DLT", pubErr);
                            return Mono.error(pubErr);
                        });
                })
                .then()
                .doOnSuccess(v -> {
                    idempotencyStore.markProcessed(event.eventId());
                    ack.acknowledge();
                })
                .doOnError(err -> {
                    String dltTopic = topicProperties.reviewFailed() + ".dlt";
                    dltPublisher.forwardToDlt(dltTopic, event.eventId(), event, ack);
                })
                .subscribe(
                        unused -> {},
                        error -> log.error("Unexpected error in ReviewFailed event handling: {}",
                                error.getMessage(), error)
                );
        }
    }

    private String buildFailureComment(ReviewFailedEvent event) {
        return String.format("""
            ## Code Review Failed

            Unfortunately, the automated code review could not be completed due to an error.

            **Error:** %s

            Please check the logs or contact the development team for assistance.

            *Correlation ID: %s*
            """, event.errorMessage(), event.correlationId());
    }

    private Mono<Void> publishFailedEvent(ReviewFailedEvent event, Throwable error, String errorType) {
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
