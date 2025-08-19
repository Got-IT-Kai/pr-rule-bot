package com.code.agent.infra.ai.adapter;

import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.config.AiProperties;
import com.knuddels.jtokkit.api.Encoding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaAiAdapterTest {

    @Mock
    private ChatClient.Builder mockChatClientBuilder;

    @Mock
    private ChatClient mockChatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec mockChatClientRequestSpec;

    @Mock
    private ChatClient.StreamResponseSpec mockStreamResponseSpec;

    @Mock
    private Encoding encoding;

    @Captor
    ArgumentCaptor<Prompt> promptCaptor;

    private OllamaAiAdapter ollamaAiAdapter;

    final String diff = """
                diff --git a/src/main/java/com/example/service/UserService.java b/src/main/java/com/example/service/UserService.java
                --- a/src/main/java/com/example/service/UserService.java
                +++ b/src/main/java/com/example/service/UserService.java
                @@ -10,1 +10,1 @@
                -    // old code
                +    // new code for user
                diff --git a/src/main/java/com/example/service/OrderService.java b/src/main/java/com/example/service/OrderService.java
                --- a/src/main/java/com/example/service/OrderService.java
                +++ b/src/main/java/com/example/service/OrderService.java
                @@ -25,1 +25,1 @@
                -    return "order";
                +    return "new order";
                """;

    @BeforeEach
    void setUp() {
        when(mockChatClientBuilder.build()).thenReturn(mockChatClient);

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

        ollamaAiAdapter = new OllamaAiAdapter(mockChatClientBuilder, encoding, aiProperties);
    }

    private void setUpPromptChain() {
        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.stream()).thenReturn(mockStreamResponseSpec);
        when(mockStreamResponseSpec.content()).thenReturn(Flux.just("OK"));

        when(encoding.countTokens(anyString())).thenReturn(50);
    }

    @Test
    void blankDiff_returns() {
        StepVerifier.create(ollamaAiAdapter.evaluateDiff(""))
                .expectNext("No changes to review.")
                .verifyComplete();
        verifyNoInteractions(mockChatClient);
    }

    @Nested
    class SingleFile {

        @BeforeEach
        void setUp() {
            setUpPromptChain();
        }

        static Stream<String> singleDiffs() {
            String unix = """
                    diff --git a/src/main/java/com/example/service/UserService.java b/src/main/java/com/example/service/UserService.java
                    --- a/src/main/java/com/example/service/UserService.java
                    +++ b/src/main/java/com/example/service/UserService.java
                    @@ -10,1 +10,1 @@
                    -    // old code
                    +    // new code for user
                    """;
            String windows = unix.replace("\n", "\r\n");
            return Stream.of(unix, windows);
        }

        @ParameterizedTest
        @MethodSource("singleDiffs")
        void singleFile_os_independent(String diff) {
            when(mockStreamResponseSpec.content())
                    .thenReturn(Flux.just("Start"))
                    .thenReturn(Flux.just("OK"));

            StepVerifier.create(ollamaAiAdapter.evaluateDiff(diff))
                    .expectNext("OK")
                    .verifyComplete();

            verify(mockChatClient, times(2)).prompt(any(Prompt.class));
        }

    }

    @Nested
    class MultipleFiles {

        @BeforeEach
        void setUp() {
            setUpPromptChain();
        }

        @Test
        void multiFile_merge() {
            when(mockStreamResponseSpec.content())
                    .thenReturn(Flux.just("OK1"))
                    .thenReturn(Flux.just("OK2"))
                    .thenReturn(Flux.just("Merged reviews"));

            StepVerifier.create(ollamaAiAdapter.evaluateDiff(diff))
                    .expectNext("Merged reviews")
                    .verifyComplete();

            verify(mockChatClient, times(3)).prompt(promptCaptor.capture());

            Prompt prompt = promptCaptor.getAllValues().get(2);

            assertThat(prompt.getContents()).contains("OK1", "OK2");

        }

        @Test
        void multiFilePrompt() {
            when(mockStreamResponseSpec.content())
                    .thenReturn(Flux.just("OK1"))
                    .thenReturn(Flux.just("OK2"))
                    .thenReturn(Flux.just("Merged reviews"));

            StepVerifier.create(ollamaAiAdapter.evaluateDiff(diff))
                    .expectNext("Merged reviews")
                    .verifyComplete();

            verify(mockChatClient, times(3)).prompt(any(Prompt.class));

        }
    }

    @Nested
    class TokenGuard {

        @Test
        void overLimitTokenCount_one() {
            when(encoding.countTokens(anyString())).thenReturn(10000);

            StepVerifier.create(ollamaAiAdapter.evaluateDiff("HUGE"))
                    .expectNext("All files exceed the 7680‑token limit")
                    .verifyComplete();

            verify(mockChatClient, never()).prompt(any(Prompt.class));
        }

        @Test
        void overLimitTokenCount_multiple() {
            setUpPromptChain();

            when(encoding.countTokens(anyString())).thenReturn(5000)
                    .thenReturn(10000)
                    .thenReturn(5000);

            when(mockStreamResponseSpec.content())
                    .thenReturn(Flux.just("SMALL"))
                    .thenReturn(Flux.just("MERGED"));

            StepVerifier.create(ollamaAiAdapter.evaluateDiff(diff))
                    .expectNextMatches(result -> result.contains("MERGED")
                            && result.contains("1 files were not reviewed"))
                    .verifyComplete();

            verify(mockChatClient, times(2)).prompt(any(Prompt.class));
        }

        @Test
        void overLimitTokenCount_merge() {
            setUpPromptChain();

            when(encoding.countTokens(anyString())).thenReturn(5000)
                    .thenReturn(5000)
                    .thenReturn(10000);

            StepVerifier.create(ollamaAiAdapter.evaluateDiff(diff))
                    .expectNext("Combined reviews exceed the 7680‑token limit")
                    .verifyComplete();

            verify(mockChatClient, times(2)).prompt(any(Prompt.class));
        }
    }
}