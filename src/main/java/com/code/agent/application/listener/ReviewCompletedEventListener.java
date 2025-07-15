package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.port.out.GitHubPort;
import com.code.agent.config.ExecutorName;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewCompletedEventListener {
    private final GitHubPort gitHubPort;

    @Async(ExecutorName.REVIEW_TASK_EXECUTOR)
    @EventListener
    public void handleReviewCompletedEvent(ReviewCompletedEvent event) {
        gitHubPort.postReviewComment(event.reviewInfo(), event.reviewResult());
    }
}
