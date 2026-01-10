package com.code.integration.infrastructure.adapter.outbound.github;

import com.code.integration.application.port.outbound.GitHubCommentClient;
import com.code.integration.domain.model.ReviewComment;
import com.code.platform.metrics.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubCommentClientAdapter implements GitHubCommentClient {

    private static final String URI_TEMPLATE = "/repos/{owner}/{repo}/issues/{issue_number}/comments";
    private static final int MAX_ERROR_BODY_LENGTH = 2_000;

    private final WebClient gitHubWebClient;
    private final GitHubCommentBodyTrimmer trimmer;
    private final MetricsHelper metricsHelper;

    @Override
    public Mono<Long> postComment(ReviewComment comment) {
        return validate(comment)
                .flatMap(c -> {
                    GitHubCommentBodyTrimmer.TrimResult trimmed = trimmer.trimWithMetadata(c.body());

                    if (trimmed.changed()) {
                        log.warn(
                                "Comment trimmed: owner={}/{} pr={} originalLen={} finalLen={} removed={} hardTruncated={}",
                                c.repositoryOwner(), c.repositoryName(), c.pullRequestNumber(),
                                trimmed.originalLength(), trimmed.finalLength(),
                                trimmed.removedSections(), trimmed.hardTruncated()
                        );

                        metricsHelper.incrementCounter("github.comment.trim.applied");
                        if (trimmed.hardTruncated()) {
                            metricsHelper.incrementCounter("github.comment.trim.hard_truncated");
                        }
                    }

                    String body = trimmed.body();

                    log.debug("Posting comment to PR #{} in {}/{}",
                            c.pullRequestNumber(),
                            c.repositoryOwner(),
                            c.repositoryName());

                    return gitHubWebClient.post()
                            .uri(URI_TEMPLATE, c.repositoryOwner(), c.repositoryName(), c.pullRequestNumber())
                            .bodyValue(Map.of("body", body))
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, ClientResponse::createException)
                            .bodyToMono(GitHubCommentResponse.class)
                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                    "GitHub returned empty response body for create-comment"
                            )))
                            .flatMap(resp -> Mono.justOrEmpty(resp.id())
                                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                                    "GitHub response missing comment id"))))
                            .doOnNext(commentId -> {
                                log.debug("Comment posted successfully with ID: {}", commentId);
                                metricsHelper.incrementCounter("github.comment.post.success");
                            })
                            .doOnError(err -> {
                                logPostError(c, err);
                                metricsHelper.incrementCounter("github.comment.post.failure");
                            });
                });

    }

    private static Mono<ReviewComment> validate(ReviewComment comment) {
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

        return Mono.just(comment);
    }

    private static void logPostError(ReviewComment comment, Throwable err) {
        if (err instanceof WebClientResponseException wcre) {
            log.error(
                    "Failed to post comment: owner={}/{} pr={} status={} responseBody={}",
                    comment.repositoryOwner(),
                    comment.repositoryName(),
                    comment.pullRequestNumber(),
                    wcre.getStatusCode().value(),
                    truncateForLog(wcre.getResponseBodyAsString(), MAX_ERROR_BODY_LENGTH),
                    err
            );
            return;
        }

        log.error(
                "Failed to post comment: owner={}/{} pr={}",
                comment.repositoryOwner(),
                comment.repositoryName(),
                comment.pullRequestNumber(),
                err
        );
    }

    private static String truncateForLog(String body, int maxLogLength) {
        if (body == null) { return ""; }
        if (body.length() <= maxLogLength) { return body; }
        return body.substring(0, maxLogLength) + "...(truncated)";
    }

    record GitHubCommentResponse(Long id) {}
}
