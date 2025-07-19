package com.code.agent.application.service;

import com.code.agent.application.event.ReviewFailedEvent;
import com.code.agent.application.port.out.EventBusPort;
import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.application.event.ReviewRequestedEvent;
import com.code.agent.application.port.out.GitHubPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCoordinator {
    private final GitHubPort gitHubPort;
    private final EventBusPort eventBusPort;

    public void startReview(PullRequestReviewInfo command) {
        gitHubPort.getDiff(command)
                .doOnSuccess(diff -> eventBusPort.publishEvent(new ReviewRequestedEvent(command, diff)))
                .doOnError(error -> {
                    log.error("Failed to fetch diff for pull request {}: {}", command.pullRequestNumber(), error.getMessage());
                    eventBusPort.publishEvent(new ReviewFailedEvent(command, error.getMessage()));
                }).subscribe();
    }

}
