package com.code.context.infrastructure.adapter.outbound.github;

import com.code.context.application.port.outbound.GitHubClient;
import com.code.context.domain.exception.InvalidDiffException;
import com.code.context.domain.validator.DiffValidator;
import com.code.context.domain.validator.ValidationResult;
import com.code.platform.metrics.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@Component
@RequiredArgsConstructor
public final class GitHubClientAdapter implements GitHubClient {

    private final WebClient gitHubWebClient;
    private final Retry retryStrategy;
    private final DiffValidator diffValidator;
    private final MetricsHelper metricsHelper;

    @Override
    public Mono<String> getDiff(String diffUrl) {
        log.debug("Fetching diff from: {}", diffUrl);

        return gitHubWebClient.get()
                .uri(diffUrl)
                .accept(MediaType.valueOf("application/vnd.github.v3.diff"))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(diff -> validateAndRecordMetrics(diff, diffUrl))
                .retryWhen(retryStrategy)
                .doOnError(err -> {
                    // Don't record metrics twice for InvalidDiffException
                    if (!(err instanceof InvalidDiffException)) {
                        metricsHelper.incrementCounter("github.api.call",
                            "endpoint", "diff", "status", "failure",
                            "error_type", err.getClass().getSimpleName());
                    }
                    log.error("Failed to fetch diff from {}", diffUrl, err);
                });
    }

    private Mono<String> validateAndRecordMetrics(String diff, String diffUrl) {
        ValidationResult result = diffValidator.validate(diff);

        // Record validation metrics for ALL cases
        metricsHelper.incrementCounter("diff.validation",
            "status", result.status().name(),
            "reason", result.reason().name());

        return switch (result.status()) {
            case VALID -> {
                metricsHelper.incrementCounter("github.api.call",
                    "endpoint", "diff", "status", "success");
                metricsHelper.recordValue("github.diff.size", diff.length());
                log.debug("Diff validation: {} ({} bytes)", result.getMessage(), diff.length());
                yield Mono.just(diff);
            }
            case SKIP -> {
                metricsHelper.incrementCounter("github.api.call",
                    "endpoint", "diff", "status", "success");
                log.debug("Diff validation: {} - skipping review", result.getMessage());
                yield Mono.empty();
            }
            case INVALID -> {
                metricsHelper.incrementCounter("github.api.call",
                    "endpoint", "diff", "status", "failure",
                    "error_type", "InvalidDiffException");
                log.warn("Diff validation failed: {}", result.getMessage());
                yield Mono.error(new InvalidDiffException(result));
            }
        };
    }

    @Override
    public Mono<String> getFileMetadata(String repositoryOwner, String repositoryName, Integer prNumber) {
        if (repositoryOwner == null || repositoryName == null || prNumber == null) {
            return Mono.error(new IllegalArgumentException("Parameters must not be null"));
        }

        String uri = "/repos/{owner}/{repo}/pulls/{pull_number}/files";
        log.debug("Fetching file metadata for PR #{} in {}/{}",prNumber, repositoryOwner, repositoryName);

        return gitHubWebClient.get()
                .uri(uri, repositoryOwner, repositoryName, prNumber)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(retryStrategy)
                .doOnSuccess(metadata -> log.debug("File metadata fetched successfully"))
                .doOnError(err -> log.error("Failed to fetch file metadata for PR #{}", prNumber, err));
    }
}
