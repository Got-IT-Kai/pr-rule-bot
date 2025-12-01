package com.code.review.infrastructure.adapter.outbound.ai;

import com.code.review.domain.model.PrContext;
import com.code.review.domain.model.PrType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompositeAiModelAdapter")
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class CompositeAiModelAdapterTest {

    @Mock
    AiClient ollamaClient;

    @Mock
    AiClient geminiClient;

    @Mock
    AiProperties aiProperties;

    static final PrContext PR_CONTEXT = PrContext.from("fix: critical bug");
    static final String DIFF = "diff --git a/test.java";
    static final String REVIEW = "Review comment";
    static final String MERGED_REVIEW = "Merged review";

    @Nested
    @DisplayName("when selecting AI provider")
    class WhenSelectingProvider {

        @Test
        @DisplayName("should use configured provider when ready")
        void shouldUseConfiguredProviderWhenReady() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(true);
            when(ollamaClient.providerName()).thenReturn("ollama");
            when(geminiClient.provider()).thenReturn(AiProvider.GEMINI);

            CompositeAiModelAdapter adapter = new CompositeAiModelAdapter(
                    List.of(ollamaClient, geminiClient), aiProperties);

            assertThat(adapter.providerName()).isEqualTo("ollama");
        }

        @Test
        @DisplayName("should fallback to available provider when configured is not ready")
        void shouldFallbackWhenConfiguredNotReady() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(false);
            when(geminiClient.provider()).thenReturn(AiProvider.GEMINI);
            when(geminiClient.isReady()).thenReturn(true);
            when(geminiClient.providerName()).thenReturn("gemini");

            CompositeAiModelAdapter adapter = new CompositeAiModelAdapter(
                    List.of(ollamaClient, geminiClient), aiProperties);

            assertThat(adapter.providerName()).isEqualTo("gemini");
        }

        @Test
        @DisplayName("should throw exception when no providers are ready")
        void shouldThrowExceptionWhenNoProvidersReady() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(false);
            when(geminiClient.provider()).thenReturn(AiProvider.GEMINI);
            when(geminiClient.isReady()).thenReturn(false);

            assertThatThrownBy(() -> new CompositeAiModelAdapter(
                    List.of(ollamaClient, geminiClient), aiProperties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No AI client is ready")
                    .hasMessageContaining("OLLAMA");
        }

        @Test
        @DisplayName("should prefer configured provider over fallback order")
        void shouldPreferConfiguredProviderOverOrder() {
            when(aiProperties.provider()).thenReturn(AiProvider.GEMINI);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(true);
            when(geminiClient.provider()).thenReturn(AiProvider.GEMINI);
            when(geminiClient.isReady()).thenReturn(true);
            when(geminiClient.providerName()).thenReturn("gemini");

            CompositeAiModelAdapter adapter = new CompositeAiModelAdapter(
                    List.of(ollamaClient, geminiClient), aiProperties);

            assertThat(adapter.providerName()).isEqualTo("gemini");
        }
    }

    @Nested
    @DisplayName("when delegating operations")
    class WhenDelegatingOperations {

        CompositeAiModelAdapter adapter;

        @Test
        @DisplayName("should delegate reviewCode to active client")
        void shouldDelegateReviewCode() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(true);
            when(ollamaClient.reviewCode(anyString(), any(PrContext.class)))
                    .thenReturn(Mono.just(REVIEW));

            adapter = new CompositeAiModelAdapter(List.of(ollamaClient), aiProperties);

            StepVerifier.create(adapter.reviewCode(DIFF, PR_CONTEXT))
                    .expectNext(REVIEW)
                    .verifyComplete();
        }

        @Test
        @DisplayName("should delegate mergeReviews to active client")
        void shouldDelegateMergeReviews() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(true);
            when(ollamaClient.mergeReviews(anyString(), any(PrContext.class)))
                    .thenReturn(Mono.just(MERGED_REVIEW));

            adapter = new CompositeAiModelAdapter(List.of(ollamaClient), aiProperties);

            StepVerifier.create(adapter.mergeReviews("Review 1\nReview 2", PR_CONTEXT))
                    .expectNext(MERGED_REVIEW)
                    .verifyComplete();
        }

        @Test
        @DisplayName("should delegate maxTokens to active client")
        void shouldDelegateMaxTokens() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(true);
            when(ollamaClient.maxTokens()).thenReturn(8000);

            adapter = new CompositeAiModelAdapter(List.of(ollamaClient), aiProperties);

            assertThat(adapter.maxTokens()).isEqualTo(8000);
        }

        @Test
        @DisplayName("should delegate providerName to active client")
        void shouldDelegateProviderName() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(true);
            when(ollamaClient.providerName()).thenReturn("ollama");

            adapter = new CompositeAiModelAdapter(List.of(ollamaClient), aiProperties);

            assertThat(adapter.providerName()).isEqualTo("ollama");
        }

        @Test
        @DisplayName("should delegate modelName to active client")
        void shouldDelegateModelName() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(true);
            when(ollamaClient.modelName()).thenReturn("qwen2.5-coder:7b");

            adapter = new CompositeAiModelAdapter(List.of(ollamaClient), aiProperties);

            assertThat(adapter.modelName()).isEqualTo("qwen2.5-coder:7b");
        }

        @Test
        @DisplayName("should propagate errors from active client")
        void shouldPropagateErrorsFromActiveClient() {
            when(aiProperties.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.provider()).thenReturn(AiProvider.OLLAMA);
            when(ollamaClient.isReady()).thenReturn(true);
            when(ollamaClient.reviewCode(anyString(), any(PrContext.class)))
                    .thenReturn(Mono.error(new RuntimeException("AI service error")));

            adapter = new CompositeAiModelAdapter(List.of(ollamaClient), aiProperties);

            StepVerifier.create(adapter.reviewCode(DIFF, PR_CONTEXT))
                    .expectErrorMatches(e -> e instanceof RuntimeException &&
                            e.getMessage().contains("AI service error"))
                    .verify();
        }
    }
}
