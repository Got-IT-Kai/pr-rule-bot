package com.code.agent.infra.ai.router;

import com.code.agent.infra.ai.config.AiProperties;
import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.ai.spi.AiModelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link AiRouter}.
 * Test scenarios cover:
 * - Provider selection logic
 * - Fallback mechanisms
 * - Error handling
 * - Edge cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiRouter")
class AiRouterTest {

    @Mock
    private AiModelClient ollamaClient;

    @Mock
    private AiModelClient geminiClient;

    @Mock
    private AiProperties aiProperties;

    private Map<AiProvider, AiModelClient> aiModelClients;
    private AiRouter aiRouter;

    @BeforeEach
    void setUp() {
        aiModelClients = new HashMap<>();
        aiModelClients.put(AiProvider.OLLAMA, ollamaClient);
        aiModelClients.put(AiProvider.GEMINI, geminiClient);
    }

    @Nested
    @DisplayName("when activating AI client")
    class WhenActivatingAiClient {

        @Nested
        @DisplayName("with configured provider ready")
        class WithConfiguredProviderReady {

            @BeforeEach
            void setUp() {
                // Given: Ollama is configured and ready
                given(aiProperties.provider()).willReturn(AiProvider.OLLAMA);
                given(ollamaClient.isReady()).willReturn(true);

                aiRouter = new AiRouter(aiModelClients, aiProperties);
            }

            @Test
            @DisplayName("should return configured client directly")
            void shouldReturnConfiguredClientDirectly() {
                // When
                AiModelClient result = aiRouter.active();

                // Then
                assertThat(result).isSameAs(ollamaClient);
                verify(ollamaClient).isReady();
                verifyNoInteractions(geminiClient);
            }
        }

        @Nested
        @DisplayName("with configured provider not ready")
        class WithConfiguredProviderNotReady {

            @Test
            @DisplayName("should fallback to ready provider")
            void shouldFallbackToReadyProvider() {
                // Given: Ollama is configured but not ready, Gemini is ready
                given(aiProperties.provider()).willReturn(AiProvider.OLLAMA);
                given(ollamaClient.isReady()).willReturn(false);
                given(geminiClient.isReady()).willReturn(true);
                given(geminiClient.modelName()).willReturn("gemini");

                aiRouter = new AiRouter(aiModelClients, aiProperties);

                // When
                AiModelClient result = aiRouter.active();

                // Then
                assertThat(result).isSameAs(geminiClient);
                // Note: ollamaClient.isReady() may be called multiple times (initial check + stream filter)
                verify(geminiClient).isReady();
                verify(geminiClient).modelName();
            }
        }

        @Nested
        @DisplayName("with no client for configured provider")
        class WithNoClientForConfiguredProvider {

            @BeforeEach
            void setUp() {
                // Given: Configured provider has no client implementation
                given(aiProperties.provider()).willReturn(AiProvider.OLLAMA);

                // Empty map - no clients registered
                aiRouter = new AiRouter(new HashMap<>(), aiProperties);
            }

            @Test
            @DisplayName("should throw IllegalStateException with descriptive message")
            void shouldThrowIllegalStateException() {
                // When & Then
                assertThatThrownBy(() -> aiRouter.active())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No AI client registered for provider: OLLAMA");
            }
        }

        @Nested
        @DisplayName("with no ready fallback available")
        class WithNoReadyFallbackAvailable {

            @BeforeEach
            void setUp() {
                // Given: All clients exist but none are ready
                given(aiProperties.provider()).willReturn(AiProvider.OLLAMA);
                given(ollamaClient.isReady()).willReturn(false);
                given(geminiClient.isReady()).willReturn(false);

                aiRouter = new AiRouter(aiModelClients, aiProperties);
            }

            @Test
            @DisplayName("should throw IllegalStateException indicating no fallback")
            void shouldThrowIllegalStateExceptionNoFallback() {
                // When & Then
                assertThatThrownBy(() -> aiRouter.active())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No AI client is ready for provider")
                    .hasMessageContaining("OLLAMA")
                    .hasMessageContaining("no fallback available");
            }
        }

        @Nested
        @DisplayName("edge cases")
        class EdgeCases {

            @Test
            @DisplayName("should handle null provider gracefully")
            void shouldHandleNullProviderGracefully() {
                // Given: Provider is null (should not happen but defensive programming)
                given(aiProperties.provider()).willReturn(null);
                aiRouter = new AiRouter(aiModelClients, aiProperties);

                // When & Then
                assertThatThrownBy(() -> aiRouter.active())
                    .isInstanceOf(IllegalStateException.class);
            }

            @Test
            @DisplayName("should handle empty client map")
            void shouldHandleEmptyClientMap() {
                // Given: No clients registered at all
                given(aiProperties.provider()).willReturn(AiProvider.GEMINI);
                aiRouter = new AiRouter(new HashMap<>(), aiProperties);

                // When & Then
                assertThatThrownBy(() -> aiRouter.active())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No AI client registered for provider: GEMINI");
            }

            @Test
            @DisplayName("should select available fallback when configured provider not ready")
            void shouldSelectAvailableFallbackWhenConfiguredProviderNotReady() {
                // Given: Configured provider not ready, another provider ready
                given(aiProperties.provider()).willReturn(AiProvider.OLLAMA);
                given(ollamaClient.isReady()).willReturn(false);
                given(geminiClient.isReady()).willReturn(true);
                given(geminiClient.modelName()).willReturn("gemini");

                aiRouter = new AiRouter(aiModelClients, aiProperties);

                // When
                AiModelClient result = aiRouter.active();

                // Then: Should return ready fallback client
                assertThat(result).isSameAs(geminiClient);
            }
        }
    }

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should accept valid inputs")
        void shouldAcceptValidInputs() {
            // Given
            given(aiProperties.provider()).willReturn(AiProvider.OLLAMA);

            // When
            AiRouter router = new AiRouter(aiModelClients, aiProperties);

            // Then
            assertThat(router).isNotNull();
        }

        @Test
        @DisplayName("should handle null client map")
        void shouldHandleNullClientMap() {
            // Given
            given(aiProperties.provider()).willReturn(AiProvider.OLLAMA);

            // When & Then: Should not throw during construction
            AiRouter router = new AiRouter(null, aiProperties);
            assertThat(router).isNotNull();

            // But should throw when trying to activate
            assertThatThrownBy(router::active)
                .isInstanceOf(NullPointerException.class);
        }
    }
}