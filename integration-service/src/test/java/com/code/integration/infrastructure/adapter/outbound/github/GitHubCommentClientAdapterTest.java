package com.code.integration.infrastructure.adapter.outbound.github;

import com.code.integration.domain.model.ReviewComment;
import com.code.platform.metrics.MetricsHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
    WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    WebClient.ResponseSpec responseSpec;

    @Captor
    ArgumentCaptor<Object> bodyCaptor;

    @Mock
    MetricsHelper metricsHelper;

    GitHubCommentBodyTrimmer trimmer;
    GitHubCommentClientAdapter adapter;

    static final String OWNER = "owner";
    static final String REPO = "repo";
    static final Integer PR_NUMBER = 123;
    static final String COMMENT_BODY = "Review completed";
    static final Long COMMENT_ID = 456L;

    static final String SHORT_ERROR_BODY =
            "Upstream GitHub API returned 500 Internal Server Error";

    @BeforeEach
    void setUp() {
        trimmer = new GitHubCommentBodyTrimmer();
        adapter = new GitHubCommentClientAdapter(webClient, trimmer, metricsHelper);
    }

    @Nested
    @DisplayName("when posting comment")
    class WhenPostingComment {

        @BeforeEach
        void setUp() {
            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString(), any(Object[].class)))
                    .thenReturn(requestBodySpec);
            doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());

            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        }

        @Test
        @DisplayName("should post comment successfully")
        void shouldPostCommentSuccessfully() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, COMMENT_BODY, null);


            when(responseSpec.bodyToMono(eq(GitHubCommentClientAdapter.GitHubCommentResponse.class)))
                    .thenReturn(Mono.just(new GitHubCommentClientAdapter.GitHubCommentResponse(COMMENT_ID)));

            StepVerifier.create(adapter.postComment(comment))
                    .expectNext(COMMENT_ID)
                    .verifyComplete();

            verify(webClient).post();
            verify(requestBodyUriSpec).uri(
                    "/repos/{owner}/{repo}/issues/{issue_number}/comments",
                    OWNER, REPO, PR_NUMBER
            );

            verify(metricsHelper).incrementCounter("github.comment.post.success");
            verify(metricsHelper, never()).incrementCounter("github.comment.post.failure");
            verify(metricsHelper, never()).incrementCounter("github.comment.trim.applied");
            verify(metricsHelper, never()).incrementCounter("github.comment.trim.hard_truncated");
        }

        @Test
        @DisplayName("2xx but empty body -> should fail (not business success)")
        void shouldFailWhenEmptyBodyReturned() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, COMMENT_BODY, null);

            when(responseSpec.bodyToMono(eq(GitHubCommentClientAdapter.GitHubCommentResponse.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(adapter.postComment(comment))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(IllegalStateException.class);
                        assertThat(err.getMessage()).contains("empty response body");
                    })
                    .verify();

            verify(metricsHelper).incrementCounter("github.comment.post.failure");
            verify(metricsHelper, never()).incrementCounter("github.comment.post.success");
        }

        @Test
        @DisplayName("2xx but id is null -> should fail (not business success)")
        void shouldFailWhenIdMissing() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, COMMENT_BODY, null);

            when(responseSpec.bodyToMono(eq(GitHubCommentClientAdapter.GitHubCommentResponse.class)))
                    .thenReturn(Mono.just(new GitHubCommentClientAdapter.GitHubCommentResponse(null)));

            StepVerifier.create(adapter.postComment(comment))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(IllegalStateException.class);
                        assertThat(err.getMessage()).contains("missing comment id");
                    })
                    .verify();

            verify(metricsHelper).incrementCounter("github.comment.post.failure");
            verify(metricsHelper, never()).incrementCounter("github.comment.post.success");
        }

        @Test
        @DisplayName("WebClientResponseException error -> should propagate & count failure")
        void shouldHandleWebClientResponseException() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, COMMENT_BODY, null);

            WebClientResponseException webClientResponseException = new WebClientResponseException(
                    500,
                    "Internal Server Error",
                    HttpHeaders.EMPTY,
                    SHORT_ERROR_BODY.getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );

            when(responseSpec.bodyToMono(eq(GitHubCommentClientAdapter.GitHubCommentResponse.class)))
                    .thenReturn(Mono.error(webClientResponseException));

            StepVerifier.create(adapter.postComment(comment))
                    .expectErrorSatisfies(e -> {
                        assertThat(e).isSameAs(webClientResponseException);
                        assertThat(e).isInstanceOf(WebClientResponseException.class);
                        assertThat(((WebClientResponseException)e).getStatusCode().value()).isEqualTo(500);
                    })
                    .verify();

            verify(metricsHelper).incrementCounter("github.comment.post.failure");
            verify(metricsHelper, never()).incrementCounter("github.comment.post.success");
        }

        @Test
        @DisplayName("unexpected runtime error -> should count failure (covers non-WebClientResponseException branch)")
        void shouldHandleUnexpectedRuntimeError() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, COMMENT_BODY, null);

            when(responseSpec.bodyToMono(eq(GitHubCommentClientAdapter.GitHubCommentResponse.class)))
                    .thenReturn(Mono.error(new RuntimeException("boom")));

            StepVerifier.create(adapter.postComment(comment))
                    .expectError(RuntimeException.class)
                    .verify();

            verify(metricsHelper).incrementCounter("github.comment.post.failure");
            verify(metricsHelper, never()).incrementCounter("github.comment.post.success");
        }

        @Test
        @DisplayName("long body -> should be trimmed before posting and emit trim metrics")
        void shouldTrimLongBodyBeforePosting() {
            String longBody = "B".repeat(80_000);
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, longBody, null);

            when(responseSpec.bodyToMono(eq(GitHubCommentClientAdapter.GitHubCommentResponse.class)))
                    .thenReturn(Mono.just(new GitHubCommentClientAdapter.GitHubCommentResponse(COMMENT_ID)));

            StepVerifier.create(adapter.postComment(comment))
                    .expectNext(COMMENT_ID)
                    .verifyComplete();

            verify(requestBodySpec).bodyValue(bodyCaptor.capture());

            Object captured = bodyCaptor.getValue();
            assertThat(captured).isInstanceOf(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) captured;
            assertThat(payload).containsKey("body");

            String postedBody = (String) payload.get("body");

            assertThat(postedBody.length()).isLessThanOrEqualTo(60_000);
            assertThat(postedBody).isNotEqualTo(longBody);
            assertThat(postedBody).contains("This review was truncated");

            verify(metricsHelper).incrementCounter("github.comment.trim.applied");
            verify(metricsHelper).incrementCounter("github.comment.trim.hard_truncated");
            verify(metricsHelper).incrementCounter("github.comment.post.success");
            verify(metricsHelper, never()).incrementCounter("github.comment.post.failure");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("comment is null -> IllegalArgumentException")
        void shouldFailWhenCommentNull() {
            StepVerifier.create(adapter.postComment(null))
                    .expectErrorSatisfies(err -> {
                        assertThat(err).isInstanceOf(IllegalArgumentException.class);
                        assertThat(err.getMessage()).contains("must not be null");
                    })
                    .verify();

            verifyNoInteractions(webClient);
            verifyNoInteractions(metricsHelper);
        }

        @Test
        @DisplayName("owner blank -> IllegalArgumentException")
        void shouldFailWhenOwnerBlank() {
            ReviewComment comment = new ReviewComment("   ", REPO, PR_NUMBER, COMMENT_BODY, null);

            StepVerifier.create(adapter.postComment(comment))
                    .expectError(IllegalArgumentException.class)
                    .verify();

            verifyNoInteractions(webClient);
            verifyNoInteractions(metricsHelper);
        }

        @Test
        @DisplayName("repo blank -> IllegalArgumentException")
        void shouldFailWhenRepoBlank() {
            ReviewComment comment = new ReviewComment(OWNER, "  ", PR_NUMBER, COMMENT_BODY, null);

            StepVerifier.create(adapter.postComment(comment))
                    .expectError(IllegalArgumentException.class)
                    .verify();

            verifyNoInteractions(webClient);
            verifyNoInteractions(metricsHelper);
        }

        @Test
        @DisplayName("pullRequestNumber null -> IllegalArgumentException")
        void shouldFailWhenPullRequestNumberNull() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, null, COMMENT_BODY, null);

            StepVerifier.create(adapter.postComment(comment))
                    .expectError(IllegalArgumentException.class)
                    .verify();

            verifyNoInteractions(webClient);
            verifyNoInteractions(metricsHelper);
        }

        @Test
        @DisplayName("body blank -> IllegalArgumentException")
        void shouldFailWhenBodyBlank() {
            ReviewComment comment = new ReviewComment(OWNER, REPO, PR_NUMBER, "   ", null);

            StepVerifier.create(adapter.postComment(comment))
                    .expectError(IllegalArgumentException.class)
                    .verify();

            verifyNoInteractions(webClient);
            verifyNoInteractions(metricsHelper);
        }
    }
}
