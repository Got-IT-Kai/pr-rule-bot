package com.code.agent.infra.ai.router;

import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.ai.spi.AiModelClient;
import com.code.agent.infra.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AiRouter {
    private final Map<AiProvider, AiModelClient> aiModelClients;
    private final AiProvider configured;

    public AiRouter(Map<AiProvider, AiModelClient> aiModelClients, AiProperties aiProperties) {
        this.aiModelClients = aiModelClients;
        configured = aiProperties.provider();
    }

    public AiModelClient active() {
        AiModelClient client = aiModelClients.get(configured);

        if (client == null) {
            log.warn("No AI client found for provider: {}", configured);
            throw new IllegalStateException("No AI client registered for provider: " + configured);
        }

        if (client.isReady()) {
            return client;
        }

        AiModelClient fallbackClient = aiModelClients.values().stream()
                .filter(AiModelClient::isReady)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No AI client is ready for provider " + configured + " and no fallback available."));

        log.warn("Selected provider {} not ready. Falling back to {}.", configured, fallbackClient.modelName());
        return fallbackClient;
    }
}
