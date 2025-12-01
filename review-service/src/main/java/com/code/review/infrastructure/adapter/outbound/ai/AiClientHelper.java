package com.code.review.infrastructure.adapter.outbound.ai;

import com.code.review.domain.model.PrContext;
import com.code.review.domain.model.PrType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
final class AiClientHelper {

    private final ChatClient chatClient;
    private final PromptTemplate codeReviewPrompt;
    private final PromptTemplate reviewMergePrompt;
    private final String providerName;

    AiClientHelper(ChatClient chatClient,
                   PromptTemplate codeReviewPrompt,
                   PromptTemplate reviewMergePrompt,
                   String providerName) {
        this.chatClient = chatClient;
        this.codeReviewPrompt = codeReviewPrompt;
        this.reviewMergePrompt = reviewMergePrompt;
        this.providerName = providerName;
    }

    static String loadResource(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt resource: {}", resource, e);
            throw new IllegalStateException("Failed to load prompt template", e);
        }
    }

    Mono<String> reviewCode(String diff, PrContext prContext) {
        if (chatClient == null) {
            return Mono.error(new IllegalStateException(providerName + " AI client is not configured"));
        }

        log.debug("Processing diff chunk with {}: {} chars, PR type: {}",
                providerName, diff.length(), prContext.type());

        Map<String, Object> model = new HashMap<>();
        model.put("diff", diff);

        if (prContext != null && prContext.type() != PrType.UNKNOWN) {
            model.put("context", Map.of(
                    "type", prContext.type().displayName(),
                    "title", prContext.title() != null ? prContext.title() : "",
                    "focus", prContext.focus() != null ? prContext.focus() : ""
            ));
        }

        return chatClient.prompt(codeReviewPrompt.create(model))
                .stream()
                .content()
                .collect(Collectors.joining());
    }

    Mono<String> mergeReviews(String combinedReviews, PrContext prContext) {
        if (chatClient == null) {
            return Mono.error(new IllegalStateException(providerName + " AI client is not configured"));
        }

        Map<String, Object> prContextMap = (prContext != null && prContext.type() != PrType.UNKNOWN)
                ? Map.of(
                    "type", prContext.type().displayName(),
                    "title", prContext.title() != null ? prContext.title() : ""
                )
                : Map.of("type", "General", "title", "");

        Map<String, Object> model = Map.of(
                "merge", combinedReviews,
                "prContext", prContextMap
        );

        return chatClient.prompt(reviewMergePrompt.create(model))
                .stream()
                .content()
                .collect(Collectors.joining());
    }

    boolean isReady() {
        return chatClient != null;
    }
}
