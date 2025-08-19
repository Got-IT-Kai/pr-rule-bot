package com.code.agent.infra.ai.spi;

import com.code.agent.infra.ai.model.AiProvider;
import reactor.core.publisher.Mono;

import java.time.Duration;

public interface AiModelClient {
    Mono<String> reviewCode(String diff);
    Mono<String> reviewMerge(String combinedReviews);
    int maxTokens();
    Duration requestTimeout();
    boolean isReady();
    String modelName();
    AiProvider provider();
}
