package com.code.agent.infra.ai.config;

import io.netty.channel.ChannelOption;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionProperties;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class AiConfig {

    @Bean
    public OllamaApi ollamaApi(OllamaConnectionProperties connectionProperties,
                               AiClientProperties aiClientProperties,
                               RestClient.Builder restClientBuilder) {
        AiClientProperties.Ollama ollamaConfig = aiClientProperties.ollama();

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(ollamaConfig.responseTimeout())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) ollamaConfig.connectTimeout().toMillis())
                .followRedirect(true);

        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(connectionProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        return OllamaApi.builder()
                .baseUrl(connectionProperties.getBaseUrl())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder).build();
    }
}
