package com.code.review.infrastructure.adapter.outbound.ai;

import com.code.review.application.port.outbound.AiModelPort;
import com.code.review.domain.model.PrContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class CompositeAiModelAdapter implements AiModelPort {

    private final AiClient activeClient;

    public CompositeAiModelAdapter(List<AiClient> clients, AiProperties aiProperties) {
        AiProvider configuredProvider = aiProperties.provider();

        this.activeClient = clients.stream()
                .filter(client -> client.provider() == configuredProvider)
                .filter(AiClient::isReady)
                .findFirst()
                .or(() -> clients.stream()
                        .filter(AiClient::isReady)
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "No AI client is ready. Configured provider: " + configuredProvider));

        if (activeClient.provider() != configuredProvider) {
            log.warn("Configured provider {} not ready. Using fallback: {}",
                    configuredProvider, activeClient.provider());
        } else {
            log.info("Using configured AI provider: {}", configuredProvider);
        }
    }

    @Override
    public Mono<String> reviewCode(String diff, PrContext prContext) {
        return activeClient.reviewCode(diff, prContext);
    }

    @Override
    public Mono<String> mergeReviews(String combinedReviews, PrContext prContext) {
        return activeClient.mergeReviews(combinedReviews, prContext);
    }

    @Override
    public int maxTokens() {
        return activeClient.maxTokens();
    }

    @Override
    public String providerName() {
        return activeClient.providerName();
    }

    @Override
    public String modelName() {
        return activeClient.modelName();
    }
}
