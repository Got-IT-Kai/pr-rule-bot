package com.code.agent.application.service;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.application.event.ReviewRequestedEvent;
import com.code.agent.application.port.out.GitHubPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewCoordinator {
    private final GitHubPort gitHubPort;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void startReview(PullRequestReviewInfo command) {
        String diff = gitHubPort.getDiff(command);
        applicationEventPublisher.publishEvent(new ReviewRequestedEvent(command, diff));
    }

}
