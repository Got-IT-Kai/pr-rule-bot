package com.code.review.infrastructure.adapter.outbound.ai;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai.prompts")
@Validated
public record PromptProperties(
        @NotNull Prompts ollama,
        @NotNull Prompts gemini
) {
    public record Prompts(
            @NotNull Resource codeReviewPrompt,
            @NotNull Resource reviewMergePrompt
    ) {
    }

    public Prompts getPromptsFor(AiProvider provider) {
        return switch (provider) {
            case OLLAMA -> ollama;
            case GEMINI -> gemini;
        };
    }
}
