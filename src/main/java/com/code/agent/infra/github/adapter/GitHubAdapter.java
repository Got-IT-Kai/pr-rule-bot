package com.code.agent.infra.github.adapter;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.application.port.out.GitHubPort;
import com.code.agent.infra.github.config.GitHubProperties;
import com.code.agent.infra.github.event.GitHubReviewEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@RequiredArgsConstructor
public class GitHubAdapter implements GitHubPort {

    private final WebClient gitHubWebClient;
    private final GitHubProperties gitHubProperties;
    private final Retry retryGet;
    private final Retry retryPost;

    @Override
    public Mono<String> getDiff(PullRequestReviewInfo reviewInfo) {
        return gitHubWebClient.get()
                .uri(reviewInfo.diffUrl())
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(retryGet);
    }

    @Override
    public Mono<Void> postReviewComment(PullRequestReviewInfo reviewInfo, String comment) {
        GitHubReviewEvent gitHubReviewEvent = GitHubReviewEvent.simpleReviewEvent(comment);

        return gitHubWebClient.post()
                .uri(gitHubProperties.reviewPath(),
                        reviewInfo.repositoryOwner(),
                        reviewInfo.repositoryName(),
                        reviewInfo.pullRequestNumber())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(gitHubReviewEvent)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(retryPost)
                .then();
    }


}

