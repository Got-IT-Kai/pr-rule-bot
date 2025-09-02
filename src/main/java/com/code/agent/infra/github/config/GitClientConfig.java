package com.code.agent.infra.github.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class GitClientConfig {

    @Bean
    @Qualifier("gitHubWebClient")
    WebClient gitHubWebClient(GitHubProperties gitHubProperties) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(Duration.ofSeconds(300))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);

        return WebClient.builder().baseUrl(gitHubProperties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gitHubProperties.token())
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

    }
}
