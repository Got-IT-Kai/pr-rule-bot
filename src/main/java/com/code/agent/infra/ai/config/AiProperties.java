package com.code.agent.infra.ai.config;

import com.code.agent.infra.ai.model.AiProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@ConfigurationProperties(prefix= "ai")
@Validated
public record AiProperties(@NotNull AiProvider provider,
                           Map<AiProvider, @Valid Prompt> prompts) {

    public record Prompt(
            @NotNull Resource codeReviewPrompt,
            @NotNull Resource reviewMergePrompt
    ) {}
}
