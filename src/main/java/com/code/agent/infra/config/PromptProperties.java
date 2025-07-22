package com.code.agent.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "ai.prompt")
public record PromptProperties(
        Resource codeReviewPrompt,
        Resource reviewMergePrompt
) {
}
