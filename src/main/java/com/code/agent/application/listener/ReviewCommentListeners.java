package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.event.ReviewFailedEvent;
import com.code.agent.application.port.out.GitHubPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Event listener for handling code review completion and failure events.
 * Automatically posts review comments to GitHub when reviews are completed or failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCommentListeners {

    private final GitHubPort gitHubPort;

    /**
     * Handles review completion events by posting the review result to GitHub.
     *
     * @param event the review completed event containing review information and results
     * @return Mono<Void> that completes when the comment is posted
     */
    @EventListener
    public Mono<Void> onReviewCompleted(ReviewCompletedEvent event) {
        return gitHubPort.postReviewComment(event.reviewInfo(), event.reviewResult())
                .doOnSuccess(aVoid ->
                        log.info("Review comment completed for pull request {}", event.reviewInfo().pullRequestNumber()))
                .doOnError(error ->
                        log.error("Comment post failed for PR {}", event.reviewInfo().pullRequestNumber(), error));

    }

    /**
     * Handles review failure events by posting an error message to GitHub.
     *
     * @param event the review failed event containing error information
     * @return Mono<Void> that completes when the error comment is posted
     */
    @EventListener
    public Mono<Void> onReviewFailed(ReviewFailedEvent event) {
        String errorMessage = "Review failed: " + event.message();
        return gitHubPort.postReviewComment(event.reviewInfo(), errorMessage)
                .doOnSuccess(aVoid ->
                        log.info("Fail comment completed for pull request {}", event.reviewInfo().pullRequestNumber()))
                .doOnError(error ->
                        log.error("Comment post failed {}", event.reviewInfo().pullRequestNumber(), error));
    }

}
