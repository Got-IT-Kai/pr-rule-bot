package com.code.agent.infra.ai.adapter;

import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.ai.config.AiProperties;
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
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaAiClientTest {

    @Mock
    OllamaChatModel mockOllamaChatModel;

    @Mock
    ChatClient mockChatClient;

    @Mock
    ChatClient.ChatClientRequestSpec mockChatClientRequestSpec;

    @Mock
    ChatClient.StreamResponseSpec mockStreamResponseSpec;

    @Captor
    ArgumentCaptor<Prompt> promptCaptor;

    OllamaAiClient ollamaAiClient;

    @BeforeEach
    void setUp() {
        try (MockedStatic<ChatClient> mockedStatic = mockStatic(ChatClient.class)) {
            mockedStatic.when(() -> ChatClient.create(mockOllamaChatModel)).thenReturn(mockChatClient);


            String codeReviewTemplate = """
                {diff}
                """;
            String mergeTemplate = """
                {merge}
                """;

            AiProperties.Prompt prompt = new AiProperties.Prompt(
                    new ByteArrayResource(codeReviewTemplate.getBytes(StandardCharsets.UTF_8)),
                    new ByteArrayResource(mergeTemplate.getBytes(StandardCharsets.UTF_8)));
            AiProperties aiProperties = new AiProperties(AiProvider.OLLAMA, Map.of(AiProvider.OLLAMA, prompt));


            ollamaAiClient = new OllamaAiClient(mockOllamaChatModel, aiProperties);
        }
    }

    @Test
    void metadata() {
        assertThat(ollamaAiClient.maxTokens()).isEqualTo(7680);
        assertThat(ollamaAiClient.requestTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(ollamaAiClient.isReady()).isTrue();
        assertThat(ollamaAiClient.provider()).isEqualTo(AiProvider.OLLAMA);
        assertThat(ollamaAiClient.modelName()).isEqualTo("ollama");
    }

    @Nested
    class Review {
        @BeforeEach
        void setUp() {
            when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockChatClientRequestSpec);
            when(mockChatClientRequestSpec.stream()).thenReturn(mockStreamResponseSpec);
            when(mockStreamResponseSpec.content()).thenReturn(Flux.just("Hello", " ", "Ollama"));
        }

        @Test
        void reviewCodeJoinsStream() {
            String diff = "some diff content";

            StepVerifier.create(ollamaAiClient.reviewCode(diff))
                    .expectNext("Hello Ollama")
                    .verifyComplete();

            verify(mockChatClient).prompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue().getContents()).contains(diff);
        }

        @Test
        void reviewMergeJoinsStream() {
            String merged = "--- Next Review ---";

            StepVerifier.create(ollamaAiClient.reviewMerge(merged))
                    .expectNext("Hello Ollama")
                    .verifyComplete();

            verify(mockChatClient).prompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue().getContents()).contains(merged);
        }

    }

}