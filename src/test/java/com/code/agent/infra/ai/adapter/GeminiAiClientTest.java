package com.code.agent.infra.ai.adapter;

import com.code.agent.infra.ai.config.AiProperties;
import com.code.agent.infra.ai.model.AiProvider;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeminiAiClientTest {

    @Mock
    VertexAiGeminiChatModel mockGeminiChatModel;
    @Mock
    ChatClient mockChatClient;
    @Mock
    ChatClient.ChatClientRequestSpec mockChatClientRequestSpec;
    @Mock
    ChatClient.StreamResponseSpec mockStreamResponseSpec;

    @Captor
    ArgumentCaptor<Prompt> promptCaptor;

    GeminiAiClient geminiAiClient;

    @BeforeEach
    void setUp() {
        try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
            mockedStatic.when(() -> ChatClient.create(mockGeminiChatModel)).thenReturn(mockChatClient);


            String codeReviewTemplate = """
                {diff}
                """;
            String mergeTemplate = """
                {merge}
                """;

            AiProperties.Prompt prompt = new AiProperties.Prompt(
                    new ByteArrayResource(codeReviewTemplate.getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayResource(mergeTemplate.getBytes(StandardCharsets.UTF_8)));
            AiProperties aiProperties = new AiProperties(AiProvider.GEMINI, Map.of(AiProvider.GEMINI, prompt));


            geminiAiClient = new GeminiAiClient(mockGeminiChatModel, aiProperties);
        }
    }

    @Test
    void metadata() {
        assertThat(geminiAiClient.maxTokens()).isEqualTo(100_000);
        assertThat(geminiAiClient.requestTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(geminiAiClient.isReady()).isTrue();
        assertThat(geminiAiClient.provider()).isEqualTo(AiProvider.GEMINI);
        assertThat(geminiAiClient.modelName()).isEqualTo("gemini");
    }

    @Nested
    class Review {
        @BeforeEach
        void setUp() {
            when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockChatClientRequestSpec);
            when(mockChatClientRequestSpec.stream()).thenReturn(mockStreamResponseSpec);
            when(mockStreamResponseSpec.content()).thenReturn(Flux.just("Hello", " ", "Gemini"));
        }

        @Test
        void reviewCodeJoinsStream() {
            String diff = "some diff content";

            StepVerifier.create(geminiAiClient.reviewCode(diff))
                    .expectNext("Hello Gemini")
                    .verifyComplete();

            verify(mockChatClient).prompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue().getContents()).contains(diff);
        }

        @Test
        void reviewMergeJoinsStream() {
            String merged = "--- Next Review ---";

            StepVerifier.create(geminiAiClient.reviewMerge(merged))
                    .expectNext("Hello Gemini")
                    .verifyComplete();

            verify(mockChatClient).prompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue().getContents()).contains(merged);
        }


    }
}