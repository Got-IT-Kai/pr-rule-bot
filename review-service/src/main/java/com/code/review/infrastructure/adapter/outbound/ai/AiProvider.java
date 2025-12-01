package com.code.review.infrastructure.adapter.outbound.ai;

import org.springframework.util.StringUtils;

public enum AiProvider {
    OLLAMA,
    GEMINI;

    public static AiProvider from(String provider) {
        if (!StringUtils.hasText(provider)) {
            return OLLAMA;
        }
        return AiProvider.valueOf(provider.trim().toUpperCase());
    }
}
