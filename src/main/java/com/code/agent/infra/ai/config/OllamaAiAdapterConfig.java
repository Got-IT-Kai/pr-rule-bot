package com.code.agent.infra.ai.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OllamaAiAdapterConfig {

    @Bean
    @Primary
    WebClient.Builder ollamaWebClientBuilder() {
        return WebClient.builder()
                .baseUrl("http://localhost:11434")
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofMinutes(10))
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                ));
    }
}
