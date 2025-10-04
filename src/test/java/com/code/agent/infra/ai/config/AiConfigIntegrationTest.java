package com.code.agent.infra.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionProperties;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigIntegrationTest {

    @Test
    void ollamaApi_ShouldBeConfiguredWithTimeoutFromProperties() {
        Duration customResponseTimeout = Duration.ofMinutes(20);
        Duration customConnectTimeout = Duration.ofSeconds(30);

        AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(
                customResponseTimeout,
                customConnectTimeout,
                null  // maxTokens - will use default
        );
        AiClientProperties aiClientProperties = new AiClientProperties(ollama, null);

        OllamaConnectionProperties connectionProperties = new OllamaConnectionProperties();
        connectionProperties.setBaseUrl("http://localhost:11434");

        RestClient.Builder restClientBuilder = RestClient.builder();

        AiConfig config = new AiConfig();
        OllamaApi ollamaApi = config.ollamaApi(connectionProperties, aiClientProperties, restClientBuilder);

        assertThat(ollamaApi).isNotNull();
        assertThat(aiClientProperties.ollama().responseTimeout()).isEqualTo(customResponseTimeout);
        assertThat(aiClientProperties.ollama().connectTimeout()).isEqualTo(customConnectTimeout);
    }

    @Test
    void ollamaApi_ShouldUseDefaultTimeouts() {
        AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(null, null, null);
        AiClientProperties aiClientProperties = new AiClientProperties(ollama, null);

        OllamaConnectionProperties connectionProperties = new OllamaConnectionProperties();
        connectionProperties.setBaseUrl("http://localhost:11434");

        RestClient.Builder restClientBuilder = RestClient.builder();

        AiConfig config = new AiConfig();
        OllamaApi ollamaApi = config.ollamaApi(connectionProperties, aiClientProperties, restClientBuilder);

        assertThat(ollamaApi).isNotNull();
        assertThat(aiClientProperties.ollama().responseTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(aiClientProperties.ollama().connectTimeout()).isEqualTo(Duration.ofSeconds(15));
    }
}
