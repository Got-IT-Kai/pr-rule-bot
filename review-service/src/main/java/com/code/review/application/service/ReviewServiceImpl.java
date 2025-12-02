package com.code.review.application.service;

import com.code.review.application.port.inbound.ReviewService;
import com.code.review.application.port.outbound.AiModelPort;
import com.code.review.application.port.outbound.EventPublisher;
import com.code.events.review.ReviewCompletedEvent;
import com.code.events.review.ReviewFailedEvent;
import com.code.events.review.ReviewStartedEvent;
import com.code.review.domain.model.PrContext;
import com.code.review.domain.model.ReviewResult;
import com.code.review.domain.model.ReviewStatus;
import com.code.review.infrastructure.config.ReactorProperties;
import com.code.platform.metrics.MetricsHelper;
import com.knuddels.jtokkit.api.Encoding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final String GIT_DIFF_PREFIX = "diff --git ";
    private static final Pattern FILE_SPLIT_PATTERN = Pattern.compile("(?m)(?=^diff --git )");
    private static final String MISSING_REVIEW_NOTICE = """

            ---
            %d files were not reviewed due to exceeding the %d-token limit.
            ---""";

    private final AiModelPort aiModelPort;
    private final EventPublisher eventPublisher;
    private final Encoding encoding;
    private final ReactorProperties reactorProperties;
    private final MetricsHelper metricsHelper;

    @Override
    public Mono<ReviewResult> perform(
            String contextId,
            String repositoryOwner,
            String repositoryName,
            Integer pullRequestNumber,
            String prTitle,
            String diff,
            String correlationId) {

        // Validate required parameters
        if (contextId == null || contextId.isBlank()) {
            return Mono.error(new IllegalArgumentException("contextId must not be blank"));
        }
        if (repositoryOwner == null || repositoryOwner.isBlank()) {
            return Mono.error(new IllegalArgumentException("repositoryOwner must not be blank"));
        }
        if (repositoryName == null || repositoryName.isBlank()) {
            return Mono.error(new IllegalArgumentException("repositoryName must not be blank"));
        }
        if (pullRequestNumber == null || pullRequestNumber <= 0) {
            return Mono.error(new IllegalArgumentException("pullRequestNumber must be positive"));
        }

        String reviewId = UUID.randomUUID().toString();
        PrContext prContext = PrContext.from(prTitle);
        Instant startTime = Instant.now();

        log.info("Starting review for PR #{} (reviewId: {}, contextId: {}, type: {}, correlationId: {})",
                pullRequestNumber, reviewId, contextId, prContext.type(), correlationId);

        return publishReviewStarted(reviewId, contextId, repositoryOwner, repositoryName,
                        pullRequestNumber, correlationId)
                .doOnSuccess(v -> metricsHelper.incrementCounter("review.started.event", "status", "success"))
                .onErrorResume(err -> {
                    log.warn("Failed to publish ReviewStartedEvent, continuing with review", err);
                    metricsHelper.incrementCounter("review.started.event", "status", "publish_failed");
                    return Mono.empty();
                })
                .then(performReview(diff, prContext))
                .map(reviewComment -> createReviewResult(
                        reviewId, contextId, repositoryOwner, repositoryName,
                        pullRequestNumber, reviewComment, ReviewStatus.COMPLETED, correlationId))
                .flatMap(result -> publishReviewCompleted(result).thenReturn(result))
                .doOnSuccess(result -> {
                    log.info("Review completed for PR #{} (reviewId: {})", pullRequestNumber, reviewId);
                    metricsHelper.incrementCounter("review.completed", "status", "success");
                    metricsHelper.recordDuration("review.processing.time",
                        Duration.between(startTime, Instant.now()), "status", "success");
                })
                .doOnError(err -> {
                    log.error("Review failed for PR #{} (reviewId: {})", pullRequestNumber, reviewId, err);
                    metricsHelper.incrementCounter("review.completed", "status", "failure",
                        "error_type", err.getClass().getSimpleName());
                    metricsHelper.recordDuration("review.processing.time",
                        Duration.between(startTime, Instant.now()), "status", "failure");
                })
                .onErrorResume(err -> handleReviewFailure(
                        reviewId, contextId, repositoryOwner, repositoryName,
                        pullRequestNumber, correlationId, err));
    }

    private Mono<String> performReview(String diff, PrContext prContext) {
        if (diff == null || diff.isBlank()) {
            log.warn("Received empty diff for review");
            return Mono.just("No changes to review.");
        }

        List<String> fileDiffs = splitDiffIntoFiles(diff);
        int maxTokens = aiModelPort.maxTokens();

        return Flux.fromIterable(fileDiffs)
                .filterWhen(chunk -> isWithinTokenLimit(chunk, maxTokens))
                .flatMap(
                        chunk -> aiModelPort.reviewCode(chunk, prContext),
                        reactorProperties.maxConcurrentReviews(),
                        reactorProperties.prefetchSize()
                )
                .collectList()
                .flatMap(reviews -> synthesizeReviews(reviews, fileDiffs.size() - reviews.size(), maxTokens, prContext));
    }

    private List<String> splitDiffIntoFiles(String diff) {
        if (!diff.startsWith(GIT_DIFF_PREFIX)) {
            return List.of(diff);
        }

        String normalizedDiff = diff.replace("\r\n", "\n").replace("\r", "\n");
        return FILE_SPLIT_PATTERN.splitAsStream(normalizedDiff)
                .filter(chunk -> !chunk.isBlank())
                .toList();
    }

    private Mono<String> synthesizeReviews(List<String> individualReviews, int missingCount, int maxTokens, PrContext prContext) {
        if (individualReviews.isEmpty()) {
            return Mono.just("All files exceed the %d-token limit".formatted(maxTokens));
        }

        String combinedReviews = String.join("\n\n--- Next Review ---\n\n", individualReviews);

        return isWithinTokenLimit(combinedReviews, maxTokens)
                .flatMap(withinLimit -> {
                    if (!withinLimit) {
                        log.warn("Combined reviews exceed the token limit of {}", maxTokens);
                        return Mono.just("Combined reviews exceed the %d-token limit".formatted(maxTokens));
                    }

                    Mono<String> merged = aiModelPort.mergeReviews(combinedReviews, prContext);
                    if (missingCount > 0) {
                        return merged.map(review -> review + MISSING_REVIEW_NOTICE.formatted(missingCount, maxTokens));
                    }

                    return merged;
                });
    }

    private Mono<Boolean> isWithinTokenLimit(String text, int maxTokens) {
        // Execute token counting on bounded elastic scheduler to avoid blocking
        return Mono.fromCallable(() -> encoding.countTokens(text))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(tokenCount -> {
                    log.debug("Token count: {} (limit: {})", tokenCount, maxTokens);
                    metricsHelper.recordValue("review.token.count", tokenCount);
                })
                .map(tokenCount -> tokenCount <= maxTokens);
    }

    private ReviewResult createReviewResult(
            String reviewId,
            String contextId,
            String repositoryOwner,
            String repositoryName,
            Integer pullRequestNumber,
            String reviewComment,
            ReviewStatus status,
            String correlationId) {

        return new ReviewResult(
                reviewId,
                contextId,
                repositoryOwner,
                repositoryName,
                pullRequestNumber,
                reviewComment,
                status,
                aiModelPort.providerName(),
                aiModelPort.modelName(),
                correlationId,
                Instant.now()
        );
    }

    private Mono<Void> publishReviewStarted(
            String reviewId,
            String contextId,
            String repositoryOwner,
            String repositoryName,
            Integer pullRequestNumber,
            String correlationId) {

        ReviewStartedEvent event = new ReviewStartedEvent(
                UUID.randomUUID().toString(),
                reviewId,
                contextId,
                repositoryOwner,
                repositoryName,
                pullRequestNumber,
                correlationId,
                Instant.now()
        );
        return eventPublisher.publish(event);
    }

    private Mono<Void> publishReviewCompleted(ReviewResult result) {
        ReviewCompletedEvent event = new ReviewCompletedEvent(
                UUID.randomUUID().toString(),
                result.reviewId(),
                result.contextId(),
                result.repositoryOwner(),
                result.repositoryName(),
                result.pullRequestNumber(),
                result.reviewComment(),
                result.aiProvider(),
                result.aiModel(),
                result.correlationId(),
                Instant.now()
        );
        return eventPublisher.publish(event);
    }

    private Mono<ReviewResult> handleReviewFailure(
            String reviewId,
            String contextId,
            String repositoryOwner,
            String repositoryName,
            Integer pullRequestNumber,
            String correlationId,
            Throwable error) {

        ReviewFailedEvent event = new ReviewFailedEvent(
                UUID.randomUUID().toString(),
                reviewId,
                contextId,
                repositoryOwner,
                repositoryName,
                pullRequestNumber,
                getErrorMessage(error),
                correlationId,
                Instant.now()
        );

        ReviewResult failedResult = new ReviewResult(
                reviewId,
                contextId,
                repositoryOwner,
                repositoryName,
                pullRequestNumber,
                "Review failed: " + getErrorMessage(error),
                ReviewStatus.FAILED,
                aiModelPort.providerName(),
                aiModelPort.modelName(),
                correlationId,
                Instant.now()
        );

        return eventPublisher.publish(event).thenReturn(failedResult);
    }

    private String getErrorMessage(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }
}
