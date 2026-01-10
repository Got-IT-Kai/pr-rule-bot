package com.code.review.application.service;

import com.code.platform.metrics.MetricsHelper;
import com.code.review.application.port.outbound.AiModelPort;
import com.code.review.application.port.outbound.EventPublisher;
import com.code.review.domain.model.*;
import com.code.review.infrastructure.config.ReactorProperties;
import com.knuddels.jtokkit.api.Encoding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewServiceImpl")
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ReviewServiceImplTest {

    @Mock
    AiModelPort aiModelPort;

    @Mock
    EventPublisher eventPublisher;

    @Mock
    Encoding encoding;

    @Mock
    MetricsHelper metricsHelper;

    @Captor
    ArgumentCaptor<String> mergeReviewsCaptor;

    ReactorProperties reactorProperties = new ReactorProperties(10, 1);

    ReviewServiceImpl reviewService;

    static final int MAX_TOKENS = 7680;
    static final String CONTEXT_ID = "ctx-123";
    static final String REPO_OWNER = "testowner";
    static final String REPO_NAME = "testrepo";
    static final Integer PR_NUMBER = 42;
    static final String PR_TITLE = "fix: critical bug";
    static final String CORRELATION_ID = "corr-123";

    final String multiFileDiff = """
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
        reviewService = new ReviewServiceImpl(aiModelPort, eventPublisher, encoding, reactorProperties, metricsHelper);
        lenient().when(eventPublisher.publish(any(com.code.events.review.ReviewStartedEvent.class))).thenReturn(Mono.empty());
        lenient().when(eventPublisher.publish(any(com.code.events.review.ReviewCompletedEvent.class))).thenReturn(Mono.empty());
        lenient().when(eventPublisher.publish(any(com.code.events.review.ReviewFailedEvent.class))).thenReturn(Mono.empty());
        when(aiModelPort.providerName()).thenReturn("test-provider");
        when(aiModelPort.modelName()).thenReturn("test-model");
    }

    @Test
    @DisplayName("should handle blank diff")
    void shouldHandleBlankDiff() {
        when(aiModelPort.maxTokens()).thenReturn(MAX_TOKENS);

        StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, "", CORRELATION_ID))
                .expectNextMatches(result ->
                        result.status() == ReviewStatus.COMPLETED &&
                        result.reviewComment().equals("No changes to review."))
                .verifyComplete();

        verify(aiModelPort, never()).reviewCode(anyString(), any());
    }

    @Nested
    @DisplayName("when processing files")
    class WhenProcessingFiles {

        @BeforeEach
        void setUp() {
            when(aiModelPort.maxTokens()).thenReturn(MAX_TOKENS);
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
        @DisplayName("should handle single file with different line endings")
        void shouldHandleSingleFileOsIndependent(String diff) {
            when(encoding.countTokens(anyString())).thenReturn(50);
            when(aiModelPort.reviewCode(anyString(), any(PrContext.class))).thenReturn(Mono.just("OK"));
            when(aiModelPort.mergeReviews(anyString(), any(PrContext.class))).thenReturn(Mono.just("OK"));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, diff, CORRELATION_ID))
                    .expectNextMatches(result ->
                            result.status() == ReviewStatus.COMPLETED &&
                            result.reviewComment().equals("OK"))
                    .verifyComplete();

            verify(aiModelPort, times(1)).reviewCode(anyString(), any(PrContext.class));
            ArgumentCaptor<String> mergedCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiModelPort).mergeReviews(mergedCaptor.capture(), eq(PrContext.from(PR_TITLE)));
            assertThat(mergedCaptor.getValue())
                    .contains("[FILE: src/main/java/com/example/service/UserService.java]")
                    .contains("\nOK");
        }

        @Test
        @DisplayName("should merge multiple file reviews")
        void shouldMergeMultipleFileReviews() {
            when(encoding.countTokens(anyString())).thenReturn(50);
            when(aiModelPort.reviewCode(argThat(s -> s != null && s.contains("UserService.java")), any(PrContext.class)))
                    .thenReturn(Mono.just("OK1"));
            when(aiModelPort.reviewCode(argThat(s -> s != null && s.contains("OrderService.java")), any(PrContext.class)))
                    .thenReturn(Mono.just("OK2"));
            when(aiModelPort.mergeReviews(anyString(), any(PrContext.class))).thenReturn(Mono.just("Merged reviews"));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, multiFileDiff, CORRELATION_ID))
                    .expectNextMatches(result ->
                            result.status() == ReviewStatus.COMPLETED &&
                            result.reviewComment().equals("Merged reviews"))
                    .verifyComplete();

            ArgumentCaptor<String> mergedCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiModelPort).mergeReviews(mergedCaptor.capture(), any(PrContext.class));
            assertThat(mergedCaptor.getValue())
                    .contains("[FILE: src/main/java/com/example/service/UserService.java]")
                    .contains("OK1")
                    .contains("[FILE: src/main/java/com/example/service/OrderService.java]")
                    .contains("OK2")
                    .contains("--- Next Review ---");
        }
    }

    @Nested
    @DisplayName("when enforcing token limits")
    class WhenEnforcingTokenLimits {

        @BeforeEach
        void setUp() {
            when(aiModelPort.maxTokens()).thenReturn(MAX_TOKENS);
        }

        @Test
        @DisplayName("should reject all files exceeding token limit")
        void shouldRejectAllFilesExceedingTokenLimit() {
            when(encoding.countTokens(anyString())).thenReturn(10000);

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, "HUGE", CORRELATION_ID))
                    .expectNextMatches(result ->
                            result.status() == ReviewStatus.COMPLETED &&
                            result.reviewComment().contains("All files exceed the 7680-token limit"))
                    .verifyComplete();

            verify(aiModelPort, never()).reviewCode(anyString(), any());
            verify(aiModelPort, never()).mergeReviews(anyString(), any());
        }

        @Test
        @DisplayName("should skip files exceeding token limit")
        void shouldSkipFilesExceedingTokenLimit() {
            when(encoding.countTokens(anyString())).thenAnswer(inv -> {
                String text = inv.getArgument(0, String.class);
                if (text.contains("diff --git") && text.contains("OrderService.java")) {
                    return 10000;
                }
                return 5000;
            });
            when(aiModelPort.reviewCode(anyString(), any(PrContext.class))).thenReturn(Mono.just("SMALL"));
            when(aiModelPort.mergeReviews(anyString(), any(PrContext.class))).thenReturn(Mono.just("MERGED"));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, multiFileDiff, CORRELATION_ID))
                    .expectNextMatches(result ->
                            result.status() == ReviewStatus.COMPLETED &&
                            result.reviewComment().contains("MERGED") &&
                            result.reviewComment().contains("1 files were not reviewed") &&
                            result.reviewComment().contains("7680-token"))
                    .verifyComplete();

            verify(aiModelPort, times(1)).reviewCode(anyString(), any(PrContext.class));
            ArgumentCaptor<String> mergedCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiModelPort).mergeReviews(mergedCaptor.capture(), any(PrContext.class));
            assertThat(mergedCaptor.getValue()).contains("[FILE:");
        }

        @Test
        @DisplayName("should reject merge when combined reviews exceed token limit")
        void shouldRejectMergeWhenCombinedExceedsTokenLimit() {
            when(encoding.countTokens(anyString())).thenAnswer(inv -> {
                String text = inv.getArgument(0, String.class);
                if (text.contains("--- Next Review ---")) {
                    return 10000;
                }
                return 5000;
            });
            when(aiModelPort.reviewCode(anyString(), any(PrContext.class))).thenReturn(Mono.just("SMALL"));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, multiFileDiff, CORRELATION_ID))
                    .expectNextMatches(result ->
                            result.status() == ReviewStatus.COMPLETED &&
                            result.reviewComment().contains("Combined reviews exceed the 7680-token limit"))
                    .verifyComplete();

            verify(aiModelPort, times(2)).reviewCode(anyString(), any(PrContext.class));
            verify(aiModelPort, never()).mergeReviews(anyString(), any());
        }
    }

    @Nested
    @DisplayName("when handling errors")
    class WhenHandlingErrors {

        @BeforeEach
        void setUp() {
            when(aiModelPort.maxTokens()).thenReturn(MAX_TOKENS);
        }

        @Test
        @DisplayName("should handle AI model errors gracefully")
        void shouldHandleAiModelErrorsGracefully() {
            when(encoding.countTokens(anyString())).thenReturn(50);
            when(aiModelPort.reviewCode(anyString(), any(PrContext.class)))
                    .thenReturn(Mono.error(new RuntimeException("AI service unavailable")));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, multiFileDiff, CORRELATION_ID))
                    .expectNextMatches(result ->
                            result.status() == ReviewStatus.FAILED &&
                            result.reviewComment().contains("Review failed") &&
                            result.reviewComment().contains("AI service unavailable"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should continue review when ReviewStartedEvent publish fails")
        void shouldContinueReviewWhenEventPublishFails() {
            when(eventPublisher.publish(any(com.code.events.review.ReviewStartedEvent.class)))
                    .thenReturn(Mono.error(new RuntimeException("Kafka unavailable")));
            when(encoding.countTokens(anyString())).thenReturn(50);
            when(aiModelPort.reviewCode(anyString(), any(PrContext.class))).thenReturn(Mono.just("OK"));
            when(aiModelPort.mergeReviews(anyString(), any(PrContext.class))).thenReturn(Mono.just("OK"));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, multiFileDiff, CORRELATION_ID))
                    .expectNextMatches(result ->
                            result.status() == ReviewStatus.COMPLETED &&
                            result.reviewComment().equals("OK"))
                    .verifyComplete();

            verify(eventPublisher).publish(any(com.code.events.review.ReviewStartedEvent.class));
            verify(aiModelPort, atLeastOnce()).reviewCode(anyString(), any(PrContext.class));
        }
    }

    @Nested
    @DisplayName("when using PR context")
    class WhenUsingPrContext {

        @BeforeEach
        void setUp() {
            when(aiModelPort.maxTokens()).thenReturn(MAX_TOKENS);
            when(encoding.countTokens(anyString())).thenReturn(50);
        }

        @Test
        @DisplayName("should pass PR context to AI model")
        void shouldPassPrContextToAiModel() {
            when(aiModelPort.reviewCode(anyString(), any(PrContext.class))).thenReturn(Mono.just("Review with context"));
            when(aiModelPort.mergeReviews(anyString(), any(PrContext.class))).thenReturn(Mono.just("Merged"));

            String prTitle = "fix: critical authentication bug";
            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, prTitle, multiFileDiff, CORRELATION_ID))
                    .expectNextMatches(result -> result.status() == ReviewStatus.COMPLETED)
                    .verifyComplete();

            ArgumentCaptor<PrContext> contextCaptor = ArgumentCaptor.forClass(PrContext.class);
            verify(aiModelPort, atLeastOnce()).reviewCode(anyString(), contextCaptor.capture());

            PrContext captured = contextCaptor.getValue();
            assertThat(captured.title()).isEqualTo(prTitle);
            assertThat(captured.type().name()).isEqualTo("BUGFIX");
        }

        @Test
        @DisplayName("should handle null PR title gracefully")
        void shouldHandleNullPrTitleGracefully() {
            when(aiModelPort.reviewCode(anyString(), any(PrContext.class))).thenReturn(Mono.just("Review"));
            when(aiModelPort.mergeReviews(anyString(), any(PrContext.class))).thenReturn(Mono.just("Merged"));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, null, multiFileDiff, CORRELATION_ID))
                    .expectNextMatches(result -> result.status() == ReviewStatus.COMPLETED)
                    .verifyComplete();

            ArgumentCaptor<PrContext> contextCaptor = ArgumentCaptor.forClass(PrContext.class);
            verify(aiModelPort, atLeastOnce()).reviewCode(anyString(), contextCaptor.capture());

            PrContext captured = contextCaptor.getValue();
            assertThat(captured.type().name()).isEqualTo("UNKNOWN");
        }
    }

    @Nested
    @DisplayName("when filtering deleted lines")
    class WhenFilteringDeletedLines {

        @BeforeEach
        void setUp() {
            when(aiModelPort.maxTokens()).thenReturn(MAX_TOKENS);
            when(encoding.countTokens(anyString())).thenReturn(50);
            when(aiModelPort.mergeReviews(anyString(), any(PrContext.class))).thenReturn(Mono.just("Merged"));
        }

        @Test
        @DisplayName("should remove deleted lines from diff")
        void shouldRemoveDeletedLines() {
            String diffWithDeleted = """
                    diff --git a/test.java b/test.java
                    --- a/test.java
                    +++ b/test.java
                    @@ -1,3 +1,3 @@
                     public class Test {
                    -    private String oldField;
                    +    private String newField;
                     }
                    """;

            when(aiModelPort.reviewCode(anyString(), any(PrContext.class))).thenReturn(Mono.just("OK"));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, diffWithDeleted, CORRELATION_ID))
                    .expectNextMatches(result -> result.status() == ReviewStatus.COMPLETED)
                    .verifyComplete();

            ArgumentCaptor<String> diffCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiModelPort).reviewCode(diffCaptor.capture(), any(PrContext.class));

            String capturedDiff = diffCaptor.getValue();
            assertThat(capturedDiff).doesNotContain("-    private String oldField;");
            assertThat(capturedDiff).contains("+    private String newField;");
            assertThat(capturedDiff).contains("--- a/test.java");  // File header preserved
        }

        @Test
        @DisplayName("should preserve file headers with --- prefix")
        void shouldPreserveFileHeaders() {
            String diff = """
                    diff --git a/old.java b/new.java
                    deleted file mode 100644
                    --- a/old.java
                    +++ /dev/null
                    @@ -1,5 +0,0 @@
                    -deleted line 1
                    -deleted line 2
                    """;

            when(aiModelPort.reviewCode(anyString(), any(PrContext.class))).thenReturn(Mono.just("OK"));

            StepVerifier.create(reviewService.perform(CONTEXT_ID, REPO_OWNER, REPO_NAME, PR_NUMBER, PR_TITLE, diff, CORRELATION_ID))
                    .expectNextMatches(result -> result.status() == ReviewStatus.COMPLETED)
                    .verifyComplete();

            ArgumentCaptor<String> diffCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiModelPort).reviewCode(diffCaptor.capture(), any(PrContext.class));

            String capturedDiff = diffCaptor.getValue();
            assertThat(capturedDiff).contains("--- a/old.java");
            assertThat(capturedDiff).doesNotContain("-deleted line 1");
            assertThat(capturedDiff).doesNotContain("-deleted line 2");
        }
    }
}
