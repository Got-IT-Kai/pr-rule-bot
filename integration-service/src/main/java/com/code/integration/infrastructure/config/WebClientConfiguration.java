package com.code.integration.infrastructure.config;

import com.code.platform.github.GitHubProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebClientConfiguration {

    private final GitHubProperties gitHubProperties;

    @Bean
    public WebClient gitHubWebClient() {
        // Validate token configuration at startup
        if (gitHubProperties.token() == null || gitHubProperties.token().isBlank()) {
            throw new IllegalStateException("GitHub API token must be configured (github.api.token)");
        }

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) gitHubProperties.timeout().connect().toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(gitHubProperties.timeout().read().toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(gitHubProperties.timeout().write().toMillis(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(gitHubProperties.baseUrl())
                .defaultHeader("Authorization", "token " + gitHubProperties.token())
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "PR-Rule-Bot/1.0")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
