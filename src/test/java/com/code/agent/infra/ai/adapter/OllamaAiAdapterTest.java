package com.code.agent.infra.ai.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ChatClient.CallResponseSpec mockCallResponseSpec;

    @Captor
    ArgumentCaptor<String> promptCaptor;


    private OllamaAiAdapter ollamaAiAdapter;

    @BeforeEach
    void setUp() {
        when(mockChatClientBuilder.build()).thenReturn(mockChatClient);
        ollamaAiAdapter = new OllamaAiAdapter(mockChatClientBuilder);
    }

    static Stream<Arguments> diffProvider() {
        return Stream.of(
                Arguments.of("""
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
                """, "This is a mock comment based on the diff provided."),
                Arguments.of("""
                diff --git a/src/main/java/com/example/App.java b/src/main/java/com/example/App.java\r\n" +
                "--- a/src/main/java/com/example/App.java\r\n" +
                "+++ b/src/main/java/com/example/App.java\r\n" +
                "@@ -1 +1 @@\r\n" +
                "-old\r\n" +
                "+new\r\n" +
                "diff --git a/src/main/java/com/example/Util.java b/src/main/java/com/example/Util.java\r\n" +
                "--- a/src/main/java/com/example/Util.java\r\n" +
                "+++ b/src/main/java/com/example/Util.java\r\n" +
                "@@ -2 +2 @@\r\n" +
                "-foo\r\n" +
                "+bar\r\n";""", "This is a mock comment based on the diff provided.")
        );
    }
    @ParameterizedTest
    @MethodSource("diffProvider")
    void testEvaluateDiff(String diff, String expected) {
        when(mockChatClient.prompt()).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.user(anyString())).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn("This is a mock comment based on the diff provided.");

        String actual = ollamaAiAdapter.evaluateDiff(diff);

        assertThat(actual).isEqualTo(expected);
    }


    @Test
    void blankDiff_returns() {
        String out = ollamaAiAdapter.evaluateDiff("  ");
        assertThat(out).isEqualTo("No changes to review.");
        verifyNoInteractions(mockChatClient);
    }

    static Stream<String> singleDiffs() {
        String unix = "diff --git a/src/main/java/com/example/service/UserService.java b/src/main/java/com/example/service/UserService.java\n" +
                "--- a/src/main/java/com/example/service/UserService.java\n" +
                "+++ b/src/main/java/com/example/service/UserService.java\n" +
                "@@ -10,1 +10,1 @@\n" +
                "-    // old code\n" +
                "+    // new code for user\n";
        String windows = unix.replace("\n", "\r\n");
        return Stream.of(unix, windows);
    }

    @ParameterizedTest
    @MethodSource("singleDiffs")
    void singleDiff_os_independent(String diff) {
        when(mockChatClient.prompt()).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.user(anyString())).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn("This is a mock comment based on the diff provided.");

        String out = ollamaAiAdapter.evaluateDiff(diff);
        assertThat(out).isEqualTo("This is a mock comment based on the diff provided.");

        verify(mockChatClientRequestSpec).user(promptCaptor.capture());
        String expectedPrompt = promptCaptor.getValue();

        assertThat(expectedPrompt).contains(diff);
    }


    @Test
    void multipleDiffs() {
        String diff = """
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

        when(mockChatClient.prompt()).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.user(anyString())).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn("This is a mock comment based on the diff provided.");

        String out = ollamaAiAdapter.evaluateDiff(diff);
        assertThat(out).isEqualTo("This is a mock comment based on the diff provided.");

        verify(mockChatClientRequestSpec, times(3)).user(promptCaptor.capture());
        List<String> prompts = promptCaptor.getAllValues();

        assertThat(prompts.get(0)).contains("UserService.java").doesNotContain("OrderService.java");
        assertThat(prompts.get(1)).contains("OrderService.java").doesNotContain("UserService.java");
        assertThat(prompts.get(2)).contains("Individual Code Reviews");
    }


}