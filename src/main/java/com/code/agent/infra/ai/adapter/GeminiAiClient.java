package com.code.agent.infra.ai.adapter;

import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.ai.spi.AiModelClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class GeminiAiClient implements AiModelClient {
    @Override
    public Mono<String> reviewCode(String diff) {
        return null;
    }

    @Override
    public Mono<String> reviewMerge(String combinedReviews) {
        return null;
    }

    @Override
    public int maxTokens() {
        return 0;
    }

    @Override
    public Duration requestTimeout() {
        return null;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public String modelName() {
        return AiProvider.GEMINI.name().toLowerCase();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }
}
