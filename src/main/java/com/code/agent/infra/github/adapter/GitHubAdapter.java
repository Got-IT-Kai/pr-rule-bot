package com.code.agent.infra.github.adapter;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.application.port.out.GitHubPort;
import com.code.agent.infra.github.event.GitHubReviewEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class GitHubAdapter implements GitHubPort {

    private final WebClient gitHubWebClient;

    @Override
    public String getDiff(PullRequestReviewInfo reviewInfo) {
        return gitHubWebClient.get()
                .uri(reviewInfo.diffUrl())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Override
    public void postReviewComment(PullRequestReviewInfo reviewInfo, String comment) {
        GitHubReviewEvent gitHubReviewEvent = GitHubReviewEvent.simpleReviewEvent(comment);
        gitHubWebClient.post()
                .uri("/repos/{owner}/{repo}/pulls/{pullNumber}/reviews",
                        reviewInfo.repositoryOwner(),
                        reviewInfo.repositoryName(),
                        reviewInfo.pullRequestNumber())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(gitHubReviewEvent)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
