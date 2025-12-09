package com.code.integration.infrastructure.adapter.inbound.event;

import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.code.events.integration.CommentPostingFailedEvent;
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
public class ContextCollectedEventListener {

    private final CommentPostingService commentPostingService;
    private final EventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final DltPublisher dltPublisher;
    private final KafkaTopicProperties topicProperties;
    private final ReactiveRetrySupport retrySupport;

    @KafkaListener(
        topics = "${kafka.topics.context-collected}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onContextCollected(ContextCollectedEvent event, Acknowledgment ack) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", event.correlationId())) {
            log.info("Received ContextCollected event: eventId={}, contextId={}, repo={}/{}, PR #{}, status={}",
                event.eventId(), event.contextId(), event.repositoryOwner(),
                event.repositoryName(), event.pullRequestNumber(), event.status());

            if (event.status() == ContextCollectionStatus.COMPLETED) {
                log.debug("Context collection completed, skipping (review-service will handle)");
                ack.acknowledge();
                return;
            }

            if (!idempotencyStore.tryStart(event.eventId())) {
                log.info("Duplicate event detected: {}, skipping", event.eventId());
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
                .retryWhen(retrySupport.transientRetry(3, java.time.Duration.ofMillis(100)))
                .doOnSuccess(result ->
                    log.info("Successfully posted {} notification for PR #{}",
                        event.status(), event.pullRequestNumber())
                )
                .onErrorResume(error -> {
                    Throwable cause = retrySupport.unwrap(error);
                    ErrorType errorType = ErrorType.from(cause);

                    return publishFailedEvent(event, cause, errorType.name())
                        .doOnError(publishErr ->
                            log.error("Failed to publish CommentPostingFailedEvent: contextId={}, PR #{}",
                                event.contextId(), event.pullRequestNumber(), publishErr)
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
                    String dltTopic = topicProperties.contextCollected() + ".dlt";
                    dltPublisher.forwardToDlt(dltTopic, event.eventId(), event, ack);
                })
                .subscribe(
                        unused -> {},
                        error -> log.error("Unexpected error in ContextCollected event handling: {}",
                                error.getMessage(), error)
                );
        }
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
