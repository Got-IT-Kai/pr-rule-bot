package com.code.agent.infra.github.service;

import com.code.agent.infra.github.event.GitHubReviewEvent;
import com.fasterxml.jackson.databind.JsonNode;
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

    /**
     * Check if a review comment already exists for this PR to prevent duplicate AI reviews.
     * This protects against close/reopen spam attacks.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param prNumber Pull request number
     * @return Mono<Boolean> true if review exists, false otherwise
     */
    public Mono<Boolean> hasExistingReview(String owner, String repo, int prNumber) {
        return gitHubWebClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{pull_number}/reviews", owner, repo, prNumber)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(reviews -> reviews.isArray() && !reviews.isEmpty())
                .defaultIfEmpty(false)
                .doOnNext(hasReview -> log.debug("Existing review check for {}/{} PR #{}: {}", owner, repo, prNumber, hasReview))
                .onErrorResume(error -> {
                    log.warn("Failed to check existing reviews for {}/{} PR #{}, proceeding with review", owner, repo, prNumber, error);
                    return Mono.just(false);  // On error, proceed with review to avoid blocking legitimate requests
                });
    }
}
