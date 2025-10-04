package com.code.agent.infra.ai.adapter;

import com.code.agent.infra.ai.config.AiClientProperties;
import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OllamaAiClient}.
 * Tests cover:
 * - Normal operation with configured client
 * - Configuration property handling (maxTokens)
 * - Edge cases and defaults
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OllamaAiClient")
class OllamaAiClientTest {

    @Mock
    OllamaChatModel mockOllamaChatModel;

    @Mock
    ChatClient mockChatClient;

    @Mock
    ChatClient.ChatClientRequestSpec mockChatClientRequestSpec;

    @Mock
    ChatClient.StreamResponseSpec mockStreamResponseSpec;

    @Mock
    AiClientProperties mockAiClientProperties;

    @Captor
    ArgumentCaptor<Prompt> promptCaptor;

    OllamaAiClient ollamaAiClient;
    AiProperties aiProperties;

    @BeforeEach
    void setUp() {
        String codeReviewTemplate = """
            {diff}
            """;
        String mergeTemplate = """
            {merge}
            """;

        AiProperties.Prompt prompt = new AiProperties.Prompt(
                new ByteArrayResource(codeReviewTemplate.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayResource(mergeTemplate.getBytes(StandardCharsets.UTF_8)));
        aiProperties = new AiProperties(AiProvider.OLLAMA, Map.of(AiProvider.OLLAMA, prompt));
    }

    @Nested
    @DisplayName("when Ollama is configured")
    class WhenOllamaIsConfigured {

        @BeforeEach
        void setUp() {
            try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
                mockedStatic.when(() -> ChatClient.create(mockOllamaChatModel)).thenReturn(mockChatClient);

                // Setup default configuration
                when(mockAiClientProperties.ollama()).thenReturn(
                    new AiClientProperties.Ollama(null, null, null)  // Will use defaults
                );

                ollamaAiClient = new OllamaAiClient(mockOllamaChatModel, aiProperties, mockAiClientProperties);
            }
        }

        @Test
        @DisplayName("should have correct metadata with default maxTokens")
        void shouldHaveCorrectMetadata() {
            assertThat(ollamaAiClient.maxTokens()).isEqualTo(7680);
            assertThat(ollamaAiClient.isReady()).isTrue();
            assertThat(ollamaAiClient.provider()).isEqualTo(AiProvider.OLLAMA);
            assertThat(ollamaAiClient.modelName()).isEqualTo("ollama");
        }

        @Nested
        @DisplayName("review operations")
        class ReviewOperations {
            @BeforeEach
            void setUp() {
                when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockChatClientRequestSpec);
                when(mockChatClientRequestSpec.stream()).thenReturn(mockStreamResponseSpec);
                when(mockStreamResponseSpec.content()).thenReturn(Flux.just("Hello", " ", "Ollama"));
            }

            @Test
            @DisplayName("should review code and join stream")
            void shouldReviewCodeAndJoinStream() {
                String diff = "some diff content";

                StepVerifier.create(ollamaAiClient.reviewCode(diff))
                        .expectNext("Hello Ollama")
                        .verifyComplete();

                verify(mockChatClient).prompt(promptCaptor.capture());
                assertThat(promptCaptor.getValue().getContents()).contains(diff);
            }

            @Test
            @DisplayName("should review merge and join stream")
            void shouldReviewMergeAndJoinStream() {
                String merged = "--- Next Review ---";

                StepVerifier.create(ollamaAiClient.reviewMerge(merged))
                        .expectNext("Hello Ollama")
                        .verifyComplete();

                verify(mockChatClient).prompt(promptCaptor.capture());
                assertThat(promptCaptor.getValue().getContents()).contains(merged);
            }
        }
    }

    @Nested
    @DisplayName("when configuring maxTokens")
    class WhenConfiguringMaxTokens {

        @Test
        @DisplayName("should use custom maxTokens value")
        void shouldUseCustomMaxTokens() {
            try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
                mockedStatic.when(() -> ChatClient.create(mockOllamaChatModel)).thenReturn(mockChatClient);

                // Custom maxTokens
                when(mockAiClientProperties.ollama()).thenReturn(
                    new AiClientProperties.Ollama(null, null, 16_000)
                );

                ollamaAiClient = new OllamaAiClient(mockOllamaChatModel, aiProperties, mockAiClientProperties);

                assertThat(ollamaAiClient.maxTokens()).isEqualTo(16_000);
            }
        }

        @Test
        @DisplayName("should use default when ollama config is null")
        void shouldUseDefaultWhenOllamaConfigIsNull() {
            try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
                mockedStatic.when(() -> ChatClient.create(mockOllamaChatModel)).thenReturn(mockChatClient);

                // Null ollama config
                when(mockAiClientProperties.ollama()).thenReturn(null);

                ollamaAiClient = new OllamaAiClient(mockOllamaChatModel, aiProperties, mockAiClientProperties);

                assertThat(ollamaAiClient.maxTokens()).isEqualTo(7680);
            }
        }

        @Test
        @DisplayName("should use default when maxTokens is null")
        void shouldUseDefaultWhenMaxTokensIsNull() {
            try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
                mockedStatic.when(() -> ChatClient.create(mockOllamaChatModel)).thenReturn(mockChatClient);

                // maxTokens is null
                when(mockAiClientProperties.ollama()).thenReturn(
                    new AiClientProperties.Ollama(null, null, null)
                );

                ollamaAiClient = new OllamaAiClient(mockOllamaChatModel, aiProperties, mockAiClientProperties);

                assertThat(ollamaAiClient.maxTokens()).isEqualTo(7680);
            }
        }
    }
}