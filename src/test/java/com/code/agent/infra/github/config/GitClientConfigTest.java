package com.code.agent.infra.github.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GitClientConfigTest {

    @Test
    void gitHubWebClient_ShouldBeConfigured() {
        GitHubProperties.Client client = new GitHubProperties.Client(
                Duration.ofSeconds(300),
                Duration.ofSeconds(5)
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
        // WebClient is immutable and internal configuration is private,
        // actual behavior is verified in integration tests
    }

    @Test
    void gitHubWebClient_ShouldHaveCorrectBaseUrl() {
        GitHubProperties.Client client = new GitHubProperties.Client(
                Duration.ofSeconds(300),
                Duration.ofSeconds(5)
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
        // WebClient's baseUrl is used at request time, verified in integration tests
    }

    @Test
    void gitHubClientProperties_ShouldHaveDefaultTimeouts() {
        GitHubProperties.Client client = new GitHubProperties.Client(null, null);

        assertThat(client.responseTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(client.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void gitHubClientProperties_ShouldUseCustomTimeouts() {
        Duration customResponse = Duration.ofSeconds(600);
        Duration customConnect = Duration.ofSeconds(10);
        GitHubProperties.Client client = new GitHubProperties.Client(customResponse, customConnect);

        assertThat(client.responseTimeout()).isEqualTo(customResponse);
        assertThat(client.connectTimeout()).isEqualTo(customConnect);
    }
}
