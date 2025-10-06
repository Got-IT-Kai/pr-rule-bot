package com.code.agent.infra.github.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import static com.code.agent.infra.github.GitHubConstants.*;

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

        return WebClient.builder()
                .baseUrl(gitHubProperties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, API_ACCEPT_HEADER)
                .defaultHeader(API_VERSION_HEADER, API_VERSION)
                .filter((request, next) -> {
                    String token = gitHubProperties.token();
                    if (token == null || token.isBlank()) {
                        throw new IllegalStateException(ERROR_TOKEN_REQUIRED);
                    }
                    ClientRequest modifiedRequest = ClientRequest.from(request)
                            .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_PREFIX + token)
                            .build();
                    return next.exchange(modifiedRequest);
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

    }
}
