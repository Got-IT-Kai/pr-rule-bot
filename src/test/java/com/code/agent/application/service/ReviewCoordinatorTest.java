package com.code.agent.application.service;

import com.code.agent.application.event.ReviewFailedEvent;
import com.code.agent.application.event.ReviewRequestedEvent;
import com.code.agent.application.port.out.EventBusPort;
import com.code.agent.application.port.out.GitHubPort;
import com.code.agent.domain.model.PullRequestReviewInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link ReviewCoordinator}.
 * Tests the orchestration logic of review process:
 * - Fetching diff from GitHub
 * - Publishing appropriate events based on success/failure
 * - Error handling and event propagation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewCoordinator")
class ReviewCoordinatorTest {

    @Mock
    private GitHubPort gitHubPort;

    @Mock
    private EventBusPort eventBusPort;

    @InjectMocks
    private ReviewCoordinator reviewCoordinator;

    @Captor
    private ArgumentCaptor<ReviewRequestedEvent> reviewRequestedEventCaptor;

    @Captor
    private ArgumentCaptor<ReviewFailedEvent> reviewFailedEventCaptor;

    @Nested
    @DisplayName("startReview")
    class StartReview {

        @Nested
        @DisplayName("when diff is successfully fetched")
        class WhenDiffSuccessfullyFetched {

            @Test
            @DisplayName("should publish ReviewRequestedEvent with diff")
            void shouldPublishReviewRequestedEventWithDiff() {
                // Given
                PullRequestReviewInfo info = new PullRequestReviewInfo(
                        "owner",
                        "repo",
                        123,
                        "https://github.com/owner/repo/pull/123.diff"
                );
                String expectedDiff = "diff --git a/file.txt b/file.txt\n+new line";

                given(gitHubPort.getDiff(info)).willReturn(Mono.just(expectedDiff));
                given(eventBusPort.publishEvent(any(ReviewRequestedEvent.class)))
                        .willReturn(Mono.empty());

                // When
                Mono<Void> result = reviewCoordinator.startReview(info);

                // Then
                StepVerifier.create(result)
                        .verifyComplete();

                verify(gitHubPort).getDiff(info);
                verify(eventBusPort).publishEvent(reviewRequestedEventCaptor.capture());
                verify(eventBusPort, never()).publishEvent(any(ReviewFailedEvent.class));

                ReviewRequestedEvent capturedEvent = reviewRequestedEventCaptor.getValue();
                assertThat(capturedEvent.reviewInfo()).isEqualTo(info);
                assertThat(capturedEvent.diff()).isEqualTo(expectedDiff);
            }

            @Test
            @DisplayName("should handle empty diff")
            void shouldHandleEmptyDiff() {
                // Given
                PullRequestReviewInfo info = new PullRequestReviewInfo(
                        "owner",
                        "repo",
                        456,
                        "https://github.com/owner/repo/pull/456.diff"
                );

                given(gitHubPort.getDiff(info)).willReturn(Mono.just(""));
                given(eventBusPort.publishEvent(any(ReviewRequestedEvent.class)))
                        .willReturn(Mono.empty());

                // When
                Mono<Void> result = reviewCoordinator.startReview(info);

                // Then
                StepVerifier.create(result)
                        .verifyComplete();

                verify(eventBusPort).publishEvent(reviewRequestedEventCaptor.capture());

                ReviewRequestedEvent capturedEvent = reviewRequestedEventCaptor.getValue();
                assertThat(capturedEvent.diff()).isEmpty();
            }
        }

        @Nested
        @DisplayName("when diff fetch fails")
        class WhenDiffFetchFails {

            @Test
            @DisplayName("should publish ReviewFailedEvent with error message")
            void shouldPublishReviewFailedEventWithErrorMessage() {
                // Given
                PullRequestReviewInfo info = new PullRequestReviewInfo(
                        "owner",
                        "repo",
                        789,
                        "https://github.com/owner/repo/pull/789.diff"
                );
                String errorMessage = "GitHub API rate limit exceeded";

                given(gitHubPort.getDiff(info))
                        .willReturn(Mono.error(new RuntimeException(errorMessage)));
                given(eventBusPort.publishEvent(any(ReviewFailedEvent.class)))
                        .willReturn(Mono.empty());

                // When
                Mono<Void> result = reviewCoordinator.startReview(info);

                // Then
                StepVerifier.create(result)
                        .verifyComplete();

                verify(gitHubPort).getDiff(info);
                verify(eventBusPort).publishEvent(reviewFailedEventCaptor.capture());
                verify(eventBusPort, never()).publishEvent(any(ReviewRequestedEvent.class));

                ReviewFailedEvent capturedEvent = reviewFailedEventCaptor.getValue();
                assertThat(capturedEvent.reviewInfo()).isEqualTo(info);
                assertThat(capturedEvent.message()).isEqualTo(errorMessage);
            }

