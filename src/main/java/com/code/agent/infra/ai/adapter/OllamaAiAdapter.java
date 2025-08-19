package com.code.agent.infra.ai.adapter;

import com.code.agent.application.port.out.AiPort;
import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.config.AiProperties;
import com.knuddels.jtokkit.api.Encoding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@Primary
public class OllamaAiAdapter implements AiPort {

    private final ChatClient chatClient;

    private final Encoding tiktokenEncoding;

    private final PromptTemplate codeReviewPrompt;
    private final PromptTemplate reviewMergePrompt;

    private static final int MAX_TOKENS = 7680;

    private static final String GIT_DIFF_PREFIX = "diff --git ";

    public OllamaAiAdapter(ChatClient.Builder chatClientBuilder,
                           Encoding tiktokenEncoding,
                           AiProperties aiProperties) {
        this.chatClient = chatClientBuilder.build();
        this.tiktokenEncoding = tiktokenEncoding;
        AiProperties.Prompt prompt = aiProperties.prompts().get(AiProvider.OLLAMA);
        this.codeReviewPrompt = new PromptTemplate(prompt.codeReviewPrompt());
        this.reviewMergePrompt = new PromptTemplate(prompt.reviewMergePrompt());
    }

    @Override
    public Mono<String> evaluateDiff(String diff) {
        log.debug("Evaluating diff for code review: {}", diff);

        if (!StringUtils.hasText(diff)) {
            log.warn("Received empty diff for code review.");
            return Mono.just("No changes to review.");
        }

        List<String> fileDiffs = splitDiffIntoFiles(diff);

        return Flux.fromIterable(fileDiffs)
                .filter(this::tokenGuard)
                .flatMap(this::getReviewForSingleDiff, 5)
                .collectList()
                .flatMap(list ->
                        synthesizeIndividualReviews(list, fileDiffs.size() - list.size()));
    }

    private List<String> splitDiffIntoFiles(String diff) {
        if (!diff.startsWith(GIT_DIFF_PREFIX)) {
            return List.of(diff);
        }

        String normalizedDiff = diff.replaceAll("\r\n", "\n").replaceAll("\r", "\n");
        String delimiter = "\n" + GIT_DIFF_PREFIX;
        return Arrays.asList(normalizedDiff.substring(GIT_DIFF_PREFIX.length()).split(delimiter));
    }

    private Mono<String> getReviewForSingleDiff(String diffChunk) {
        log.debug("Processing diff chunk: {}", diffChunk);
        Map<String, Object> model = Map.of("diff", diffChunk);

        return chatClient.prompt(codeReviewPrompt.create(model))
                .stream()
                .content()
                .timeout(Duration.ofMinutes(5))
                .collect(Collectors.joining());
    }

    private Mono<String> synthesizeIndividualReviews(List<String> individualReviews, long missingReviewsCount) {
        if (individualReviews.isEmpty()) {
            return Mono.just("All files exceed the %d‑token limit"
                    .formatted(MAX_TOKENS));
        }

        String combinedReviews = String.join("\n\n--- Next Review ---\n\n", individualReviews);

        if (!tokenGuard(combinedReviews)) {
            log.warn("Combined reviews exceed the token limit of {}", MAX_TOKENS);
            return Mono.just("Combined reviews exceed the %d‑token limit"
                    .formatted(MAX_TOKENS));
        }

        Map<String, Object> model = Map.of("merge", combinedReviews);
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
        }
    }

    private int countTokens(String text) {
        return tiktokenEncoding.countTokens(text);
    }

    private boolean tokenGuard(String diff) {
        int countTokens = countTokens(diff);
        log.debug("Token count: {}", countTokens);
        return countTokens <= MAX_TOKENS;
    }

}
