package com.code.review.infrastructure.adapter.outbound.ai;

import com.code.review.domain.model.PrContext;
import reactor.core.publisher.Mono;

interface AiClient {

    Mono<String> reviewCode(String diff, PrContext prContext);

    Mono<String> mergeReviews(String combinedReviews, PrContext prContext);

    int maxTokens();

    String providerName();

    String modelName();

    boolean isReady();

    AiProvider provider();
}