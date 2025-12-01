package com.code.review.infrastructure.adapter.outbound.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai.client.gemini")
@Validated
public record GeminiAiProperties(
        int maxTokens
) {
}
