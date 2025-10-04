package com.code.agent.infra.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.client")
public record AiClientProperties(
        Ollama ollama,
        Gemini gemini
) {
    public record Ollama(
            Duration responseTimeout,
            Duration connectTimeout,
            Integer maxTokens
    ) {
        public Ollama {
            if (responseTimeout == null) {
                responseTimeout = Duration.ofMinutes(10);
            }
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(15);
            }
            if (maxTokens == null) {
                maxTokens = 7680; // Ollama default context window
            }
        }
    }

    public record Gemini(
            Integer maxTokens
    ) {
        public Gemini {
            if (maxTokens == null) {
                maxTokens = 100_000; // Gemini default context window
            }
        }
    }
}
