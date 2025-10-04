package com.code.agent.infra.ai.adapter;

import com.code.agent.infra.ai.config.AiClientProperties;
import com.code.agent.infra.ai.config.AiProperties;
import com.code.agent.infra.ai.model.AiProvider;
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
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link GeminiAiClient}.
 * Tests cover:
 * - Normal operation with configured client
 * - Null safety when Gemini is not configured
 * - Configuration property handling
 * - Edge cases and error scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeminiAiClient")
class GeminiAiClientTest {

    @Mock
    VertexAiGeminiChatModel mockGeminiChatModel;
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

    GeminiAiClient geminiAiClient;
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
        aiProperties = new AiProperties(AiProvider.GEMINI, Map.of(AiProvider.GEMINI, prompt));
    }

    @Nested
    @DisplayName("when Gemini is configured")
    class WhenGeminiIsConfigured {

        @BeforeEach
        void setUp() {
            try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
                mockedStatic.when(() -> ChatClient.create(mockGeminiChatModel)).thenReturn(mockChatClient);

                // Setup default configuration
                when(mockAiClientProperties.gemini()).thenReturn(
                    new AiClientProperties.Gemini(null)  // Will use default
                );

                geminiAiClient = new GeminiAiClient(mockGeminiChatModel, aiProperties, mockAiClientProperties);
            }
        }

        @Test
        @DisplayName("should have correct metadata with default maxTokens")
        void shouldHaveCorrectMetadata() {
            assertThat(geminiAiClient.maxTokens()).isEqualTo(100_000);
            assertThat(geminiAiClient.isReady()).isTrue();
            assertThat(geminiAiClient.provider()).isEqualTo(AiProvider.GEMINI);
            assertThat(geminiAiClient.modelName()).isEqualTo("gemini");
        }

        @Nested
        @DisplayName("review operations")
        class ReviewOperations {
            @BeforeEach
            void setUp() {
                when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockChatClientRequestSpec);
                when(mockChatClientRequestSpec.stream()).thenReturn(mockStreamResponseSpec);
                when(mockStreamResponseSpec.content()).thenReturn(Flux.just("Hello", " ", "Gemini"));
            }

            @Test
            @DisplayName("should review code and join stream")
            void shouldReviewCodeAndJoinStream() {
                String diff = "some diff content";

                StepVerifier.create(geminiAiClient.reviewCode(diff))
                        .expectNext("Hello Gemini")
                        .verifyComplete();

                verify(mockChatClient).prompt(promptCaptor.capture());
                assertThat(promptCaptor.getValue().getContents()).contains(diff);
            }

            @Test
            @DisplayName("should review merge and join stream")
            void shouldReviewMergeAndJoinStream() {
                String merged = "--- Next Review ---";

                StepVerifier.create(geminiAiClient.reviewMerge(merged))
                        .expectNext("Hello Gemini")
                        .verifyComplete();

                verify(mockChatClient).prompt(promptCaptor.capture());
                assertThat(promptCaptor.getValue().getContents()).contains(merged);
            }
        }
    }

    @Nested
    @DisplayName("when Gemini is not configured")
    class WhenGeminiIsNotConfigured {

        @BeforeEach
        void setUp() {
            // Gemini model is null - not configured
            when(mockAiClientProperties.gemini()).thenReturn(
                new AiClientProperties.Gemini(null)
            );

            geminiAiClient = new GeminiAiClient(null, aiProperties, mockAiClientProperties);
        }

        @Test
        @DisplayName("should indicate client is not ready")
        void shouldIndicateClientIsNotReady() {
            assertThat(geminiAiClient.isReady()).isFalse();
        }

        @Test
        @DisplayName("should return error when reviewing code")
        void shouldReturnErrorWhenReviewingCode() {
            String diff = "some diff content";

            StepVerifier.create(geminiAiClient.reviewCode(diff))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("should return error when reviewing merge")
        void shouldReturnErrorWhenReviewingMerge() {
            String merged = "--- Next Review ---";

            StepVerifier.create(geminiAiClient.reviewMerge(merged))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("should still return metadata correctly")
        void shouldStillReturnMetadata() {
            assertThat(geminiAiClient.maxTokens()).isEqualTo(100_000);
            assertThat(geminiAiClient.provider()).isEqualTo(AiProvider.GEMINI);
            assertThat(geminiAiClient.modelName()).isEqualTo("gemini");
        }
    }

    @Nested
    @DisplayName("when configuring maxTokens")
    class WhenConfiguringMaxTokens {

        @Test
        @DisplayName("should use custom maxTokens value")
        void shouldUseCustomMaxTokens() {
            try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
                mockedStatic.when(() -> ChatClient.create(mockGeminiChatModel)).thenReturn(mockChatClient);

                // Custom maxTokens
                when(mockAiClientProperties.gemini()).thenReturn(
                    new AiClientProperties.Gemini(200_000)
                );

                geminiAiClient = new GeminiAiClient(mockGeminiChatModel, aiProperties, mockAiClientProperties);

                assertThat(geminiAiClient.maxTokens()).isEqualTo(200_000);
            }
        }

        @Test
        @DisplayName("should use default when gemini config is null")
        void shouldUseDefaultWhenGeminiConfigIsNull() {
            try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
                mockedStatic.when(() -> ChatClient.create(mockGeminiChatModel)).thenReturn(mockChatClient);

                // Null gemini config
                when(mockAiClientProperties.gemini()).thenReturn(null);

                geminiAiClient = new GeminiAiClient(mockGeminiChatModel, aiProperties, mockAiClientProperties);

                assertThat(geminiAiClient.maxTokens()).isEqualTo(100_000);
            }
        }

        @Test
        @DisplayName("should use default when maxTokens is null")
        void shouldUseDefaultWhenMaxTokensIsNull() {
            try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
                mockedStatic.when(() -> ChatClient.create(mockGeminiChatModel)).thenReturn(mockChatClient);

                // maxTokens is null
                when(mockAiClientProperties.gemini()).thenReturn(
                    new AiClientProperties.Gemini(null)
                );

                geminiAiClient = new GeminiAiClient(mockGeminiChatModel, aiProperties, mockAiClientProperties);

                assertThat(geminiAiClient.maxTokens()).isEqualTo(100_000);
            }
        }
    }
}