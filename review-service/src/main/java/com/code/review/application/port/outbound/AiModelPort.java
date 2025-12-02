package com.code.review.application.port.outbound;

import com.code.review.domain.model.PrContext;
import reactor.core.publisher.Mono;

public interface AiModelPort {

    Mono<String> reviewCode(String diff, PrContext prContext);

    Mono<String> mergeReviews(String combinedReviews, PrContext prContext);

    int maxTokens();

    String providerName();

    String modelName();
}
