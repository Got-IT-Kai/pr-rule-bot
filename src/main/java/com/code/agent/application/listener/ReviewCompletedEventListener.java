package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.port.out.GitHubPort;
import com.code.agent.config.ExecutorName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCompletedEventListener {
    private final GitHubPort gitHubPort;

    @Async(ExecutorName.REVIEW_TASK_EXECUTOR)
    @EventListener
    public void handleReviewCompletedEvent(ReviewCompletedEvent event) {
        Mono<Void> postTask = gitHubPort.postReviewComment(event.reviewInfo(), event.reviewResult());
        postTask.subscribe(
                null,
                error -> log.error("Post review comment failed for pull request",error),
                () -> log.info("Post review comment completed for pull request {}", event.reviewInfo().pullRequestNumber())
        );
    }
}
