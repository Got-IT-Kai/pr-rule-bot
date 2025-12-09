package com.code.review.infrastructure.adapter.outbound.ai;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.client.ollama")
@Validated
public record OllamaAiProperties(
        @NotNull Duration responseTimeout,
        @NotNull Duration connectTimeout,
        int maxTokens
) {
}
