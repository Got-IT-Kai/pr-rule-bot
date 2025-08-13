package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.event.ReviewFailedEvent;
import com.code.agent.application.port.out.GitHubPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCommentListeners {

    private final GitHubPort gitHubPort;

    @EventListener
    public Mono<Void> onReviewCompleted(ReviewCompletedEvent event) {
        return gitHubPort.postReviewComment(event.reviewInfo(), event.reviewResult())
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(aVoid ->
                        log.info("Review comment completed for pull request {}", event.reviewInfo().pullRequestNumber()))
                .doOnError(error ->
                        log.info("Comment post failed {}", event.reviewInfo().pullRequestNumber(), error));

    }

    @EventListener
    public Mono<Void> onReviewFailed(ReviewFailedEvent event) {
        String errorMessage = "Review failed: " + event.message();
        return gitHubPort.postReviewComment(event.reviewInfo(), errorMessage)
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(aVoid ->
                        log.info("Fail comment completed for pull request {}", event.reviewInfo().pullRequestNumber()))
                .doOnError(error ->
                        log.error("Comment post failed {}", event.reviewInfo().pullRequestNumber(), error));
    }

}