            @Test
            @DisplayName("should handle null pointer exception")
            void shouldHandleNullPointerException() {
                // Given
                PullRequestReviewInfo info = new PullRequestReviewInfo(
                        "owner",
                        "repo",
                        999,
                        null
                );

                given(gitHubPort.getDiff(info))
                        .willReturn(Mono.error(new NullPointerException("Diff URL is null")));
                given(eventBusPort.publishEvent(any(ReviewFailedEvent.class)))
                        .willReturn(Mono.empty());

                // When
                Mono<Void> result = reviewCoordinator.startReview(info);

                // Then
                StepVerifier.create(result)
                        .verifyComplete();

                verify(eventBusPort).publishEvent(reviewFailedEventCaptor.capture());

                ReviewFailedEvent capturedEvent = reviewFailedEventCaptor.getValue();
                assertThat(capturedEvent.message()).contains("Diff URL is null");
            }

            @Test
            @DisplayName("should propagate error if ReviewFailedEvent publish fails")
            void shouldPropagateErrorIfReviewFailedEventPublishFails() {
                // Given
                PullRequestReviewInfo info = new PullRequestReviewInfo(
                        "owner",
                        "repo",
                        111,
                        "https://github.com/owner/repo/pull/111.diff"
                );

                given(gitHubPort.getDiff(info))
                        .willReturn(Mono.error(new RuntimeException("Fetch failed")));
                given(eventBusPort.publishEvent(any(ReviewFailedEvent.class)))
                        .willReturn(Mono.error(new RuntimeException("Event bus down")));

                // When
                Mono<Void> result = reviewCoordinator.startReview(info);

                // Then: Error should propagate
                StepVerifier.create(result)
                        .expectError(RuntimeException.class)
                        .verify();

                verify(eventBusPort).publishEvent(any(ReviewFailedEvent.class));
            }
        }

        @Nested
        @DisplayName("edge cases")
        class EdgeCases {

            @Test
            @DisplayName("should handle very large diff")
            void shouldHandleVeryLargeDiff() {
                // Given
                PullRequestReviewInfo info = new PullRequestReviewInfo(
                        "owner",
                        "repo",
                        555,
                        "https://github.com/owner/repo/pull/555.diff"
                );
                String largeDiff = "diff --git a/file.txt b/file.txt\n" + "x".repeat(10000);

                given(gitHubPort.getDiff(info)).willReturn(Mono.just(largeDiff));
                given(eventBusPort.publishEvent(any(ReviewRequestedEvent.class)))
                        .willReturn(Mono.empty());

                // When
                Mono<Void> result = reviewCoordinator.startReview(info);

                // Then
                StepVerifier.create(result)
                        .verifyComplete();

                verify(eventBusPort).publishEvent(reviewRequestedEventCaptor.capture());
                assertThat(reviewRequestedEventCaptor.getValue().diff()).hasSize(largeDiff.length());
            }

            @Test
            @DisplayName("should handle special characters in diff")
            void shouldHandleSpecialCharactersInDiff() {
                // Given
                PullRequestReviewInfo info = new PullRequestReviewInfo(
                        "owner",
                        "repo",
                        666,
                        "https://github.com/owner/repo/pull/666.diff"
                );
                String specialDiff = "diff --git a/файл.txt b/файл.txt\n+한글\n+日本語\n+emoji🎉";

                given(gitHubPort.getDiff(info)).willReturn(Mono.just(specialDiff));
                given(eventBusPort.publishEvent(any(ReviewRequestedEvent.class)))
                        .willReturn(Mono.empty());

                // When
                Mono<Void> result = reviewCoordinator.startReview(info);

                // Then
                StepVerifier.create(result)
                        .verifyComplete();

                verify(eventBusPort).publishEvent(reviewRequestedEventCaptor.capture());
                assertThat(reviewRequestedEventCaptor.getValue().diff()).isEqualTo(specialDiff);
            }
        }
    }
}
