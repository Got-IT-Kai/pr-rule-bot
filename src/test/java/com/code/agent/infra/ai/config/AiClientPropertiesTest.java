package com.code.agent.infra.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiClientProperties}.
 * Tests cover:
 * - Ollama configuration (timeouts and maxTokens)
 * - Gemini configuration (maxTokens)
 * - Default value initialization
 * - Combined configurations
 */
@DisplayName("AiClientProperties")
class AiClientPropertiesTest {

    @Nested
    @DisplayName("Ollama configuration")
    class OllamaConfiguration {

        @Nested
        @DisplayName("timeout configuration")
        class TimeoutConfiguration {

            @Test
            @DisplayName("should have default timeouts when null")
            void shouldHaveDefaultTimeoutsWhenNull() {
                AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(null, null, null);

                assertThat(ollama.responseTimeout()).isEqualTo(Duration.ofMinutes(10));
                assertThat(ollama.connectTimeout()).isEqualTo(Duration.ofSeconds(15));
            }

            @Test
            @DisplayName("should use custom timeouts when provided")
            void shouldUseCustomTimeoutsWhenProvided() {
                Duration customResponse = Duration.ofMinutes(20);
                Duration customConnect = Duration.ofSeconds(30);
                AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(
                    customResponse,
                    customConnect,
                    null
                );

                assertThat(ollama.responseTimeout()).isEqualTo(customResponse);
                assertThat(ollama.connectTimeout()).isEqualTo(customConnect);
            }
        }

        @Nested
        @DisplayName("maxTokens configuration")
        class MaxTokensConfiguration {

            @Test
            @DisplayName("should have default maxTokens when null")
            void shouldHaveDefaultMaxTokensWhenNull() {
                AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(null, null, null);

                assertThat(ollama.maxTokens()).isEqualTo(7680);
            }

            @Test
            @DisplayName("should use custom maxTokens when provided")
            void shouldUseCustomMaxTokensWhenProvided() {
                AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(
                    null,
                    null,
                    16_000
                );

                assertThat(ollama.maxTokens()).isEqualTo(16_000);
            }
        }

        @Test
        @DisplayName("should have all properties configured")
        void shouldHaveAllPropertiesConfigured() {
            AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(
                Duration.ofMinutes(15),
                Duration.ofSeconds(20),
                8192
            );

            assertThat(ollama.responseTimeout()).isEqualTo(Duration.ofMinutes(15));
            assertThat(ollama.connectTimeout()).isEqualTo(Duration.ofSeconds(20));
            assertThat(ollama.maxTokens()).isEqualTo(8192);
        }
    }

    @Nested
    @DisplayName("Gemini configuration")
    class GeminiConfiguration {

        @Nested
        @DisplayName("maxTokens configuration")
        class MaxTokensConfiguration {

            @Test
            @DisplayName("should have default maxTokens when null")
            void shouldHaveDefaultMaxTokensWhenNull() {
                AiClientProperties.Gemini gemini = new AiClientProperties.Gemini(null);

                assertThat(gemini.maxTokens()).isEqualTo(100_000);
            }

            @Test
            @DisplayName("should use custom maxTokens when provided")
            void shouldUseCustomMaxTokensWhenProvided() {
                AiClientProperties.Gemini gemini = new AiClientProperties.Gemini(200_000);

                assertThat(gemini.maxTokens()).isEqualTo(200_000);
            }
        }
    }

    @Nested
    @DisplayName("combined configurations")
    class CombinedConfigurations {

        @Test
        @DisplayName("should contain both Ollama and Gemini configs")
        void shouldContainBothOllamaAndGeminiConfigs() {
            AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(
                Duration.ofMinutes(10),
                Duration.ofSeconds(15),
                7680
            );
            AiClientProperties.Gemini gemini = new AiClientProperties.Gemini(100_000);

            AiClientProperties properties = new AiClientProperties(ollama, gemini);

            assertThat(properties.ollama()).isNotNull();
            assertThat(properties.ollama().responseTimeout()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.ollama().connectTimeout()).isEqualTo(Duration.ofSeconds(15));
            assertThat(properties.ollama().maxTokens()).isEqualTo(7680);

            assertThat(properties.gemini()).isNotNull();
            assertThat(properties.gemini().maxTokens()).isEqualTo(100_000);
        }

        @Test
        @DisplayName("should handle null Ollama config")
        void shouldHandleNullOllamaConfig() {
            AiClientProperties.Gemini gemini = new AiClientProperties.Gemini(100_000);
            AiClientProperties properties = new AiClientProperties(null, gemini);

            assertThat(properties.ollama()).isNull();
            assertThat(properties.gemini()).isNotNull();
            assertThat(properties.gemini().maxTokens()).isEqualTo(100_000);
        }

        @Test
        @DisplayName("should handle null Gemini config")
        void shouldHandleNullGeminiConfig() {
            AiClientProperties.Ollama ollama = new AiClientProperties.Ollama(
                Duration.ofMinutes(10),
                Duration.ofSeconds(15),
                7680
            );
            AiClientProperties properties = new AiClientProperties(ollama, null);

            assertThat(properties.ollama()).isNotNull();
            assertThat(properties.ollama().maxTokens()).isEqualTo(7680);
            assertThat(properties.gemini()).isNull();
        }

        @Test
        @DisplayName("should handle both null configs")
        void shouldHandleBothNullConfigs() {
            AiClientProperties properties = new AiClientProperties(null, null);

            assertThat(properties.ollama()).isNull();
            assertThat(properties.gemini()).isNull();
        }
    }
}
