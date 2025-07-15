package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.event.ReviewRequestedEvent;
import com.code.agent.application.port.out.AiPort;
import com.code.agent.config.ExecutorName;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewRequestedEventListener {
    private final AiPort aiPort;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Async(ExecutorName.AI_TASK_EXECUTOR)
    @EventListener
    public void handleReviewRequestedEvent(ReviewRequestedEvent event) {
        String reviewResult = aiPort.evaluateDiff(event.diff());

        applicationEventPublisher.publishEvent(new ReviewCompletedEvent(event.reviewInfo(), reviewResult));
    }

}
