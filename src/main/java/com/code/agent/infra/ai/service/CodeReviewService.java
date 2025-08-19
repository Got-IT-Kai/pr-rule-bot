package com.code.agent.infra.ai.service;

import com.code.agent.application.port.out.AiPort;
import com.code.agent.infra.ai.router.AiRouter;
import com.code.agent.infra.ai.spi.AiModelClient;
import com.knuddels.jtokkit.api.Encoding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewService implements AiPort {
    private static final String GIT_DIFF_PREFIX = "diff --git ";

    private final AiRouter aiRouter;
    private final Encoding encoding;

    @Override
    public Mono<String> evaluateDiff(String diff) {
        log.debug("Evaluating diff for code review: {}", diff);

        if (!StringUtils.hasText(diff)) {
            log.warn("Received empty diff for code review.");
            return Mono.just("No changes to review.");
        }

        AiModelClient client = aiRouter.active();
        List<String> fileDiffs = splitDiffIntoFiles(diff);

        return Flux.fromIterable(fileDiffs)
                .filter(chunk -> tokenGuard(client, chunk))
                .flatMap(client::reviewCode, 5)
                .collectList()
                .flatMap(list ->
                        synthesizeIndividualReviews(client, list, fileDiffs.size() - list.size()));
    }

    private List<String> splitDiffIntoFiles(String diff) {
        if (!diff.startsWith(GIT_DIFF_PREFIX)) {
            return List.of(diff);
        }

        String normalizedDiff = diff.replaceAll("\r\n", "\n").replaceAll("\r", "\n");
        String delimiter = "\n" + GIT_DIFF_PREFIX;
        return Arrays.asList(normalizedDiff.substring(GIT_DIFF_PREFIX.length()).split(delimiter));
    }

   /* private Mono<String> getReviewForSingleDiff(String diffChunk) {
            log.debug("Processing diff chunk: {}", diffChunk);
            Map<String, Object> model = Map.of("diff", diffChunk);

            return chatClient.prompt(codeReviewPrompt.create(model))
                    .stream()
                    .content()
                    .timeout(Duration.ofMinutes(5))
                    .collect(Collectors.joining());
    }*/

    private Mono<String> synthesizeIndividualReviews(AiModelClient client, List<String> individualReviews, long missingReviewsCount) {
        int maxTokens = client.maxTokens();
        if (individualReviews.isEmpty()) {
            return Mono.just("All files exceed the %d‑token limit"
                    .formatted(maxTokens));
        }

        String combinedReviews = String.join("\n\n--- Next Review ---\n\n", individualReviews);

        if (!tokenGuard(client, combinedReviews)) {
            log.warn("Combined reviews exceed the token limit of {}", maxTokens);
            return Mono.just("Combined reviews exceed the %d‑token limit"
                    .formatted(maxTokens));
        }

        Mono<String> merged = client.reviewMerge(combinedReviews);
        if (missingReviewsCount > 0) {
            return merged.map(review -> review + """
                    
                    ---
                    %d files were not reviewed due to exceeding the %d‑token limit.
                    ---
                    """
                    .formatted(missingReviewsCount, maxTokens));
        }

        return merged;

        /*Map<String, Object> model = Map.of("merge", combinedReviews);
        Mono<String> mergedByAi = chatClient.prompt(reviewMergePrompt.create(model))
                .stream()
                .content()
                .timeout(Duration.ofMinutes(5))
                .collect(Collectors.joining());

        if (missingReviewsCount > 0) {
            return mergedByAi.map(review -> review + """
                    
                    ---
                    %d files were not reviewed due to exceeding the %d‑token limit.
                    ---
                    """
                    .formatted(missingReviewsCount, MAX_TOKENS));
        } else  {
            return mergedByAi;
        }*/
    }

    private int countTokens(String text) {
        return encoding.countTokens(text);
    }

    private boolean tokenGuard(AiModelClient client, String diff) {
        int countTokens = countTokens(diff);
        log.debug("Token count: {}", countTokens);
        return countTokens <= client.maxTokens();
    }
}
