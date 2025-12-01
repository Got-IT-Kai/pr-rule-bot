package com.code.integration.infrastructure.adapter.outbound.github;

import com.code.integration.application.port.outbound.GitHubCommentClient;
import com.code.integration.domain.model.ReviewComment;
import com.code.platform.metrics.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public final class GitHubCommentClientAdapter implements GitHubCommentClient {

    private final WebClient gitHubWebClient;
    private final MetricsHelper metricsHelper;

    @Override
    public Mono<Long> postComment(ReviewComment comment) {
        if (comment == null) {
            return Mono.error(new IllegalArgumentException("Comment must not be null"));
        }
        if (comment.repositoryOwner() == null || comment.repositoryOwner().isBlank()) {
            return Mono.error(new IllegalArgumentException("Repository owner must not be blank"));
        }
        if (comment.repositoryName() == null || comment.repositoryName().isBlank()) {
            return Mono.error(new IllegalArgumentException("Repository name must not be blank"));
        }
        if (comment.pullRequestNumber() == null) {
            return Mono.error(new IllegalArgumentException("Pull request number must not be null"));
        }
        if (comment.body() == null || comment.body().isBlank()) {
            return Mono.error(new IllegalArgumentException("Comment body must not be blank"));
        }

        String uri = "/repos/{owner}/{repo}/issues/{issue_number}/comments";
        log.debug("Posting comment to PR #{} in {}/{}",
                comment.pullRequestNumber(),
                comment.repositoryOwner(),
                comment.repositoryName());

        return gitHubWebClient.post()
                .uri(uri,
                        comment.repositoryOwner(),
                        comment.repositoryName(),
                        comment.pullRequestNumber())
                .bodyValue(Map.of("body", comment.body()))
                .retrieve()
                .bodyToMono(GitHubCommentResponse.class)
                .map(GitHubCommentResponse::id)
                .doOnSuccess(commentId -> {
                    log.debug("Comment posted successfully with ID: {}", commentId);
                    metricsHelper.incrementCounter("github.api.call", "endpoint", "comments", "status", "success");
                    metricsHelper.recordValue("github.comment.length", comment.body().length());
                })
                .doOnError(err -> {
                    log.error("Failed to post comment to PR #{}", comment.pullRequestNumber(), err);
                    metricsHelper.incrementCounter("github.api.call", "endpoint", "comments", "status", "failure",
                            "error_type", err.getClass().getSimpleName());
                });
    }

    record GitHubCommentResponse(Long id) {}
}
