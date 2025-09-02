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

import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    public OllamaApi ollamaApi(OllamaConnectionProperties connectionProperties, RestClient.Builder restClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(10))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
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
