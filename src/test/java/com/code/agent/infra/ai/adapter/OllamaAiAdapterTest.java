package com.code.agent.infra.ai.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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


    private OllamaAiAdapter ollamaAiAdapter;

    @BeforeEach
    void setUp() {
        when(mockChatClientBuilder.build()).thenReturn(mockChatClient);
        when(mockChatClient.prompt()).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.user(anyString())).thenReturn(mockChatClientRequestSpec);
        when(mockChatClientRequestSpec.call()).thenReturn(mockCallResponseSpec);
        when(mockCallResponseSpec.content()).thenReturn("This is a mock comment based on the diff provided.");
        ollamaAiAdapter = new OllamaAiAdapter(mockChatClientBuilder);
    }

    @Test
    void testEvaluateDiff() {
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
        String expect = "This is a mock comment based on the diff provided.";

        String actual = ollamaAiAdapter.evaluateDiff(diff);

        assertThat(actual).isEqualTo(expect);
    }


}