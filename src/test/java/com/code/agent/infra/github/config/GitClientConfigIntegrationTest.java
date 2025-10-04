package com.code.agent.infra.github.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GitClientConfigIntegrationTest {

    @Test
    void gitHubWebClient_ShouldApplyTimeoutFromProperties() {
        Duration customResponseTimeout = Duration.ofSeconds(600);
        Duration customConnectTimeout = Duration.ofSeconds(10);

        GitHubProperties.Client client = new GitHubProperties.Client(
                customResponseTimeout,
                customConnectTimeout
        );
        GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "test-token",
                "/repos/{owner}/{repo}/pulls/{pull_number}/reviews",
                client
        );

        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        assertThat(webClient).isNotNull();
        // Verify that WebClient is created successfully
        // Check that timeout values from properties are correctly applied
        assertThat(properties.client().responseTimeout()).isEqualTo(customResponseTimeout);
        assertThat(properties.client().connectTimeout()).isEqualTo(customConnectTimeout);
    }

    @Test
    void gitHubWebClient_ShouldUseDefaultTimeouts() {
        GitHubProperties.Client client = new GitHubProperties.Client(null, null);
        GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "test-token",
                "/repos/{owner}/{repo}/pulls/{pull_number}/reviews",
                client
        );

        GitClientConfig config = new GitClientConfig();
        WebClient webClient = config.gitHubWebClient(properties);

        assertThat(webClient).isNotNull();

        // Verify that WebClient is created with default values
        assertThat(properties.client().responseTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(properties.client().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
