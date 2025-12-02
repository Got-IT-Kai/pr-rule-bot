package com.code.context.infrastructure.config;

import com.code.context.domain.exception.InvalidDiffException;
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
import reactor.util.retry.Retry;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GitHubClientConfig {

    private final GitHubProperties properties;

    @Bean
    public WebClient gitHubWebClient() {
        // Validate token configuration at startup
        if (properties.token() == null || properties.token().isBlank()) {
            throw new IllegalStateException("GitHub API token must be configured (github.api.token)");
        }

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.timeout().connect().toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(properties.timeout().read().toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.timeout().read().toMillis(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "token " + properties.token())
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "PR-Rule-Bot/1.0")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public Retry retryStrategy() {
        return Retry.backoff(properties.retry().maxAttempts(), properties.retry().backoff())
                .filter(throwable -> {
                    // Don't retry validation failures - they won't change on retry
                    if (throwable instanceof InvalidDiffException) {
                        log.debug("Skipping retry for InvalidDiffException");
                        return false;
                    }
                    log.debug("Evaluating retry for error: {}", throwable.getMessage());
                    return true;
                })
                .doBeforeRetry(signal ->
                    log.warn("Retrying GitHub API call (attempt {}): {}",
                            signal.totalRetries() + 1, signal.failure().getMessage()));
    }
}
