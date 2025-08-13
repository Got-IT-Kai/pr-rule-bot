package com.code.agent.application.service;

import com.code.agent.application.event.ReviewFailedEvent;
import com.code.agent.application.port.out.EventBusPort;
import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.application.event.ReviewRequestedEvent;
import com.code.agent.application.port.out.GitHubPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCoordinator {
    private final GitHubPort gitHubPort;
    private final EventBusPort eventBusPort;

    public Mono<Void> startReview(PullRequestReviewInfo info) {
        return gitHubPort.getDiff(info)
                .flatMap(diff -> {
                    log.debug("Pull request {} diff fetched successfully", info.pullRequestNumber());
                    return eventBusPort.publishEvent(new ReviewRequestedEvent(info, diff));
                })
                .doOnError(error -> {
                    log.error("Failed to fetch diff for pull request {}", info.pullRequestNumber(), error);
                })
                .onErrorResume(error ->
                        eventBusPort.publishEvent(new ReviewFailedEvent(info, error.getMessage()))
                                .then());
    }

}
