package com.code.agent.infra.ai.service;

import com.code.agent.infra.ai.router.AiRouter;
import com.code.agent.infra.ai.spi.AiModelClient;
import com.knuddels.jtokkit.api.Encoding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeReviewServiceTest {

    @Mock
    AiModelClient client;

    @Mock
    AiRouter aiRouter;

    @Mock
    Encoding encoding;

    CodeReviewService codeReviewService;

    static final int MAX_TOKENS = 7680;

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
        codeReviewService = new CodeReviewService(aiRouter, encoding);
    }

    @Test
    void blankDiff() {
        StepVerifier.create(codeReviewService.evaluateDiff(""))
                .expectNext("No changes to review.")
                .verifyComplete();

        verify(aiRouter, never()).active();
        verifyNoInteractions(client);
    }


    @Nested
    class Files {

        @BeforeEach
        void setUp() {
            when(aiRouter.active()).thenReturn(client);
            when(client.maxTokens()).thenReturn(MAX_TOKENS);
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
            when(encoding.countTokens(anyString())).thenReturn(50);

            when(client.reviewCode(anyString())).thenReturn(Mono.just("OK"));
            when(client.reviewMerge(anyString())).thenReturn(Mono.just("OK"));

            StepVerifier.create(codeReviewService.evaluateDiff(diff))
                    .expectNext("OK")
                    .verifyComplete();

            verify(aiRouter, times(1)).active();
            verify(client, times(1)).reviewCode(anyString());
            verify(client, times(1)).reviewMerge("OK");
        }


        @Test
        void multiFile_merge() {
            when(encoding.countTokens(anyString())).thenReturn(50);
            when(client.reviewCode(anyString()))
                    .thenReturn(Mono.just("OK1"))
                    .thenReturn(Mono.just("OK2"));

            when(client.reviewMerge(anyString())).thenReturn(Mono.just("Merged reviews"));

            StepVerifier.create(codeReviewService.evaluateDiff(diff))
                    .expectNext("Merged reviews")
                    .verifyComplete();

            ArgumentCaptor<String> mergedCaptor = ArgumentCaptor.forClass(String.class);
            verify(client).reviewMerge(mergedCaptor.capture());
            assertThat(mergedCaptor.getValue())
                    .contains("OK1")
                    .contains("OK2")
                    .contains("--- Next Review ---");
        }
    }

    @Nested
    class TokenGuard {

        @BeforeEach
        void setUp() {
            when(aiRouter.active()).thenReturn(client);
            when(client.maxTokens()).thenReturn(MAX_TOKENS);
        }

        @Test
        void overLimitTokenCount_one() {
            when(encoding.countTokens(anyString())).thenReturn(10000);

            StepVerifier.create(codeReviewService.evaluateDiff("HUGE"))
                    .expectNext("All files exceed the 7680‑token limit")
                    .verifyComplete();

            verify(client, never()).reviewCode(anyString());
            verify(client, never()).reviewMerge(anyString());
        }

        @Test
        void overLimitTokenCount_multiple() {
            when(encoding.countTokens(anyString())).thenReturn(5000, 10000, 5000);

            when(client.reviewCode(anyString())).thenReturn(Mono.just("SMALL"));
            when(client.reviewMerge("SMALL")).thenReturn(Mono.just("MERGED"));

            StepVerifier.create(codeReviewService.evaluateDiff(diff))
                    .expectNextMatches(result ->
                            result.contains("MERGED")
                                    && result.contains("1 files were not reviewed")
                                    && result.contains("7680-token"))
                    .verifyComplete();

            verify(client, times(1)).reviewCode(anyString());
            verify(client, times(1)).reviewMerge("SMALL");
        }

        @Test
        void overLimitTokenCount_merge() {
            when(encoding.countTokens(anyString())).thenReturn(5000, 5000, 10000);
            when(client.reviewCode(anyString())).thenReturn(Mono.just("SMALL"));


            StepVerifier.create(codeReviewService.evaluateDiff(diff))
                    .expectNext("Combined reviews exceed the 7680‑token limit")
                    .verifyComplete();

            verify(client, times(2)).reviewCode(anyString());
            verify(client, never()).reviewMerge(anyString());
        }
    }
}