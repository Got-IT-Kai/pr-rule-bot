package com.code.agent.cli;

import com.code.agent.application.port.out.AiPort;
import com.code.agent.config.CliProperties;
import com.code.agent.infra.github.service.GitHubReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("ci")
public class ReviewCli implements ApplicationRunner {

    private final AiPort aiPort;
    private final GitHubReviewService gitHubReviewService;
    private final CliProperties cliProperties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String owner = cliProperties.repository().owner();
        String repo = cliProperties.repository().name();
        int prNumber = cliProperties.prNumber();

        log.info("AI review started for {}/{} PR #{}", owner, repo, prNumber);

        // Check if review already exists to prevent duplicate API calls
        gitHubReviewService.hasExistingReview(owner, repo, prNumber)
                .flatMap(hasReview -> {
                    if (hasReview) {
                        log.info("Review already exists for {}/{} PR #{}, skipping to prevent duplicate", owner, repo, prNumber);
                        return Mono.empty();
                    }
                    return gitHubReviewService.fetchUnifiedDiff(owner, repo, prNumber);
                })
                .filter(StringUtils::hasText)
                .flatMap(diff -> {
                    log.info("Diff fetched, starting AI review.");
                    return aiPort.evaluateDiff(diff);
                })
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("AI returned empty review for {}/{} PR #{}", owner, repo, prNumber);
                    return Mono.empty();
                }))
                .flatMap(review -> {
                    log.info("AI review completed, posting comment.");
                    return gitHubReviewService.postReviewComment(owner, repo, prNumber, review);
                })
                .doOnError(e -> log.error("Failed to complete AI review for {}/{} PR #{}", owner, repo, prNumber, e))
                .doOnSuccess(v -> log.info("AI review process finished for {}/{} PR #{}", owner, repo, prNumber))
                .block(Duration.ofMinutes(cliProperties.timeOutMinutes()));
    }
}
