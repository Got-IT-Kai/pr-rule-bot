package com.code.context.infrastructure.adapter.outbound.github;

import com.code.context.application.port.outbound.GitHubClient;
import com.code.context.domain.exception.InvalidDiffException;
import com.code.context.domain.validator.DiffValidator;
import com.code.context.domain.validator.ValidationResult;
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

    @Override
    public Mono<String> getDiff(String diffUrl) {
        log.debug("Fetching diff from: {}", diffUrl);

        return gitHubWebClient.get()
                .uri(diffUrl)
                .accept(MediaType.valueOf("application/vnd.github.v3.diff"))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(diff -> validateDiff(diff, diffUrl))
                .retryWhen(retryStrategy)
                .doOnError(err -> log.error("Failed to fetch diff from {}", diffUrl, err));
    }

    private Mono<String> validateDiff(String diff, String diffUrl) {
        ValidationResult result = diffValidator.validate(diff);

        return switch (result.status()) {
            case VALID -> {
                log.debug("Diff validation: {} ({} bytes)", result.getMessage(), diff.length());
                yield Mono.just(diff);
            }
            case SKIP -> {
                log.debug("Diff validation: {} - skipping review", result.getMessage());
                yield Mono.empty();
            }
            case INVALID -> {
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
        log.debug("Fetching file metadata for PR #{} in {}/{}", prNumber, repositoryOwner, repositoryName);

        return gitHubWebClient.get()
                .uri(uri, repositoryOwner, repositoryName, prNumber)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(retryStrategy)
                .doOnSuccess(metadata -> log.debug("File metadata fetched successfully"))
                .doOnError(err -> log.error("Failed to fetch file metadata for PR #{}", prNumber, err));
    }
}
