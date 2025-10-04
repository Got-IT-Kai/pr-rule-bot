package com.code.agent.infra.github.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class GitClientConfig {

    @Bean
    WebClient gitHubWebClient(GitHubProperties gitHubProperties) {
        GitHubProperties.Client clientConfig = gitHubProperties.client();

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(clientConfig.responseTimeout())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) clientConfig.connectTimeout().toMillis());

        return WebClient.builder().baseUrl(gitHubProperties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gitHubProperties.token())
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

    }
}
