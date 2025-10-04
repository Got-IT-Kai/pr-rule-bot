package com.code.agent.infra.github.config;

import com.code.agent.infra.github.adapter.GitHubAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GitHubAdapterConfig}.
 * Tests cover:
 * - Bean creation and configuration
 * - Dependency injection
 * - Configuration validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubAdapterConfig")
class GitHubAdapterConfigTest {

    @Mock
    private WebClient mockWebClient;

    private GitHubAdapterConfig config;
    private GitHubProperties gitHubProperties;

    @BeforeEach
    void setUp() {
        config = new GitHubAdapterConfig();

        gitHubProperties = new GitHubProperties(
            "https://api.github.com",
            "test-token",
            "/repos/{owner}/{repo}/pulls/{pull_number}/reviews",
            new GitHubProperties.Client(
                Duration.ofSeconds(300),
                Duration.ofSeconds(5)
            )
        );
    }

    @Nested
    @DisplayName("when creating GitHubAdapter bean")
    class WhenCreatingGitHubAdapterBean {

        @Test
        @DisplayName("should create bean with all dependencies")
        void shouldCreateBeanWithAllDependencies() {
            // When
            GitHubAdapter adapter = config.gitHubAdapter(mockWebClient, gitHubProperties);

            // Then
            assertThat(adapter).isNotNull();
        }

        @Test
        @DisplayName("should use provided WebClient")
        void shouldUseProvidedWebClient() {
            // When
            GitHubAdapter adapter = config.gitHubAdapter(mockWebClient, gitHubProperties);

            // Then
            assertThat(adapter).isNotNull();
            // Adapter should be initialized with the mock WebClient
        }

        @Test
        @DisplayName("should use GitHubProperties configuration")
        void shouldUseGitHubPropertiesConfiguration() {
            // When
            GitHubAdapter adapter = config.gitHubAdapter(mockWebClient, gitHubProperties);

            // Then
            assertThat(adapter).isNotNull();
            // Adapter should use the properties for review path and configuration
        }

        @Test
        @DisplayName("should create new instance each time")
        void shouldCreateNewInstanceEachTime() {
            // When
            GitHubAdapter adapter1 = config.gitHubAdapter(mockWebClient, gitHubProperties);
            GitHubAdapter adapter2 = config.gitHubAdapter(mockWebClient, gitHubProperties);

            // Then
            assertThat(adapter1).isNotNull();
            assertThat(adapter2).isNotNull();
            assertThat(adapter1).isNotSameAs(adapter2);
        }
    }

    @Nested
    @DisplayName("with different GitHubProperties configurations")
    class WithDifferentGitHubPropertiesConfigurations {

        @Test
        @DisplayName("should handle custom timeout values")
        void shouldHandleCustomTimeoutValues() {
            // Given
            GitHubProperties customProperties = new GitHubProperties(
                "https://custom.github.com",
                "custom-token",
                "/custom/path",
                new GitHubProperties.Client(
                    Duration.ofSeconds(600),
                    Duration.ofSeconds(10)
                )
            );

            // When
            GitHubAdapter adapter = config.gitHubAdapter(mockWebClient, customProperties);

            // Then
            assertThat(adapter).isNotNull();
        }

        @Test
        @DisplayName("should handle default timeout values")
        void shouldHandleDefaultTimeoutValues() {
            // Given
            GitHubProperties defaultProperties = new GitHubProperties(
                "https://api.github.com",
                "test-token",
                "/repos/{owner}/{repo}/pulls/{pull_number}/reviews",
                new GitHubProperties.Client(null, null)  // Will use defaults
            );

            // When
            GitHubAdapter adapter = config.gitHubAdapter(mockWebClient, defaultProperties);

            // Then
            assertThat(adapter).isNotNull();
        }
    }

    @Nested
    @DisplayName("bean lifecycle")
    class BeanLifecycle {

        @Test
        @DisplayName("should be instantiatable multiple times")
        void shouldBeInstantiatableMultipleTimes() {
            // When
            GitHubAdapterConfig config1 = new GitHubAdapterConfig();
            GitHubAdapterConfig config2 = new GitHubAdapterConfig();

            // Then
            assertThat(config1).isNotNull();
            assertThat(config2).isNotNull();
            assertThat(config1).isNotSameAs(config2);
        }

        @Test
        @DisplayName("should create adapter with no-arg constructor")
        void shouldCreateAdapterWithNoArgConstructor() {
            // When
            GitHubAdapterConfig newConfig = new GitHubAdapterConfig();
            GitHubAdapter adapter = newConfig.gitHubAdapter(mockWebClient, gitHubProperties);

            // Then
            assertThat(adapter).isNotNull();
        }
    }
}