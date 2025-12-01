package com.code.integration.infrastructure.adapter.outbound.github;

import com.code.integration.domain.model.ReviewComment;
import com.code.platform.metrics.MetricsHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubCommentClientAdapter")
class GitHubCommentClientAdapterTest {

    @Mock
    WebClient webClient;

    @Mock
    WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    WebClient.RequestBodySpec requestBodySpec;

    @Mock
    WebClient.ResponseSpec responseSpec;

    GitHubCommentClientAdapter adapter;

    static final String OWNER = "owner";
    static final String REPO = "repo";
    static final Integer PR_NUMBER = 123;
    static final String COMMENT_BODY = "Review completed";
    static final Long COMMENT_ID = 456L;

    @Mock
    private MetricsHelper metricsHelper;

    @BeforeEach
    void setUp() {
        adapter = new GitHubCommentClientAdapter(webClient, metricsHelper);
    }

    @Nested
    @DisplayName("when posting comment")
    class WhenPostingComment {

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUp() {
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString(), any(), any(), any())).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(anyMap())).thenReturn((WebClient.RequestHeadersSpec) requestBodySpec);
            when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        }

        @Test
        @DisplayName("should post comment successfully")
        void shouldPostCommentSuccessfully() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, COMMENT_BODY, null);

            when(responseSpec.bodyToMono(any(Class.class)))
                    .thenReturn(Mono.just(new GitHubCommentClientAdapter.GitHubCommentResponse(COMMENT_ID)));

            StepVerifier.create(adapter.postComment(comment))
                    .assertNext(commentId -> assertThat(commentId).isEqualTo(COMMENT_ID))
                    .verifyComplete();

            verify(webClient).post();
            verify(requestBodyUriSpec).uri(anyString(), eq(OWNER), eq(REPO), eq(PR_NUMBER));
        }

        @Test
        @DisplayName("should use correct URI template")
        void shouldUseCorrectUriTemplate() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, COMMENT_BODY, null);

            when(responseSpec.bodyToMono(any(Class.class)))
                    .thenReturn(Mono.just(new GitHubCommentClientAdapter.GitHubCommentResponse(COMMENT_ID)));

            StepVerifier.create(adapter.postComment(comment))
                    .expectNext(COMMENT_ID)
                    .verifyComplete();

            verify(requestBodyUriSpec).uri("/repos/{owner}/{repo}/issues/{issue_number}/comments",
                    OWNER, REPO, PR_NUMBER);
        }

        @Test
        @DisplayName("should handle error response")
        void shouldHandleError() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, COMMENT_BODY, null);

            when(responseSpec.bodyToMono(any(Class.class)))
                    .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error", null, null, null)));

            StepVerifier.create(adapter.postComment(comment))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(WebClientResponseException.class);
                    })
                    .verify();
        }
    }
}
