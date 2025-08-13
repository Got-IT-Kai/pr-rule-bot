package com.code.agent.presentation.web;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.application.service.ReviewCoordinator;
import com.code.agent.infra.github.event.GitHubPullRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GitHubWebhookController {
    private final ReviewCoordinator reviewCoordinator;

    @PostMapping(
            path = "/api/v1/webhooks/github/pull_request",
            headers = "X-GitHub-Event=pull_request"
    )
    public Mono<ResponseEntity<Void>> handleGitHubWebhook(@RequestBody GitHubPullRequestEvent event) {
        log.info("Received GitHub pull request event: {}", event);
        if (!event.isReviewTriggered()) {
            return Mono.just(ResponseEntity.ok().build());
        }

        return reviewCoordinator.startReview(new PullRequestReviewInfo(event.repository().owner().login(),
                event.repository().name(),
                event.number(),
                event.pullRequest().diffUrl()))
                .thenReturn(ResponseEntity.ok().build());
    }
}
