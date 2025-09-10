package com.code.agent.infra.github.service;

import com.code.agent.infra.github.event.GitHubReviewEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GitHubReviewService {

    private final WebClient gitHubWebClient;

    public GitHubReviewService(@Qualifier("gitHubWebClient") WebClient gitHubWebClient) {
        this.gitHubWebClient = gitHubWebClient;
    }

    public Mono<String> fetchUnifiedDiff(String owner, String repo, int prNumber) {
        return gitHubWebClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{pull_number}", owner, repo, prNumber)
                .header(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff")
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<Void> postReviewComment(String owner, String repo, int prNumber, String body) {
        GitHubReviewEvent payload = GitHubReviewEvent.simpleReviewEvent(body);

        return gitHubWebClient.post()
                .uri("/repos/{owner}/{repo}/pulls/{pull_number}/reviews", owner, repo, prNumber)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(resp -> log.debug("Review response: {}", resp));
    }
}
